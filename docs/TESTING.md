# 🧪 Test Stratejisi ve Sonuçları

Eco-Terminal'in test yaklaşımı, otomatik testleri, manuel test senaryoları ve performans metrikleri.

## İçindekiler
- [Test Stratejisi](#test-stratejisi)
- [Backend Testleri](#backend-testleri)
- [Frontend Testleri](#frontend-testleri)
- [LLM Pipeline Testleri](#llm-pipeline-testleri)
- [End-to-End Testler](#end-to-end-testler)
- [Performans Metrikleri](#performans-metrikleri)
- [Bilinen Sorunlar](#bilinen-sorunlar)

---

## Test Stratejisi

Eco-Terminal **test piramidi** modelini takip eder:

```
        ┌──────────┐
        │   E2E    │  ← 6 senaryo (manuel smoke test)
        │  Tests   │
        └──────────┘
       ┌──────────────┐
       │ Integration  │  ← 2 sınıf (Backend)
       │    Tests     │
       └──────────────┘
     ┌──────────────────┐
     │    Unit Tests    │  ← 6+ sınıf (Backend) + 6 test (LLM Service)
     └──────────────────┘
```

### Test Türleri

| Tür | Çerçeve | Lokasyon | Sayı |
|-----|---------|----------|------|
| Backend Unit | JUnit 5 + Mockito | `backend/src/test/` | 6 |
| Backend Integration | Testcontainers | `backend/src/test/` | 2 |
| Frontend Unit | Vitest + Testing Library | `frontend/src/test/` | 1 (setup) |
| LLM Service Unit | pytest | `llm-service/tests/` | 6 |
| End-to-End | Manuel (curl) | — | 6 senaryo |

---

## Backend Testleri

### Konum

```
backend/src/test/java/com/ecoterminal/
├── controller/
│   ├── AuthControllerIntegrationTest.java
│   └── OccupancyControllerIntegrationTest.java
└── service/
    ├── AuthServiceTest.java
    ├── EnergyServiceTest.java
    ├── JwtServiceTest.java
    ├── LoyaltyServiceTest.java
    ├── NotificationRateLimiterTest.java
    └── OccupancyServiceTest.java
```

### Çalıştırma

```bash
cd backend

# Tüm testler
./mvnw test

# Sadece unit
./mvnw test -Dtest='*Test'

# Sadece integration (Testcontainers gerekir)
./mvnw test -Dtest='*IntegrationTest'

# Tek sınıf
./mvnw test -Dtest=JwtServiceTest

# Coverage raporu
./mvnw test jacoco:report
# Rapor: backend/target/site/jacoco/index.html
```

### Unit Test Örnekleri

#### JwtServiceTest

```java
@Test
void shouldGenerateValidJwt() {
    String token = jwtService.generateAccessToken(testUser);
    
    assertThat(token).isNotEmpty();
    assertThat(jwtService.validateToken(token)).isTrue();
    assertThat(jwtService.extractEmail(token)).isEqualTo(testUser.getEmail());
}

@Test
void shouldRejectExpiredToken() {
    String expired = generateExpiredToken();
    assertThat(jwtService.validateToken(expired)).isFalse();
}
```

#### LoyaltyServiceTest

```java
@Test
void shouldEarnPointsAndUpgradeTier() {
    when(walletRepo.findByUserId(1L)).thenReturn(Optional.of(bronzeWallet));
    
    loyaltyService.earnPoints(1L, 500, "Route completion");
    
    verify(walletRepo).save(argThat(w -> 
        w.getPointsBalance() == 500 && 
        w.getTierLevel() == TierLevel.SILVER
    ));
}
```

### Integration Test (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class AuthControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test_db");
    
    @Test
    void loginShouldReturnJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"test@test.com\",\"password\":\"pass123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
```

### Coverage Hedefleri

| Katman | Hedef | Mevcut (yaklaşık) |
|--------|-------|-------------------|
| Service | %70+ | %65 |
| Controller | %50+ | %40 |
| Repository | (skip — JPA) | — |
| Security (JWT, Filter) | %80+ | %75 |

---

## Frontend Testleri

### Setup

`frontend/src/test/setup.js`:

```javascript
import "@testing-library/jest-dom";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

afterEach(() => {
    cleanup();
});
```

### Çalıştırma

```bash
cd frontend
npm run test       # watch mode
npm run test:ci    # tek sefer
npm run test:coverage
```

### Önerilen Test Senaryoları (Gelecek)

Şu an sadece setup var. Önerilen genişlemeler:

```javascript
// ChatbotWidget.test.jsx
test("renders intent badge for route_request", async () => {
    render(<ChatbotWidget />);
    fireEvent.click(screen.getByLabelText("Chatbot'u aç"));
    
    const input = screen.getByPlaceholderText("Mesajınızı yazın...");
    fireEvent.change(input, { target: { value: "A1 nasıl giderim" } });
    fireEvent.click(screen.getByText("Gönder"));
    
    expect(await screen.findByText("Rota")).toBeInTheDocument();
});

// chatbot.test.js
test("normalizeResponse handles snake_case keys", () => {
    const raw = { route_steps: [{ step_number: 1 }] };
    const result = normalizeResponse(raw);
    
    expect(result.routeSteps[0].stepNumber).toBe(1);
});
```

---

## LLM Pipeline Testleri

### Konum

```
llm-service/tests/
└── test_intent_classifier.py    # 6 smoke test
```

### Çalıştırma

```bash
cd llm-service

# Lokal pytest
pytest tests/ -v

# Docker içinde
docker-compose exec llm-service pytest tests/ -v
```

### Mevcut Testler

```python
def test_route_request_with_gate():
    clf = RuleBasedClassifier()
    result = clf.classify("A12 kapısına nasıl giderim?")
    assert result.intent == IntentLabel.ROUTE_REQUEST
    assert result.entities.get("destination") == "Gate A12"
    assert result.confidence >= 0.7

def test_flight_info():
    clf = RuleBasedClassifier()
    result = clf.classify("TK1922 uçuşu kaçta kalkıyor?")
    assert result.intent == IntentLabel.FLIGHT_INFO
    assert result.entities.get("flight_code") == "TK1922"

def test_crowd_query(): ...
def test_loyalty_query(): ...
def test_unknown_fallback(): ...
def test_route_preference_least_crowded(): ...
```

### Test Sonuçları

```
======================== 6 passed in 0.07s ========================
```

**Süre:** 0.07 saniye (model yüklemesi yok, sadece rule-based).

### DistilBERT Test Sonuçları (Eğitim Sonrası)

`intent-training/models/intent_classifier/final/training_metrics.json`:

```json
{
  "test_accuracy": 0.859,
  "test_samples": 64,
  "train_samples": 296,
  "val_samples": 62,
  "intents": ["route_request", "flight_info", "crowd_query", 
              "loyalty_query", "general_info", "unknown"],
  "model_base": "distilbert-base-multilingual-cased",
  "epochs": 8,
  "batch_size": 16,
  "learning_rate": 5e-05,
  "device": "cuda"
}
```

### Classification Report (Detaylı)

```
                 precision    recall  f1-score   support

  route_request      0.750     1.000     0.857        12
    flight_info      1.000     0.700     0.824        10
    crowd_query      0.727     0.800     0.762        10
  loyalty_query      0.909     1.000     0.952        10
   general_info      0.889     0.727     0.800        11
        unknown      1.000     0.909     0.952        11

       accuracy                          0.859        64
      macro avg      0.879     0.856     0.858        64
   weighted avg      0.877     0.859     0.858        64
```

---

## End-to-End Testler

### Manuel Smoke Test Senaryoları

#### 6 Senaryo (Test Edildi)

**Senaryo 1: Spesifik Gate Rota**

```bash
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"A1 kapısına nasıl giderim?"}'
```

Beklenen:
- `intent: "route_request"` ✓
- `confidence >= 0.85` ✓
- `routeSteps.length === 4` ✓
- `provider: "llm-service"` ✓
- `sourcesUsed` içinde `"classifier:hybrid..."` ✓

**Senaryo 2: Rota Tercihi**

```bash
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"B2 kapısına en kalabalıksız yoldan"}'
```

Beklenen:
- `intent: "route_request"` ✓
- `entities.route_preference: "least_crowded"` ✓

**Senaryo 3: Paraphrase (DistilBERT semantic test)**

```bash
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"C3 yürümeli kaç dakika"}'
```

Beklenen:
- `intent: "route_request"` ✓ (rule-based "unknown" derdi)
- DistilBERT confidence 0.85+ ✓
- **Bu sonuç fine-tuning'in değerinin kanıtıdır**

**Senaryo 4: Loyalty Query**

```bash
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"Eco puanlarım kaç oldu?"}'
```

Beklenen:
- `intent: "loyalty_query"` ✓
- `routeSteps: null` ✓ (rota değil)

**Senaryo 5: General Info (Hibrit fallback testi)**

```bash
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"Lounge nerede bulabilirim?"}'
```

Beklenen:
- `intent: "general_info"` ✓
- Log'da `hybrid_decision source=rule-based` ✓ (DistilBERT conf düşük)

**Senaryo 6: Unknown**

```bash
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"merhaba hava nasıl"}'
```

Beklenen:
- `intent: "unknown"` ✓
- Nazik fallback mesajı ✓

### Test Sonucu Özeti

| Senaryo | Intent | Confidence | Source | Sonuç |
|---------|--------|-----------|--------|-------|
| 1. A1 rota | route_request | 0.909 | distilbert | ✅ |
| 2. B2 kalabalıksız | route_request | 0.912 | distilbert | ✅ |
| 3. C3 paraphrase | route_request | 0.855 | distilbert | ✅ |
| 4. Eco puanım | loyalty_query | 0.86 | distilbert | ✅ |
| 5. Lounge nerede | general_info | 0.90 | rule-based | ✅ |
| 6. Hava nasıl | unknown | 0.907 | distilbert | ✅ |

**6/6 başarılı** • DistilBERT %83 • Rule-based %17

### Frontend Tarayıcı Testleri (Manuel)

| Test | Senaryo | Sonuç |
|------|---------|-------|
| Login | passenger@ecoterminal.com / pass123 | ✓ |
| Chatbot açma | Sağ alt iconu tıkla | ✓ |
| Chatbot mesaj | "A1 nasıl giderim?" → rota kartı | ✓ |
| Intent badge | Mesajda "Rota" badge | ✓ |
| Haritada Göster | Heatmap'e yönlenme | ✓ |
| Heatmap render | Numaralı marker'lar, polyline | ✓ |
| Sonraki Adım | Aktif step ilerleme | ✓ |
| Temizle | Rota kayboluyor | ✓ |
| Loyalty sorusu | Badge "Sadakat", rota yok | ✓ |
| Heatmap zone tıklama | Detay paneli açılıyor | ✓ |
| Logout | Session temizleniyor | ✓ |

---

## Performans Metrikleri

### Backend

| Endpoint | P50 | P95 | P99 |
|----------|-----|-----|-----|
| POST /api/auth/login | 80ms | 150ms | 200ms |
| GET /api/heatmap/live | 25ms | 50ms | 80ms |
| POST /api/routes/optimal | 45ms | 90ms | 120ms |
| POST /api/chatbot/ask | 850ms | 3.5s | 5s |
| GET /api/ai/predictions | 15ms | 30ms | 50ms |

*Chatbot latency Gemini API çağrısına bağlı. Fallback durumunda < 500ms.*

### LLM Service

| İşlem | Süre |
|-------|------|
| Cold start (model load) | 4.1 sn |
| Warm inference (DistilBERT) | 50-80 ms |
| RAG retrieval (KB) | 1-2 ms |
| Backend call (Dijkstra) | 50-100 ms |
| Gemini API | 2-4 sn |
| **Toplam (Gemini ile)** | **3-5 sn** |
| **Toplam (Fallback)** | **< 500 ms** |

### Memory Usage

| Servis | İdle | Yüklü |
|--------|------|-------|
| backend | 600 MB | 1.2 GB |
| llm-service | 600 MB | 1.5 GB |
| postgres | 100 MB | 300 MB |
| redis | 30 MB | 100 MB |
| ai-service | 400 MB | 800 MB |

### Docker Image Boyutları

| Image | Boyut |
|-------|-------|
| eco-terminal-frontend | 95 MB |
| eco-terminal-backend | 509 MB |
| eco-terminal-llm-service | 3.95 GB (torch + model) |
| eco-terminal-ai-service | 4.3 GB (TF + XGBoost) |
| eco-terminal-yolov8-service | 2.8 GB |

---

## Hibrit Classifier İstatistiği

**Test seti:** 6 senaryo

```
distilbert ████████████████████████  5/6 (83%)
rule-based ████                       1/6 (17%)
```

**Karar mantığı doğrulaması:**
- DistilBERT confidence >= 0.85 → kullanıldı
- DistilBERT confidence < 0.85 (Senaryo 5'te 0.37) → rule-based devreye girdi

Bu davranış **tasarlanan şekilde** çalıştı.

---

## CI/CD

### GitHub Actions Pipeline

`.github/workflows/ci.yml`:

```
backend-test ──┐
  PostgreSQL    │
  Java 21       ├─► security-scan
  Maven test    │     OWASP Dependency Check
                │
frontend-test ─┤
  Node 20       │
  npm test      │
  npm build     │
                │
                └─► docker-build
                      Backend, Frontend, AI Service images
                      (push: false — CI only)
```

### Trigger

- Push → `main` veya `develop`
- Pull request → `main`

### Artifacts

- `surefire-reports/` — Backend test sonuçları
- `dependency-check-report.html` — OWASP security scan
- Test coverage raporu (jacoco)

---

## Bilinen Sorunlar

### 1. Gemini API Ücretsiz Tier Limiti

**Sorun:** Free tier 15 RPM / 1500 RPD. Yoğun test sırasında 429 hatası.

**Etki:** LLM cevap üretimi fallback template'lere düşer.

**Çözüm:**
- Bekle (RPM sıfırlanır, kısa süre)
- Yeni Google account → yeni API key
- Production için billing aç

### 2. flight_info Recall Düşük (%70)

**Sorun:** DistilBERT model 10 flight_info örneğinden 3'ünü kaçırdı.

**Sebep:** Bazı flight cümleleri route_request ile karışıyor ("TK1922 uçağı için yol").

**Çözüm:**
- Dataset'i genişlet (20-30 yeni flight örnek)
- Tekrar train et

### 3. Lazy Load First Request Yavaşlık

**Sorun:** İlk istek 4.1 saniye (DistilBERT model loading).

**Çözüm seçenekleri:**
- Container start sırasında preload (startup time uzar)
- Warm-up request (deployment script'e ekle)

### 4. Docker Image Boyutu (LLM Service 3.95 GB)

**Sorun:** Torch + CUDA kütüphaneleri tüm container'ı şişiriyor.

**Çözüm:**
- `torch-cpu-only` kullan (200 MB vs 800 MB)
- Multi-stage build (model'i ayrı stage'de)
- Şu an akademik proje için kabul edilebilir

### 5. Email Verification Devre Dışı (Default)

**Sorun:** `MAIL_ENABLED=false` ile email gönderimi simüle edilir.

**Çözüm:** Gmail SMTP veya SendGrid konfigürasyonu (`.env`).

---

## Test Yapma Rehberi

### Yeni Bir Endpoint Eklediğinde

1. **Unit test** — Service katmanı için Mockito ile
2. **Integration test** — `@SpringBootTest` ile Testcontainers
3. **Manual test** — curl veya Postman
4. **Swagger UI** — Otomatik dokümante edilir

### Yeni LLM Intent Eklediğinde

1. `IntentLabel` enum'a ekle (`base.py`)
2. Dataset'e 50+ örnek cümle ekle (`create_dataset.py`)
3. Rule-based keyword listesini güncelle (`rule_based.py`)
4. Modeli yeniden eğit (`python train.py`)
5. Test seti üzerinde accuracy kontrol et
6. `PromptBuilder` template'e ekle (varsa özel)

### Manual Smoke Test (5 Dakika)

```bash
# 1. Servisler ayakta
docker-compose ps

# 2. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"passenger@ecoterminal.com","password":"pass123"}' \
  | jq -r '.accessToken')

# 3. Chatbot test (6 senaryo)
for msg in \
  "A1 kapısına nasıl giderim" \
  "B2 en kalabalıksız" \
  "C3 yürümeli kaç dakika" \
  "Eco puanım" \
  "Lounge nerede" \
  "merhaba"; do
  echo "=== $msg ==="
  curl -s -X POST http://localhost:8080/api/chatbot/ask \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"message\":\"$msg\"}" | jq '{intent, confidence, provider, hasRoute: (.routeSteps|length>0)}'
  echo ""
done

# 4. Frontend görsel doğrulama
open http://localhost:3000
```
