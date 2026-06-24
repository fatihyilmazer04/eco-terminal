# 🔌 API Referansı

Eco-Terminal backend (Spring Boot) ve llm-service (FastAPI) REST endpoint dokümantasyonu.

## İçindekiler
- [Genel Bilgi](#genel-bilgi)
- [Backend API](#backend-api)
- [LLM Service API](#llm-service-api)
- [Hata Yanıtları](#hata-yanıtları)

---

## Genel Bilgi

### Base URL'ler

| Servis | URL |
|--------|-----|
| Backend | `http://localhost:8080` |
| LLM Service | `http://localhost:5002` |
| Swagger (Backend) | `http://localhost:8080/swagger-ui.html` |
| FastAPI Docs (LLM) | `http://localhost:5002/docs` |

### Kimlik Doğrulama

Backend endpoint'leri (auth dışında) **JWT** ile korunur:

```http
Authorization: Bearer <accessToken>
```

LLM service endpoint'leri **dahili** çağrılar için `X-Internal-Token` kullanır. Public testler için backend üzerinden çağırın.

---

## Backend API

### 🔐 Auth — `/api/auth`

#### POST `/api/auth/login`
Kullanıcı girişi.

**Request:**
```json
{
  "email": "passenger@ecoterminal.com",
  "password": "pass123"
}
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "user": {
    "id": 2,
    "email": "passenger@ecoterminal.com",
    "role": "USER"
  }
}
```

#### POST `/api/auth/register`
Yeni kullanıcı kaydı (email doğrulama tetikler).

#### POST `/api/auth/refresh-token`
Access token yenileme.

```json
{ "refreshToken": "eyJhbGc..." }
```

#### POST `/api/auth/forgot-password`
Şifre sıfırlama emaili gönderir.

#### POST `/api/auth/reset-password`
Yeni şifre belirler (token ile).

#### POST `/api/auth/verify-register`
Email doğrulama kodu kontrolü.

#### POST `/api/auth/send-code`
Doğrulama kodu yeniden gönderir.

---

### 🤖 Chatbot — `/api/chatbot`

#### POST `/api/chatbot/ask` ⭐
Doğal dil sorgusu — llm-service'e proxy.

**Request:**
```json
{
  "message": "A1 kapısına nasıl giderim?",
  "sessionId": "optional",
  "locale": "tr-TR"
}
```

**Response 200:**
```json
{
  "reply": "A1 kapısına gitmek için CheckIn-1 → Security-1 → Lounge-1 → Gate A1 rotasını öneriyorum. Toplam 4 dakika sürer.",
  "intent": "route_request",
  "confidence": 0.91,
  "routeSteps": [
    {
      "stepNumber": 1,
      "zoneName": "CheckIn-1",
      "instruction": "Check-in işleminizi tamamlayın",
      "estimatedWalkMinutes": 0
    },
    {
      "stepNumber": 2,
      "zoneName": "Security-1",
      "instruction": "Güvenlik kontrolünden geçin",
      "estimatedWalkMinutes": 1
    }
  ],
  "sourcesUsed": [
    "classifier:hybrid(distilbert+rule-based, threshold=0.85)",
    "knowledge_base",
    "backend:dijkstra",
    "gemini:gemini-2.0-flash"
  ],
  "provider": "llm-service"
}
```

#### POST `/api/chatbot/clear`
Kullanıcı oturum context'ini temizler.

---

### 🗺️ Routes — `/api/routes`

#### GET `/api/routes/suggest`
Aktif uçuş bileti olan kullanıcı için rota önerisi.

#### POST `/api/routes/optimal` ⭐
Belirli iki zone arası 3 alternatif rota (Dijkstra).

**Request:**
```json
{
  "fromZoneId": 4,
  "toZoneId": 1
}
```

**Response 200:**
```json
{
  "data": {
    "fromZone": "CheckIn-1",
    "toZone": "Gate A1",
    "alternatives": [
      {
        "strategy": "SHORTEST",
        "totalWalkSeconds": 232,
        "totalDistanceMeters": 305,
        "avgDensity": 0.35,
        "steps": [
          { "stepNumber": 1, "zoneName": "CheckIn-1", ... },
          { "stepNumber": 2, "zoneName": "Security-1", ... },
          { "stepNumber": 3, "zoneName": "Lounge-1", ... },
          { "stepNumber": 4, "zoneName": "Gate A1", ... }
        ]
      },
      {
        "strategy": "LEAST_CROWDED",
        "totalWalkSeconds": 280,
        ...
      },
      {
        "strategy": "BALANCED",
        "totalWalkSeconds": 250,
        ...
      }
    ]
  }
}
```

#### POST `/api/routes/checkin`
Yolcu bir zone'a vardığını işaretler (loyalty puanı).

```json
{
  "zoneId": 2,
  "ticketId": 15
}
```

#### POST `/api/routes/complete`
Tüm rotayı tamamladığında çağrılır.

---

### 📊 Heatmap — `/api/heatmap`

#### GET `/api/heatmap/live`
Tüm zone'ların anlık yoğunluk durumu (frontend heatmap data source).

**Response:**
```json
[
  {
    "zoneId": 1,
    "zoneName": "Gate A1",
    "posX": 61, "posY": 9,
    "width": 11, "height": 17,
    "densityPct": 35,
    "densityLevel": "MODERATE",
    "section": "A Concourse"
  },
  ...
]
```

#### GET `/api/heatmap/summary`
Heatmap özet istatistikleri (toplam, ortalama, en yoğun).

#### GET `/api/heatmap/history?zoneId=X&hours=24`
Zone bazlı zaman serisi.

---

### 🚪 Occupancy — `/api/occupancy`

#### GET `/api/occupancy/heatmap`
Heatmap için yoğunluk verisi.

#### GET `/api/occupancy/zone?name=Security-1`
Tek zone durumu.

#### GET `/api/occupancy/summary`
Tüm zone'lar için özet.

#### POST `/api/occupancy/threshold` (ADMIN)
Zone yoğunluk eşiklerini ayarlar.

---

### ✈️ Flights — `/api/flights`

#### GET `/api/flights`
Kullanıcının aktif uçuş bileti.

#### GET `/api/flights/{flightCode}`
Uçuş detayı.

#### POST `/api/flights/{id}/checkin`
Uçuşa check-in.

---

### 🛋️ Lounges — `/api/lounges`

#### GET `/api/lounges`
Tüm lounge'ları listele.

#### GET `/api/lounges/{id}/status`
Belirli lounge'un müsaitlik durumu.

---

### 💎 Loyalty — `/api/loyalty`

#### GET `/api/loyalty/wallet`
Kullanıcı eco-wallet detayı.

#### GET `/api/loyalty/rewards`
Mevcut ödüller.

#### POST `/api/loyalty/earn`
Puan kazandır (route completion sonrası tetiklenir).

#### POST `/api/loyalty/redeem`
Puan harca, ödül al.

#### GET `/api/loyalty/history`
İşlem geçmişi.

---

### 🔔 Notifications — `/api/notifications`

#### GET `/api/notifications`
Kullanıcının bildirimleri (sayfalanmış).

#### POST `/api/notifications/mark-read`
Okundu işaretle.

#### POST `/api/notifications/send` (ADMIN)
Manuel bildirim gönder.

#### POST `/api/fcm/token`
Firebase push token kaydet.

---

### 🤖 AI — `/api/ai`

#### GET `/api/ai/predictions`
Tüm zone'lar için 30-dakika ileriye tahmin.

#### GET `/api/ai/predictions/zone/{id}`
Belirli zone tahmini.

#### GET `/api/ai/predictions/accuracy`
Model accuracy metriği.

#### GET `/api/ai/predictions/forecast?horizon=1h|1w|1m`
1 saat / 1 hafta / 1 ay ileriye tahmin (admin paneli için).

#### GET `/api/ai/crowd/analyze`
YOLOv8 kalabalık tespiti tetikleyicisi.

---

### 👤 User Profile — `/api/profile`

#### GET `/api/profile`
Kullanıcı profil bilgisi.

#### PUT `/api/profile`
Profil güncelleme.

#### GET `/api/profile/preferences`
Tercihler (dil, bildirim ayarları).

#### PUT `/api/profile/preferences`
Tercih güncelleme.

---

### 🛠️ Admin — `/api/admin`

> Tüm endpoint'ler `ROLE_ADMIN` gerektirir.

#### GET `/api/admin/dashboard`
KPI özet (kullanıcı sayısı, aktif uçuş, vs.)

#### GET `/api/admin/users`
Kullanıcı listesi.

#### PUT `/api/admin/users/{id}`
Kullanıcı güncelleme.

#### DELETE `/api/admin/users/{id}`
Kullanıcı silme.

#### GET `/api/admin/audit-log`
Audit log görüntüleme.

#### GET `/api/admin/stats`
Sistem istatistikleri.

(... 8 endpoint daha — Swagger UI'da tam liste)

---

### ⚡ Energy — `/api/energy` (ADMIN)

#### GET `/api/energy/current`
Anlık enerji tüketimi.

#### GET `/api/energy/forecast?horizon=1h|1w|1m`
Enerji tahmini.

#### GET `/api/energy/trend`
Tüketim trendi.

#### POST `/api/energy/settings`
Eşik değer ayarı.

#### GET `/api/energy/summary`
Özet rapor.

---

### 📊 Stats — `/api/stats` (ADMIN)

#### GET `/api/stats/system`
Sistem sağlığı (CPU, memory).

#### GET `/api/stats/services`
Servislerin durumu.

#### GET `/api/stats/zones`
Zone bazlı istatistikler.

---

## LLM Service API

> Bu endpoint'ler **internal token** ile çağrılır. Production'da backend üzerinden ulaşılır.

### GET `/health`
Servis sağlığı.

**Response:**
```json
{
  "status": "ok",
  "service": "llm-service",
  "version": "0.1.0",
  "intent_classifier_enabled": true,
  "rag_enabled": true
}
```

### POST `/chat`
Tam RAG pipeline.

**Request:**
```json
{
  "message": "A1 kapısına nasıl giderim?",
  "user_id": 2,
  "session_id": "abc-123",
  "locale": "tr-TR"
}
```

**Response:** (Yukarıdaki `/api/chatbot/ask` ile aynı)

### GET `/docs`
Swagger-benzeri FastAPI dokümantasyonu (geliştirme amaçlı).

---

## Hata Yanıtları

### Standart Hata Yapısı

```json
{
  "timestamp": "2025-05-31T14:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Açıklayıcı hata mesajı",
  "path": "/api/auth/login"
}
```

### HTTP Status Kodları

| Kod | Anlam | Örnek |
|-----|-------|-------|
| 200 | OK | Başarılı sorgu |
| 201 | Created | Yeni kayıt oluşturuldu |
| 400 | Bad Request | Validation hatası |
| 401 | Unauthorized | JWT eksik/geçersiz |
| 403 | Forbidden | Yetki yok (örn. user → admin endpoint) |
| 404 | Not Found | Kaynak bulunamadı |
| 429 | Too Many Requests | Rate limit aşıldı |
| 500 | Internal Server Error | Sunucu hatası |
| 503 | Service Unavailable | Bağımlı servis (llm-service vb.) erişilemez |

### Sık Karşılaşılan Hatalar

#### 401 — JWT Eksik
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token is missing or invalid"
}
```
**Çözüm:** `/api/auth/login` ile yeni token al.

#### 403 — Yetki Yok
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: ROLE_ADMIN required"
}
```
**Çözüm:** Admin hesabı ile login ol.

#### 429 — Rate Limit
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Login attempts exceeded. Try again in X seconds."
}
```
**Çözüm:** 1 dakika bekle (`/api/auth/login` için 10 req/dk).

---

## Postman Collection

Backend Swagger UI'dan tüm endpoint'lerin OpenAPI 3.0 spec'i indirilebilir:

```
http://localhost:8080/v3/api-docs
```

Bu URL'i Postman'a import et: **Import → Link → Yapıştır**.
