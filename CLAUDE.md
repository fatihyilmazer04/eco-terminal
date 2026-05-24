# Eco-Terminal — Claude Geliştirici Kılavuzu

Akıllı Havalimanı Yoğunluk ve Enerji Yönetim Sistemi.
Gerçek zamanlı yolcu yoğunluğu analizi, LSTM tabanlı tahmin, enerji optimizasyonu ve loyalty sistemi içerir.

---

## Mimari Genel Bakış

```
Browser (React SPA)
    │  HTTP/JSON
    ▼
Nginx :80  →  /api/* proxy  →  Spring Boot :8080
                                    │
                    ┌───────────────┼──────────────────┐
                    ▼               ▼                  ▼
             PostgreSQL :5432   Flask AI :5000    Firebase FCM
             (Flyway migrations) (LSTM model)    (Push bildirim)
                    │
             Prometheus :9090  →  Grafana :3001
```

- **Mimari:** Client-Server, MVC (backend), SPA (frontend)
- **Auth:** Stateless JWT (access: 15 dk, refresh: 7 gün)
- **Roller:** `ADMIN` ve `USER` (yolcu)
- **DB Versiyonlama:** Flyway (15 migration dosyası)
- **Containerization:** Docker Compose (8 servis)

---

## Teknoloji Yığını

| Katman       | Teknoloji & Versiyon                                |
|-------------|-----------------------------------------------------|
| Backend      | Spring Boot 3.2.5, Java 21, Maven                  |
| Frontend     | React 18.3.1, Vite, Tailwind CSS, Recharts         |
| AI Servisi   | Python 3.11, Flask 3.0, TensorFlow 2.15 (LSTM)     |
| Veritabanı   | PostgreSQL 15, Flyway, psycopg2                    |
| Güvenlik     | JWT (jjwt 0.12.5), BCrypt (strength=12), Bucket4j  |
| Bildirimler  | Firebase Admin SDK 9.2.0 (FCM)                     |
| Cache        | Redis 7 (Phase 2 için planlandı)                   |
| Monitoring   | Prometheus + Grafana, Spring Actuator              |
| CI/CD        | GitHub Actions (4 job pipeline)                    |
| Testler      | JUnit 5, Mockito, Testcontainers, Vitest           |

---

## Ortak Komutlar

### Geliştirme (Yerel)

```bash
# Tüm servisleri Docker ile başlat
docker-compose up --build

# Sadece veritabanını başlat (yerel geliştirme için)
docker-compose up postgres

# Backend — bağımsız çalıştır
cd backend
./mvnw spring-boot:run

# Frontend — bağımsız çalıştır
cd frontend
npm install
npm run dev      # http://localhost:5173

# AI Servisi — bağımsız çalıştır
cd ai-service
pip install -r requirements.txt
python app.py    # http://localhost:5000
```

### Test Çalıştırma

```bash
# Backend — tüm testler (Docker gerekli, Testcontainers PostgreSQL açar)
cd backend
mvn test

# Backend — sadece unit testler (Docker gerekmez)
mvn test -Dgroups="unit"

# Backend — CI modunda (Spring test profili ile)
mvn test -Dspring.profiles.active=test

# Backend — test raporu
# target/surefire-reports/

# Frontend — tek seferlik
cd frontend
npm run test

# Frontend — watch modu
npm run test:watch
```

### Derleme & CI

```bash
# Backend JAR oluştur
cd backend
mvn package -DskipTests

# Frontend production build
cd frontend
npm run build

# Docker image'larını ayrı ayrı derle
docker build -t eco-terminal-backend ./backend
docker build -t eco-terminal-frontend ./frontend
docker build -t eco-terminal-ai ./ai-service
```

---

## Proje Yapısı

