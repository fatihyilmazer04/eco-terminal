# Eco-Terminal

Akıllı Havalimanı Yoğunluk ve Enerji Yönetim Sistemi. Terminal içerisindeki bölgesel yolcu yoğunluğunu gerçek zamanlı analiz eder, enerji verimliliğini optimize eder, yolcuları düşük yoğunluklu rotalara yönlendirir ve LSTM tabanlı yapay zeka ile geleceğe yönelik yoğunluk tahminleri sunar.

---

## Mimari Diyagram

```
┌─────────────┐     ┌─────────────────────────────────────────┐
│   Browser   │────▶│  Nginx (frontend:80)                    │
│  React SPA  │     │  /api/* ──▶ backend:8080                │
└─────────────┘     └─────────────────────────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  Spring Boot      │
                    │  backend:8080     │◀── JWT Auth
                    └────┬──────┬───────┘
                         │      │
             ┌───────────▼─┐  ┌─▼──────────────┐
             │  PostgreSQL │  │  AI Service     │
             │  :5432      │  │  Flask :5000    │
             └─────────────┘  └────────────────┘
                    │
         ┌──────────▼──────────┐
         │  Prometheus :9090   │──▶  Grafana :3001
         └─────────────────────┘
```

---

## Teknoloji Yığını

| Katman       | Teknoloji                        |
|-------------|----------------------------------|
| Frontend     | React 18, Vite, Tailwind CSS, Recharts |
| Backend      | Spring Boot 3.2.5, Java 21, JWT  |
| Veritabanı   | PostgreSQL 15, Flyway migrations |
| AI / ML      | Python, Flask, TensorFlow LSTM   |
| Bildirimler  | Firebase Cloud Messaging (FCM)   |
| Cache        | Redis 7                          |
| Monitoring   | Prometheus + Grafana             |
| CI/CD        | GitHub Actions                   |
| Container    | Docker + Docker Compose          |

---

## Hızlı Başlangıç

```bash
# 1. Repoyu klonla
git clone <repo-url>
cd eco-terminal

# 2. Ortam değişkenlerini ayarla
cp .env.example .env
# .env dosyasını açıp POSTGRES_PASSWORD ve JWT_SECRET'i güncelle

# 3. Tüm servisleri başlat
docker-compose up --build

# 4. Uygulamayı aç
# Frontend:   http://localhost
# Backend:    http://localhost/api/actuator/health
# Swagger:    http://localhost/api/swagger-ui.html
# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3001  (admin / admin123)
```

---

## Test Kullanıcıları

| Rol    | E-posta                       | Şifre    |
|--------|-------------------------------|----------|
| Admin  | admin@ecoterminal.com         | admin123 |
| Yolcu  | passenger@ecoterminal.com     | pass123  |
| Yolcu  | alice@ecoterminal.com         | pass123  |
| Yolcu  | bob@ecoterminal.com           | pass123  |

---

## API Dokümantasyonu

Uygulama çalışırken Swagger UI'a erişilebilir:

```
http://localhost/api/swagger-ui.html
http://localhost/api/api-docs          (OpenAPI JSON)
```

---

## Test Çalıştırma

### Backend (JUnit 5 + Testcontainers)

```bash
cd backend

# Tüm testler (Docker gerekli — Testcontainers PostgreSQL başlatır)
mvn test

# Sadece unit testler (hızlı)
mvn test -Dgroups="unit"

# Test raporu
target/surefire-reports/
```

### Frontend (Vitest)

```bash
cd frontend

# Tek seferlik çalıştır
npm run test

# Watch modu
npm run test:watch
```

---

## Proje Yapısı

```
eco-terminal/
├── backend/                    # Spring Boot uygulaması
│   ├── src/main/java/com/ecoterminal/
│   │   ├── config/             # Security, Firebase, CORS
│   │   ├── controller/         # REST API endpoint'leri
│   │   ├── model/
│   │   │   ├── entity/         # JPA entity'leri
│   │   │   └── dto/            # Request/Response DTO'ları
│   │   ├── repository/         # Spring Data JPA repository'leri
│   │   ├── security/           # JWT, UserDetails
│   │   └── service/            # İş mantığı
│   └── src/test/               # Unit + Integration testler
│
├── frontend/                   # React uygulaması
│   ├── src/
│   │   ├── api/                # Axios API çağrıları
│   │   ├── components/         # Yeniden kullanılabilir bileşenler
│   │   ├── context/            # AuthContext
│   │   ├── hooks/              # Custom hook'lar
│   │   └── pages/              # Sayfa bileşenleri
│   └── src/components/__tests__/  # Vitest testler
│
├── ai-service/                 # Python Flask + LSTM tahmin servisi
├── monitoring/                 # Prometheus konfigürasyonu
├── docker-compose.yml          # Tüm servisler
├── .env.example                # Ortam değişkeni şablonu
└── .github/workflows/ci.yml    # GitHub Actions CI pipeline
```

---

## Faz Geliştirme Özeti

| Faz | Kapsam                                    | Durum |
|-----|------------------------------------------|-------|
| 0   | Proje kurulumu, DB şeması, temel yapı    | ✅    |
| 1   | JWT Auth (login, register, refresh)      | ✅    |
| 2   | Yoğunluk yönetimi, heatmap, sensör API   | ✅    |
| 3   | Enerji yönetimi, tasarruf önerileri      | ✅    |
| 4   | AI tahmin servisi (LSTM), admin dashboard | ✅    |
| 5   | AI frontend (PredictionCard, RiskBadge)  | ✅    |
| 6   | Bildirim sistemi (FCM, rate limiter)     | ✅    |
| 7   | Loyalty sistemi, profil, bekleme alanları | ✅    |
| 8   | Testler, Docker final, CI/CD             | ✅    |
