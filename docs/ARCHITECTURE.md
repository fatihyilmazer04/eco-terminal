# 📐 Sistem Mimarisi

## İçindekiler
- [Mimari Genel Bakış](#mimari-genel-bakış)
- [Mikro Servis Topolojisi](#mikro-servis-topolojisi)
- [Veri Akışı](#veri-akışı)
- [Veritabanı Şeması](#veritabanı-şeması)
- [Güvenlik Mimarisi](#güvenlik-mimarisi)
- [Network Mimarisi](#network-mimarisi)
- [Mimari Kararlar (ADRs)](#mimari-kararlar)

---

## Mimari Genel Bakış

Eco-Terminal **monolit + mikro servis hibrit** mimari kullanır. Backend monolit (Spring Boot) ana iş mantığını yürütür; ML/AI işleri **ayrı Python mikro servisleri** olarak çalışır.

### Mimari Prensipleri

1. **Separation of Concerns**: ML kodları backend'den ayrı (Python'da çalışıyor)
2. **Resilience**: Her servis bağımsız fail olabilir, sistem yine çalışır (fallback chain)
3. **Observability**: Tüm servisler Prometheus metrik üretir, Loki'ye log yazar
4. **Scalability**: Stateless backend, Redis cache, K8s manifest hazır
5. **Security**: JWT auth + internal service-to-service token

---

## Mikro Servis Topolojisi

### 11 Docker Servisi

```
┌─────────────────────────────────────────────────────────────────┐
│                       eco-network                                │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐                  │
│  │ frontend │──│ backend  │──│ llm-service  │                  │
│  │ :3000    │  │ :8080    │  │ :5002        │                  │
│  └──────────┘  └────┬─────┘  └──────┬───────┘                  │
│                     │                │                          │
│         ┌───────────┼────────────────┤                          │
│         ▼           ▼                ▼                          │
│  ┌──────────┐ ┌──────────┐  ┌──────────────┐                   │
│  │ postgres │ │  redis   │  │ ai-service   │                   │
│  │ :5432    │ │ :6379    │  │ :5000        │                   │
│  └──────────┘ └──────────┘  └──────────────┘                   │
│                                                                  │
│  ┌──────────────┐                                               │
│  │ yolov8       │                                               │
│  │ :5001        │                                               │
│  └──────────────┘                                               │
│                                                                  │
│  ┌──────────┐ ┌──────────┐  ┌──────────────┐ ┌──────────┐      │
│  │prometheus│ │ grafana  │  │ loki         │ │promtail  │      │
│  │ :9090    │ │ :3001    │  │ :3100        │ │          │      │
│  └──────────┘ └──────────┘  └──────────────┘ └──────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

### Servis Detayları

| Servis | Image | Görev | Bağımlılıklar |
|--------|-------|-------|---------------|
| **frontend** | eco-terminal-frontend | React SPA + Nginx | backend |
| **backend** | eco-terminal-backend | Java/Spring Boot ana API | postgres, redis |
| **llm-service** | eco-terminal-llm-service | DistilBERT + RAG + Gemini | backend |
| **ai-service** | eco-terminal-ai-service | XGBoost + LSTM tahmin | postgres |
| **yolov8-service** | eco-terminal-yolov8 | Kalabalık görüntü işleme | postgres |
| **postgres** | postgres:15-alpine | Ana veritabanı | - |
| **redis** | redis:7-alpine | Cache + rate limit | - |
| **prometheus** | prom/prometheus | Metrik toplama | - |
| **grafana** | grafana/grafana:10.4.2 | Görselleştirme | prometheus, loki |
| **loki** | grafana/loki:2.9.8 | Log toplama | - |
| **promtail** | grafana/promtail:2.9.8 | Log forwarding | loki |

---

## Veri Akışı

### Chatbot Rotası Sorgu Akışı

Aşağıdaki akış, kullanıcı "A1 kapısına nasıl giderim?" yazdığında olan her şeyi gösterir:

```
1. Frontend (ChatbotWidget)
   └─ POST /api/chatbot/ask { message: "A1 kapısına nasıl giderim?" }
        ↓ (JWT header'da)

2. Backend (ChatbotController)
   └─ ChatbotService.chat()
        └─ Provider Chain → LlmServiceProvider (default)
             ↓

3. Backend → llm-service
   └─ POST /chat (X-Internal-Token header)
        ↓

4. llm-service (chat router)
   └─ HybridClassifier.classify()
        ├─ DistilBertClassifier.classify() → confidence 0.91
        ├─ RuleBasedClassifier.classify() → entities extracted
        └─ Karar: DistilBERT (conf >= 0.85 threshold)
             ↓

5. llm-service (Retriever)
   ├─ KnowledgeBase.search() → 3 relevant facts
   └─ BackendClient.get_optimal_route(from=4, to=1)
        ↓ (Internal token ile geri Backend'e)

6. Backend (Dijkstra)
   └─ POST /api/routes/optimal
        ├─ GraphService → 15 zone, 52 edge in-memory
        ├─ DensityCache → AI predictions'tan yoğunluk
        └─ DijkstraService → 3 alternatif rota
             ↓ (alternatives JSON)

7. llm-service (PromptBuilder + Gemini)
   ├─ Prompt: System + Facts + Route + Question
   ├─ Gemini API → doğal dil cevap
   └─ Response: { reply, intent, route_steps, sources_used }
        ↓

8. Backend (LlmServiceProvider)
   └─ ChatbotResponse mapping → DTO
        ↓

9. Frontend (ChatbotWidget)
   ├─ MessageBubble render → intent badge + reply text
   ├─ RouteCard render → numaralı adımlar + "Haritada Göster"
   └─ User clicks "Haritada Göster"
        ↓ navigate('/passenger/heatmap', { state: { routeFromChatbot } })

10. Frontend (HeatmapPage)
    ├─ useEffect → zones list + chatbot route'u join
    ├─ State update → chatbotRoute, activeStepNumber
    └─ AirportHeatmap render → SVG polyline + numbered markers + animation
```

**Toplam adım sayısı:** 10
**Servis arası geçiş:** 6 kez
**Tipik latency:** 2-5 saniye (Gemini aktifse), <1 saniye (fallback)

### Yoğunluk Veri Pipeline'ı

```
ai-service (XGBoost) ──5dk──► PostgreSQL ai_predictions tablosu
                                       │
                                       ▼
GraphService.refreshDensities() ──5dk──► densityCache (in-memory)
                                       │
                                       ▼
DijkstraService.calculateEdgeWeight() ──her sorguda──► dinamik routing
```

---

## Veritabanı Şeması

### Ana Tablolar (16 + 9 sonradan eklenen)

```
┌─────────────────────────────────────────────────────────┐
│ users                                                    │
│ ├─ user_profiles                                         │
│ ├─ verification_codes                                    │
│ └─ audit_logs                                            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ zones (15 zone)                                          │
│ ├─ zone_map_positions (SVG koordinatları)                │
│ ├─ zone_connections ⭐ NEW (52 edge, Dijkstra için)      │
│ ├─ occupancy_readings (zaman serisi)                     │
│ ├─ environmental_metrics (enerji)                        │
│ └─ ai_predictions (XGBoost çıktıları)                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ airlines                                                 │
│ └─ flights                                               │
│    └─ tickets                                            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ eco_wallets                                              │
│ ├─ transaction_history                                   │
│ └─ reward_catalog                                        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ iot_devices                                              │
│ └─ maintenance_logs                                      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ route_checkins (yolcu rota takibi)                       │
│ route_completions (loyalty entegrasyonu)                 │
└─────────────────────────────────────────────────────────┘
```

### Flyway Migration Listesi

| Versiyon | Açıklama |
|----------|----------|
| V1 - V24 | İlk şema + seed veriler (önceden hazırdı) |
| **V25** | **`zone_connections` tablosu** ⭐ Yeni eklendi |

Detaylar: [`docs/DEPLOYMENT.md#migrations`](DEPLOYMENT.md)

### Zone Graph Yapısı

```
CheckIn-1 ─┐
CheckIn-2 ─┼─► Security-1 ┐
CheckIn-3 ─┘   Security-2 ┼─► Lounge-1 ┐
                          │   Lounge-2 ┼─► 8 Gate (A1-A3, B1-B2, C1-C3)
                          └────────────┘
```

- **Düğümler (Node):** 15 zone
- **Kenarlar (Edge):** 52 (her yön ayrı kayıt)
- **Edge Weight:** `walk_time_seconds + (target_density × penalty)`

---

## Güvenlik Mimarisi

### Kimlik Doğrulama Katmanları

```
1. Public Endpoints
   /api/auth/**, /actuator/health, /swagger-ui/**
   └─ Anonim erişim

2. User Authentication
   JWT (access + refresh token)
   ├─ Access: 15 dakika
   └─ Refresh: 7 gün

3. Internal Service Authentication
   X-Internal-Token header
   ├─ llm-service → backend çağrılarında kullanılır
   └─ ROLE_INTERNAL_SERVICE + ROLE_USER atanır

4. Admin-Only Endpoints
   /api/admin/**, /api/energy/**
   └─ @PreAuthorize("hasRole('ADMIN')")
```

### Filter Zinciri

```
Request → CORS Filter
       → InternalTokenFilter (X-Internal-Token kontrolü)
       → JwtAuthFilter (Bearer token kontrolü)
       → @PreAuthorize annotation kontrolü
       → Controller
```

### Diğer Güvenlik Önlemleri

- **BCrypt** password hash (strength=12)
- **Rate Limiting**: `/api/auth/login` 10 req/dk (Bucket4j + Redis)
- **HSTS, CSP, X-Frame-Options** headerları
- **Stateless session** (CSRF gerekmez)

---

## Network Mimarisi

### Docker Compose Network

Tüm servisler `eco-network` adlı bridge network'te çalışır. Servisler birbirine **container name** ile erişir:

```
backend     → postgres:5432 (DB connection)
backend     → redis:6379 (cache)
backend     → llm-service:5002 (chat proxy)
llm-service → backend:8080 (Dijkstra fetch)
llm-service → api.google.com (Gemini API, external)
prometheus  → backend:8080/actuator/prometheus
grafana     → prometheus:9090, loki:3100
```

### Port Tahsisi

| Port | Servis | Erişim |
|------|--------|--------|
| 3000 | frontend | Public (host) |
| 3001 | grafana | Public (host) |
| 5000 | ai-service | Internal |
| 5001 | yolov8-service | Internal |
| 5002 | llm-service | Public (host, debug için) |
| 5432 | postgres | Internal |
| 6379 | redis | Internal |
| 8080 | backend | Public (host) |
| 9090 | prometheus | Public (host) |

### Healthcheck Stratejisi

- **postgres**: `pg_isready` her 10sn
- **redis**: `redis-cli ping` her 10sn
- **backend**: `GET /actuator/health` her 30sn
- **llm-service**: `GET /health` her 30sn
- **loki**: `GET /ready` her 30sn

---

## Mimari Kararlar

### ADR-1: Neden Mikro Servis (Sadece LLM)?

**Karar:** ML kodları Python mikro servisinde, ana iş mantığı Java monolit'te.

**Sebepler:**
- ✅ DistilBERT/XGBoost Python ekosistemine bağımlı
- ✅ ML için ayrı scaling stratejisi (GPU, daha fazla RAM)
- ✅ Geliştirme döngüleri farklı (model güncelleme vs API değişikliği)
- ✅ Bağımsız deploy edilebilir
- ❌ Daha karmaşık ağ topolojisi
- ❌ Service-to-service auth gerekli

### ADR-2: Neden Hibrit Classifier?

**Karar:** DistilBERT + Rule-based ikili sistem.

**Sebepler:**
- ✅ DistilBERT semantic anlayış (paraphrase, typo)
- ✅ Rule-based deterministik entity extraction
- ✅ Model fail olursa rule-based devam eder
- ✅ Akademik açıdan ilginç (savunmada güçlü)
- ❌ İki sistemi yönetmek karmaşık

### ADR-3: Neden RAG?

**Karar:** Gemini'ye direkt sormak yerine RAG kullanmak.

**Sebepler:**
- ✅ Halüsinasyonu önler (Gemini'nin uydurmasını engeller)
- ✅ Domain-specific cevaplar (sadece havalimanı verisi)
- ✅ Backend Dijkstra çıktısını LLM'e bağlar
- ✅ Knowledge base güncellemesi kolay (vector embed gerekmez)
- ❌ Daha fazla istek (intent → KB → backend → Gemini)

### ADR-4: Neden Internal Token (JWT yerine)?

**Karar:** llm-service ↔ backend iletişimi için ayrı token mekanizması.

**Sebepler:**
- ✅ Servis kimlik ≠ kullanıcı kimliği
- ✅ Token expiration olmayan, rotating secret
- ✅ Audit log'larda servis çağrıları ayrılır
- ✅ Standard 12-Factor pattern
- ❌ Token rotation manuel

### ADR-5: Neden Edge Weight'te Yoğunluk?

**Karar:** Dijkstra'da `weight = walk_time + density × penalty` formülü.

**Sebepler:**
- ✅ Klasik shortest-path'in ötesi: dinamik yönlendirme
- ✅ Aynı yolcu sabah vs akşam farklı rota alabilir
- ✅ 3 strateji (shortest, least_crowded, balanced) ile kullanıcı seçimi
- ❌ Yoğunluk kestirim hatası → suboptimal rota

---

## Genişletilebilirlik

### Eklenmeye Hazır Modüller

- **K8s Deployment**: 11 manifest hazır (`k8s/`)
- **CI/CD**: GitHub Actions pipeline (`.github/workflows/ci.yml`)
- **Monitoring**: Prometheus + Grafana dashboard provisioning

### Gelecek İyileştirmeler

- Vector embedding tabanlı RAG (sentence-transformers)
- Çoklu lounge support (VIP segmentation)
- Multi-airport deployment (zone graph her havalimanı için ayrı)
- Real-time WebSocket bildirimleri
- A/B testing rota algoritması