```
eco-terminal/
├── .github/workflows/ci.yml   # GitHub Actions pipeline (4 job)
├── backend/                   # Spring Boot uygulaması
│   ├── src/main/java/com/ecoterminal/
│   │   ├── config/            # SecurityConfig, FirebaseConfig, RestTemplateConfig
│   │   ├── controller/        # 10 REST controller (AuthController, OccupancyController...)
│   │   ├── exception/         # GlobalExceptionHandler, BusinessException, AiServiceException
│   │   ├── model/
│   │   │   ├── dto/           # 38 DTO (Request/Response sınıfları)
│   │   │   └── entity/        # 30 JPA entity
│   │   ├── repository/        # 17 Spring Data JPA repository
│   │   ├── security/          # JwtService, JwtAuthFilter, CustomUserDetailsService, UserPrincipal
│   │   └── service/           # 18 iş mantığı servisi
│   ├── src/main/resources/
│   │   ├── application.yml    # Ana konfigürasyon
│   │   └── db/migration/      # V1__init.sql ... V15__*.sql
│   ├── src/test/              # 8 test dosyası (6 unit, 2 integration)
│   ├── pom.xml
│   └── Dockerfile             # Multi-stage (Maven → JRE 21 Alpine)
├── frontend/                  # React SPA
│   ├── src/
│   │   ├── api/               # 10 Axios modülü (axiosInstance.js dahil)
│   │   ├── components/        # 16 yeniden kullanılabilir bileşen
│   │   │   └── __tests__/     # 3 Vitest test dosyası
│   │   ├── context/           # AuthContext.jsx (useReducer + localStorage)
│   │   ├── hooks/             # 10 custom hook
│   │   └── pages/
│   │       ├── admin/         # AdminDashboard, OccupancyMgmt, EnergyMgmt, AIPredictions, Reports
│   │       ├── auth/          # LoginPage, RegisterPage
│   │       └── passenger/     # PassengerDashboard, Heatmap, Route, Flights, Lounges, Notifications, Profile
│   ├── nginx.conf             # SPA routing + /api proxy
│   ├── package.json
│   ├── vite.config.js
│   └── Dockerfile             # Multi-stage (Node 20 → Nginx Alpine)
├── ai-service/                # Python tahmin servisi
│   ├── app.py                 # Flask entry point
│   ├── config.py              # Sabitler ve eşik değerleri
│   ├── database.py            # psycopg2 bağlantı yönetimi
│   ├── model/
│   │   ├── lstm_model.py      # TensorFlow LSTM + fallback predictor
│   │   └── risk_calculator.py # Risk seviyesi ve trend hesaplama
│   ├── routes/predict.py      # /predict, /predict/all, /health
│   ├── requirements.txt
│   └── Dockerfile             # Python 3.11-slim
├── monitoring/prometheus.yml  # Prometheus scrape config
├── docker-compose.yml         # 8 servis (postgres, redis, backend, frontend, ai, prometheus, grafana)
├── .env.example               # Ortam değişkeni şablonu
└── README.md
```

---

## Backend — Kritik Detaylar

### Controller → Endpoint Haritası

| Controller | Endpoint | Rol |
|-----------|---------|-----|
| `AuthController` | `POST /api/auth/login`, `/register`, `/refresh` | Public |
| `OccupancyController` | `GET /api/zones`, `/api/occupancy/heatmap` | USER |
| `AIPredictionController` | `GET /api/ai/predictions`, `POST ...` | USER/ADMIN |
| `EnergyController` | `GET /api/energy/*` | ADMIN |
| `NotificationController` | `POST /api/notifications/send` | ADMIN |
| `LoyaltyController` | `GET/POST /api/loyalty/wallet`, `/earn`, `/spend` | USER |
| `UserProfileController` | `GET/PUT /api/profile/*` | USER |
| `FlightController` | `GET /api/flights/*` | USER |
| `RouteController` | `POST /api/route/suggest` | USER |
| `AdminController` | `GET /api/admin/dashboard` | ADMIN |
| `FcmTokenController` | `POST /api/fcm/register` | USER |

### Güvenlik Katmanı

```java
// SecurityConfig.java — temel kurallar
// - /api/auth/** → permitAll()
// - /actuator/** → permitAll()
// - Diğer tüm endpoint'ler → authenticated()
// - @PreAuthorize("hasRole('ADMIN')") → metod seviyesi yetkilendirme

// JwtAuthFilter.java
// Authorization: Bearer <token> header'ından token çıkarır
// JwtService ile doğrular, SecurityContext'e yerleştirir

// Rate limiting (Bucket4j)
// /api/auth/login → 10 istek/dakika per IP
```

### Önemli Servisler

- **`AIPredictionService`** — `@Scheduled` ile 5 dakikada bir Flask servisini çağırır, sonuçları `ai_predictions` tablosuna yazar
- **`NotificationRateLimiter`** — kullanıcı başına bölge bazında 5 dakika cooldown uygular
- **`CrowdAlertScheduler`** — yüksek yoğunluk tespitinde otomatik FCM bildirimi tetikler
- **`LoyaltyService`** — eco-puan kazanma/harcama, tier hesaplama (BRONZE/SILVER/GOLD/PLATINUM)
- **`RouteService`** — düşük yoğunluklu alternatif rota önerisi üretir

