# 🛫 Eco-Terminal

> **Yapay zeka destekli akıllı havalimanı yönetim sistemi**
> Yolcu yoğunluğu tahmini, dinamik rota optimizasyonu ve doğal dil chatbot ile sürdürülebilir terminal deneyimi.

[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green)]()
[![React](https://img.shields.io/badge/React-18.3-blue)]()
[![Python](https://img.shields.io/badge/Python-3.11-yellow)]()
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)]()

---

## 📋 İçindekiler

- [Proje Özeti](#-proje-özeti)
- [Temel Özellikler](#-temel-özellikler)
- [Mimari](#-mimari)
- [Teknoloji Stack](#-teknoloji-stack)
- [Hızlı Başlangıç](#-hızlı-başlangıç)
- [Kullanım Senaryoları](#-kullanım-senaryoları)
- [Detaylı Dokümantasyon](#-detaylı-dokümantasyon)
- [Test Kullanıcıları](#-test-kullanıcıları)
- [Proje İstatistikleri](#-proje-istatistikleri)

---

## 🎯 Proje Özeti

Eco-Terminal, modern havalimanlarında yaşanan yoğunluk, enerji tüketimi ve yolcu yönlendirme sorunlarına çözüm getiren bir uygulamadır. Sistem **gerçek zamanlı yoğunluk verilerini**, **AI tahminlerini** ve **doğal dil arayüzünü** birleştirerek yolculara akıllı rotalar önerir ve havalimanı yönetimine analitik gösterge paneli sunar.

Proje kapsamında **kendi LLM modelimiz** (DistilBERT fine-tuning) eğitilmiş, **RAG (Retrieval-Augmented Generation)** pipeline'ı kurulmuş ve **Dijkstra algoritması** ile dinamik rota hesaplama gerçekleştirilmiştir.

## ✨ Temel Özellikler

### 🗺️ Akıllı Rota Önerisi
- 15 zone, 52 edge'li havalimanı graf yapısı
- Dijkstra algoritması ile en kısa yol hesaplama
- 3 farklı strateji: en kısa süre, en az kalabalık, dengeli
- Yoğunluk verisine göre dinamik edge weight ayarlama

### 🤖 Yapay Zeka Destekli Chatbot
- **DistilBERT fine-tuning** (kendi modelimiz, %85.9 accuracy)
- **Hibrit sınıflandırma** (DistilBERT + Rule-based fallback)
- **RAG pipeline** (Knowledge base + Dijkstra + Gemini API)
- 6 intent: rota, uçuş, yoğunluk, sadakat, genel bilgi, bilinmeyen

### 📊 Yoğunluk Tahmini
- XGBoost ile zone bazlı density forecasting
- 1 saat, 1 hafta, 1 ay ileriye tahmin
- LSTM fallback modeli
- 5 dakikalık otomatik refresh

### 🎨 Görsel Heatmap
- SVG tabanlı interaktif terminal haritası
- Gerçek zamanlı yoğunluk gösterimi
- Chatbot rotalarının harita üzerinde animasyonlu render'ı
- Adım adım yolcu yönlendirme

### 💎 Eco-Cüzdan & Sadakat
- 4 seviyeli tier sistemi (Bronze, Silver, Gold, Platinum)
- Eco-dostu rota tamamlayınca puan kazanma
- Ödül kataloğu ve harcama akışı

### 🔔 Bildirimler
- Firebase Cloud Messaging entegrasyonu
- Yoğunluk eşik aşımında otomatik bildirim
- Admin tarafından manuel bildirim gönderimi

### 🛡️ Güvenlik
- JWT tabanlı kimlik doğrulama
- Internal service-to-service token (LLM ↔ Backend)
- Rate limiting (Bucket4j + Redis)
- BCrypt password hashing

---

## 🏗️ Mimari

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React 18 + Vite + Tailwind)                      │
│  - ChatbotWidget, AirportHeatmap, Dashboard                 │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  Backend (Spring Boot 3.2 + Java 21)                        │
│  - JWT Auth, Dijkstra Engine, Chatbot Provider Chain        │
│  - 18 Controller, 34 Service, 30 Entity                     │
└──┬─────────────────┬────────────────────────┬───────────────┘
   ▼                 ▼                        ▼
┌──────┐    ┌─────────────────┐    ┌────────────────────────┐
│Redis │    │ PostgreSQL 15   │    │  llm-service (FastAPI) │
│      │    │ Flyway, 25 mig  │    │  - Hybrid Classifier   │
│Cache │    │ 15 zones        │    │  - DistilBERT (kendi)  │
│      │    │ 52 edges        │    │  - RAG + Gemini API    │
└──────┘    └─────────────────┘    └─────┬──────────────────┘
                                          │
                          ┌───────────────┼───────────────┐
                          ▼               ▼               ▼
                  ┌──────────────┐  ┌──────────┐  ┌──────────────┐
                  │ ai-service   │  │ Knowledge│  │  Gemini API  │
                  │ XGBoost      │  │   Base   │  │  2.0 Flash   │
                  │ LSTM         │  │  10 fact │  │  (external)  │
                  └──────────────┘  └──────────┘  └──────────────┘
```

**Detaylı mimari için:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

---

## 🛠️ Teknoloji Stack

### Backend
| Katman | Teknoloji |
|--------|-----------|
| Runtime | Java 21, Spring Boot 3.2.5 |
| Build | Maven 3.9.6 |
| ORM | Spring Data JPA + Hibernate |
| Veritabanı | PostgreSQL 15 + Flyway |
| Auth | JWT (jjwt 0.12.5), BCrypt strength=12 |
| Cache | Redis 7 |
| Rate Limiting | Bucket4j 8.10.1 |
| Push | Firebase Admin SDK 9.2.0 |
| Monitoring | Micrometer + Prometheus |
| Test | JUnit 5, Mockito, Testcontainers |

### Frontend
| Katman | Teknoloji |
|--------|-----------|
| UI | React 18.3.1 |
| Build | Vite 5.3.1 |
| Styling | Tailwind CSS 3.4.4 |
| HTTP | Axios 1.7.2 |
| Routing | React Router DOM 6.23.1 |
| Grafik | Recharts 2.12.7 |
| Toast | react-hot-toast 2.4.1 |
| Test | Vitest + Testing Library |

### LLM Service (Mikro Servis)
| Katman | Teknoloji |
|--------|-----------|
| Framework | Python 3.11 + FastAPI |
| ML | PyTorch 2.3 + Transformers 4.40 |
| Model | DistilBERT (multilingual, fine-tuned) |
| LLM API | Google Gemini 2.0 Flash |
| HTTP Client | httpx (async) |
| Server | Uvicorn + Gunicorn |

### Veri & Tahmin
| Katman | Teknoloji |
|--------|-----------|
| Yoğunluk Tahmini | XGBoost 2.0+, scikit-learn 1.3 |
| LSTM Fallback | TensorFlow 2.15 |
| CV (Kalabalık) | YOLOv8n (Ultralytics) |
| Feature Eng | Pandas, NumPy |

### Altyapı
| Katman | Teknoloji |
|--------|-----------|
| Container | Docker + Docker Compose (11 servis) |
| Orchestration | Kubernetes (11 manifest hazır) |
| CI/CD | GitHub Actions |
| Logging | Loki + Promtail + Grafana |
| Metrics | Prometheus + Spring Actuator |

---

## 🚀 Hızlı Başlangıç

### Gereksinimler

- Docker Desktop 4.x+ (en az 8 GB RAM ayrılmış)
- Git
- (Opsiyonel) Google Gemini API key — [Google AI Studio](https://aistudio.google.com)'dan ücretsiz alabilirsiniz

### Kurulum

```bash
# 1. Repoyu klonlayın
git clone <repo-url>
cd eco-terminal

# 2. Environment dosyasını oluşturun
cp .env.example .env

# 3. Gemini API key'i .env dosyasına ekleyin
# GEMINI_API_KEY=AIzaSy...

# 4. Tüm servisleri başlatın
docker-compose up -d

# 5. Servislerin hazır olmasını bekleyin (~60 saniye)
docker-compose ps
```

### Erişim Noktaları

| Servis | URL |
|--------|-----|
| 🌐 Frontend | http://localhost:3000 |
| ⚙️ Backend API | http://localhost:8080/api |
| 📚 Swagger UI | http://localhost:8080/swagger-ui.html |
| 🤖 LLM Service | http://localhost:5002 |
| 🤖 LLM Service Docs | http://localhost:5002/docs |
| 📊 Grafana | http://localhost:3001 (admin/admin123) |
| 📈 Prometheus | http://localhost:9090 |

### İlk Test

```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"passenger@ecoterminal.com","password":"pass123"}'

# Chatbot test (token'ı yukarıdan alın)
curl -X POST http://localhost:8080/api/chatbot/ask \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"message":"A1 kapısına nasıl giderim?"}'
```

---

## 💡 Kullanım Senaryoları

### Senaryo 1: Yolcu Rota Sorgusu

```
Kullanıcı: "A12 kapısına en kalabalıksız yoldan nasıl giderim?"

Sistem:
  1. DistilBERT → intent: route_request (%91 güven)
  2. Entity extraction → destination: Gate A12, preference: least_crowded
  3. Knowledge Base → A konkursu bilgileri çekilir
  4. Backend Dijkstra → 3 alternatif rota hesaplanır
  5. Gemini → Türkçe doğal dil cevap üretir
  6. Frontend → Rota kartı + "Haritada Göster" butonu
  7. Heatmap → Animasyonlu rota çizimi + adım adım yönlendirme
```

### Senaryo 2: Yoğunluk Sorgulama

```
Kullanıcı: "Security şu an yoğun mu?"

Sistem:
  1. DistilBERT → intent: crowd_query
  2. Backend /api/heatmap/live → tüm zone durumları
  3. Gemini → "Security-1 %75 dolu, Security-2 %35 dolu. Security-2 öneriyorum."
```

### Senaryo 3: Sadakat Programı

```
Kullanıcı: "Eco puanlarımı görmek istiyorum"

Sistem:
  1. DistilBERT → intent: loyalty_query (%85 güven)
  2. Knowledge Base → loyalty programı bilgisi
  3. Gemini → Kullanıcının seviyesi + ödül önerileri
```

---

## 📚 Detaylı Dokümantasyon

| Dokuman | Açıklama |
|---------|----------|
| [📐 ARCHITECTURE.md](docs/ARCHITECTURE.md) | Sistem mimarisi, servisler, veri akışı |
| [🔌 API.md](docs/API.md) | REST API endpoint referansı |
| [🧠 LLM_PIPELINE.md](docs/LLM_PIPELINE.md) | DistilBERT fine-tuning, RAG, hibrit sınıflandırma |
| [🚢 DEPLOYMENT.md](docs/DEPLOYMENT.md) | Docker, K8s, üretim deployment'ı |
| [🧪 TESTING.md](docs/TESTING.md) | Test stratejisi, sonuçlar, metrikler |

---

## 👥 Test Kullanıcıları

| E-posta | Şifre | Rol | Kullanım |
|---------|-------|-----|----------|
| admin@ecoterminal.com | admin123 | ADMIN | Yönetim paneli, raporlar |
| passenger@ecoterminal.com | pass123 | USER | Yolcu deneyimi |
| alice@ecoterminal.com | pass123 | USER | Test 1 |
| bob@ecoterminal.com | pass123 | USER | Test 2 |

---

## 📊 Proje İstatistikleri

| Metrik | Değer |
|--------|-------|
| Toplam kod satırı | ~35.000+ |
| Toplam dosya | ~400+ |
| Docker servisi | 11 |
| REST endpoint | 74 |
| Veritabanı migration | 25 |
| Veritabanı entity | 30 |
| Backend test | 8 sınıf |
| LLM eğitim dataset | 422 etiketli cümle |
| LLM test accuracy | %85.9 |
| Zone graf node | 15 |
| Zone graf edge | 52 (bidirectional) |
| Knowledge base fact | 10 |
| Geliştirme süresi | ~3 ay |

---

## 📝 Lisans

Bu proje bir bitirme tezi kapsamında hazırlanmıştır. Akademik kullanım için açıktır.

---

## 🙏 Teşekkürler

- **DistilBERT**: HuggingFace Transformers ekibine
- **Gemini API**: Google AI ekibine
- **Hands-On Large Language Models**: Jay Alammar & Maarten Grootendorst kitabına
- **Anthropic Claude**: Geliştirme sürecindeki kod desteği için