### Veritabanı Şeması (Temel Tablolar)

```sql
-- Kimlik Doğrulama
users (id, email, password_hash, role, created_at)
user_profiles (id, user_id, first_name, last_name, phone, preferences_json)
audit_logs (id, user_id, action, entity_type, entity_id, timestamp)

-- Bölge & Yoğunluk
zones (id, name, zone_type, capacity, warning_threshold, critical_threshold, is_active)
occupancy_readings (id, zone_id, people_count, density_pct, source, recorded_at)
iot_devices (id, zone_id, device_type, device_status, last_seen_at)

-- Uçuş & Bilet
airlines (id, iata_code, name)
flights (id, airline_id, flight_number, origin, destination, gate, departure_time, status)
tickets (id, user_id, flight_id, seat_class, booking_reference)

-- AI & Enerji
ai_predictions (id, zone_id, predicted_load, risk_level, trend, confidence, forecast_time, generated_at)
environmental_metrics (id, zone_id, energy_kwh, temperature, lighting_level, recorded_at)

-- Loyalty
eco_wallets (id, user_id, points_balance, tier_level, total_earned, total_spent)
reward_catalog (id, name, description, points_cost, reward_type, is_active)
transaction_history (id, wallet_id, points_delta, trans_type, description, created_at)

-- Bildirimler
notifications (id, user_id, zone_id, notification_type, title, body, is_read, sent_at)
```

### Flyway Migration Konvansiyonu

```
V1__init.sql        → Temel şema
V2__seed_data.sql   → Test verisi
V3__... → V15__...  → Artımlı değişiklikler
```

Yeni migration eklerken: `V16__<açıklama>.sql` formatında, `src/main/resources/db/migration/` altına.

---

## Frontend — Kritik Detaylar

### Axios Interceptor Davranışı

`src/api/axiosInstance.js` — tüm API çağrıları bu instance üzerinden geçer:
- Request interceptor: `Authorization: Bearer <accessToken>` header'ı ekler
- Response interceptor (401): refresh token ile token yenileme dener, başarısız olursa logout

### AuthContext Kullanımı

```jsx
// Hook ile kullan
const { user, token, login, logout, isAuthenticated } = useAuth();

// Korumalı route — src/components/PrivateRoute.jsx
// Admin koruması — src/components/AdminRoute.jsx
```

### Sayfa → Route Haritası (App.jsx)

```
/                    → auth+role bazlı redirect
/login               → LoginPage
/register            → RegisterPage
/passenger/dashboard → PassengerDashboard
/passenger/heatmap   → HeatmapPage (Recharts)
/passenger/route     → RouteSuggestionPage
/passenger/flights   → FlightInfoPage
/passenger/lounges   → LoungesPage
/passenger/notifications → NotificationsPage
/passenger/profile   → ProfilePage (loyalty tier, wallet)
/admin/dashboard     → AdminDashboard (KPI'lar)
/admin/occupancy     → OccupancyManagement
/admin/energy        → EnergyManagement
/admin/predictions   → AIPredictionsPage
/admin/reports       → ReportsPage
```

### Stil Konvansiyonu

- Tailwind CSS, dark mode varsayılan (`bg-gray-900`)
- Birincil renk: `eco-green` → `#2ECC71`
- Bileşenler kendi Tailwind class'larını içerir, ayrı CSS dosyası yok

---

## AI Servisi — Kritik Detaylar

### Endpoint'ler

```
GET /predict?zone_id=1&next_minutes=30   → Tek bölge tahmini
GET /predict/all?next_minutes=30         → Tüm aktif bölgeler
GET /health                              → Servis durumu
```

### LSTM Model Detayları

- **Mimari:** LSTM(64) → Dropout(0.2) → LSTM(32) → Dense(1, sigmoid)
- **Input:** Son 60 okuma — `[people_count, density_pct]` şeklinde (sequence_length=60, features=2)
- **Output:** Tahmin edilen `density_pct` (0.0 – 1.0)
- **Fallback:** Eğer 60'tan az okuma varsa → son 5 okumanın ağırlıklı ortalaması ± küçük gürültü

### Risk Seviyeleri

```python
density <= 0.60  → LOW
density < 0.85   → MEDIUM
density >= 0.85  → HIGH
```

### Spring Boot Entegrasyonu

`AIPredictionClient.java` → Flask'ın `/predict/all` endpoint'ini çağırır
`AIPredictionService.java` → `@Scheduled` (5 dk) ile çağırır, `ai_predictions` tablosuna yazar

---

## Kodlama Standartları

### Backend (Java)

- Tüm servisler `@Slf4j` + `@Transactional` annotation'ı kullanır
- Response'lar `ApiResponse<T>` generic wrapper'a sarılır
- Exception handling: `GlobalExceptionHandler` (`@RestControllerAdvice`)
  - `BusinessException` → 400
  - `AuthenticationException` → 401
  - `AccessDeniedException` → 403
  - `EntityNotFoundException` → 404
- Admin işlemleri `audit_logs` tablosuna yazılmalı
- Yeni endpoint eklerken `@PreAuthorize("hasRole('ADMIN')")` veya `hasRole('USER')` kullan

### Frontend (React)

- Her API domain için `src/api/` altında ayrı modül
- Her veri çekme operasyonu için `src/hooks/` altında custom hook
- Sayfa bileşenleri state tutabilir; küçük UI parçaları `src/components/` altına
- `react-hot-toast` ile kullanıcı geri bildirimi

### AI Servisi (Python)

- `config.py` — tüm sabitler ve eşik değerleri buraya
- `database.py` — tüm DB bağlantısı buradan geçer (bağlantı havuzu)
- Flask blueprint: `routes/predict.py`

---

## Ortam Değişkenleri

`.env.example` dosyasını kopyala ve değerleri güncelle:

```bash
POSTGRES_DB=ecoterminal
POSTGRES_USER=ecoterminal
POSTGRES_PASSWORD=<güçlü_şifre>
JWT_SECRET=<min_64_karakter_rastgele_string>
AI_SERVICE_URL=http://ai-service:5000
FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json
CORS_ORIGINS=http://localhost,http://localhost:3000
GRAFANA_PASSWORD=admin123
SPRING_PROFILE=dev
```

**Firebase:** `firebase-service-account.json` dosyası Git'e commit edilmez; CI/CD secret olarak enjekte edilir.

---

## CI/CD Pipeline (GitHub Actions)

`.github/workflows/ci.yml` — 4 paralel/sıralı job:

```
push/PR (main, develop)
    │
    ├── backend-test    → mvn test (PostgreSQL servis container ile)
    ├── frontend-test   → npm test + npm run build
    │
    ├── security-scan   → OWASP Dependency Check (backend-test'e bağlı)
    │
    └── docker-build    → 3 image derleme — push yok (backend+frontend test'e bağlı)
```

---

## Monitoring

- **Prometheus:** `http://localhost:9090`
  - Backend metrikleri: `http://backend:8080/actuator/prometheus`
  - Scrape aralığı: 15 saniye
- **Grafana:** `http://localhost:3001` (admin / admin123)
  - Data source: Prometheus
  - Özel dashboard'lar eklenebilir

---

## Test Kullanıcıları

| Rol   | E-posta                    | Şifre    |
|-------|---------------------------|----------|
| Admin | admin@ecoterminal.com     | admin123 |
| Yolcu | passenger@ecoterminal.com | pass123  |
| Yolcu | alice@ecoterminal.com     | pass123  |
| Yolcu | bob@ecoterminal.com       | pass123  |

---

## Swagger / API Dokümantasyonu

Uygulama çalışırken:
```
http://localhost/api/swagger-ui.html
http://localhost/api/api-docs          (OpenAPI JSON)
```

---

## Faz Geliştirme Durumu

| Faz | Kapsam | Durum |
|-----|--------|-------|
| 0 | Proje kurulumu, DB şeması, temel yapı | ✅ |
| 1 | JWT Auth (login, register, refresh) | ✅ |
| 2 | Yoğunluk yönetimi, heatmap, sensör API | ✅ |
| 3 | Enerji yönetimi, tasarruf önerileri | ✅ |
| 4 | AI tahmin servisi (LSTM), admin dashboard | ✅ |
| 5 | AI frontend (PredictionCard, RiskBadge) | ✅ |
| 6 | Bildirim sistemi (FCM, rate limiter) | ✅ |
| 7 | Loyalty sistemi, profil, bekleme alanları | ✅ |
| 8 | Testler, Docker final, CI/CD | ✅ |
