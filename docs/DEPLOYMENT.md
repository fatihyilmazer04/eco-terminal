# 🚢 Deployment Rehberi

Eco-Terminal'in Docker Compose, Kubernetes ve geliştirme ortamı kurulum dokümantasyonu.

## İçindekiler
- [Geliştirme Ortamı](#geliştirme-ortamı)
- [Docker Compose Production](#docker-compose-production)
- [Kubernetes](#kubernetes)
- [Veritabanı Migration](#veritabanı-migration)
- [Environment Variables](#environment-variables)
- [Sorun Giderme](#sorun-giderme)

---

## Geliştirme Ortamı

### Sistem Gereksinimleri

| Bileşen | Minimum | Önerilen |
|---------|---------|----------|
| RAM | 8 GB | 16 GB |
| Disk | 20 GB boş | 40 GB SSD |
| CPU | 4 core | 8 core |
| OS | Linux, macOS, Windows 10+ | Ubuntu 22.04 / macOS / Win11 |
| Docker | 20.10+ | Latest stable |
| Docker Compose | v2 | v2 |

### Yazılım Bağımlılıkları (Lokal Geliştirme)

Sadece Docker yeterli ama lokal geliştirme için:

- **Java 21** (backend)
- **Node 20+** (frontend)
- **Python 3.11** (llm-service, ai-service)
- **Maven 3.9+**
- **Git**

### İlk Kurulum

```bash
# 1. Klonla
git clone <repo-url>
cd eco-terminal

# 2. Environment hazırla
cp .env.example .env
nano .env  # Veya VSCode'da aç
```

`.env` dosyasında doldurulması gerekenler:

```bash
# ====================
# DATABASE
# ====================
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres123
POSTGRES_DB=ecoterminal

# ====================
# SECURITY
# ====================
JWT_SECRET=cok-uzun-en-az-32-karakter-secret-key-buraya
LLM_SERVICE_INTERNAL_TOKEN=ecoterminal-llm-secret-degistir-bunu

# ====================
# GEMINI API (chatbot için)
# ====================
GEMINI_API_KEY=AIzaSyAbc123...  # Google AI Studio'dan al
GEMINI_MODEL=gemini-2.0-flash

# ====================
# EMAIL (opsiyonel)
# ====================
MAIL_ENABLED=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your@gmail.com
MAIL_PASSWORD=your-app-password

# ====================
# FIREBASE (push notifications, opsiyonel)
# ====================
FIREBASE_CONFIG_PATH=/app/firebase-config.json
```

### Servisleri Başlatma

```bash
# Hepsini başlat (ilk başta build edecek, 5-10 dk sürer)
docker-compose up -d

# Build cache'i yenile (eğer bağımlılıklar değiştiyse)
docker-compose build --no-cache backend llm-service

# Sadece belirli servisi başlat
docker-compose up -d postgres redis backend

# Sağlık durumu
docker-compose ps

# Log takip
docker-compose logs -f backend
docker-compose logs -f llm-service

# Durdurma
docker-compose down

# Veriyi de sil (DİKKAT: postgres data gider)
docker-compose down -v
```

### Erişim Noktaları

| Servis | URL | Login |
|--------|-----|-------|
| Frontend | http://localhost:3000 | passenger@ecoterminal.com / pass123 |
| Backend API | http://localhost:8080 | JWT gerekir |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| LLM Service | http://localhost:5002 | — |
| LLM Service Docs | http://localhost:5002/docs | — |
| Grafana | http://localhost:3001 | admin / admin123 |
| Prometheus | http://localhost:9090 | — |

---

## Docker Compose Production

### Production İçin Hazırlık

`.env.production` dosyası oluştur:

```bash
# Güvenli production secret'ları
JWT_SECRET=$(openssl rand -hex 32)
LLM_SERVICE_INTERNAL_TOKEN=$(openssl rand -hex 32)
POSTGRES_PASSWORD=$(openssl rand -hex 24)

# Production Gemini API key
GEMINI_API_KEY=AIzaSy...

# Mail aktif
MAIL_ENABLED=true
```

`docker-compose.prod.yml` (override) oluştur:

```yaml
services:
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: prod
    restart: always
  
  llm-service:
    environment:
      LOG_LEVEL: WARNING
    restart: always
  
  postgres:
    volumes:
      - /mnt/persistent/postgres-data:/var/lib/postgresql/data
    restart: always
```

Başlatma:

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.production up -d
```

### Reverse Proxy (Nginx)

Public erişim için Nginx önerilir:

```nginx
server {
    listen 443 ssl;
    server_name ecoterminal.example.com;
    
    ssl_certificate /etc/letsencrypt/live/.../fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/.../privkey.pem;
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
    
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### SSL/TLS

Let's Encrypt önerilir:

```bash
certbot --nginx -d ecoterminal.example.com
```

### Backup Stratejisi

```bash
# Postgres günlük backup (crontab)
0 2 * * * docker-compose exec -T postgres pg_dump -U postgres ecoterminal | gzip > /backup/db-$(date +\%F).sql.gz

# 7 günden eski backup'ları sil
0 3 * * * find /backup -name "*.sql.gz" -mtime +7 -delete
```

---

## Kubernetes

### Mevcut Manifest'ler

`k8s/` dizininde **11 manifest** hazır:

```
k8s/
├── configmap.yaml       # App konfigürasyonu
├── secret.yaml          # JWT, DB password, vs.
├── postgres.yaml        # StatefulSet + PVC
├── redis.yaml           # Deployment
├── backend.yaml         # Deployment + Service + initContainers
├── ai-service.yaml      # Deployment
├── yolov8-service.yaml  # Deployment
├── prometheus.yaml      # Deployment
├── grafana.yaml         # Deployment
├── ingress.yaml         # Routing (/, /api/, /admin/)
└── ... (llm-service eklenecek - hazır şablon var)
```

### Hızlı Deployment (minikube)

```bash
# Cluster başlat
minikube start --cpus=4 --memory=8192

# Namespace oluştur
kubectl create namespace eco-terminal

# Secret'ları oluştur
kubectl apply -f k8s/secret.yaml -n eco-terminal

# Configmap
kubectl apply -f k8s/configmap.yaml -n eco-terminal

# Veritabanı ilk
kubectl apply -f k8s/postgres.yaml -n eco-terminal
kubectl wait --for=condition=ready pod -l app=postgres -n eco-terminal --timeout=120s

# Redis
kubectl apply -f k8s/redis.yaml -n eco-terminal

# Backend
kubectl apply -f k8s/backend.yaml -n eco-terminal

# Diğerleri
kubectl apply -f k8s/ -n eco-terminal

# Ingress + URL
minikube tunnel  # Ayrı terminalde çalıştır
kubectl get ingress -n eco-terminal
```

### Resource Limits

Backend için tipik limit (`backend.yaml`):

```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1Gi
```

LLM-service için (model dosyaları büyük):

```yaml
resources:
  requests:
    cpu: 500m
    memory: 1Gi
  limits:
    cpu: 2000m
    memory: 2Gi
```

### Healthcheck (Liveness/Readiness)

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 90
  periodSeconds: 30
  
readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
```

---

## Veritabanı Migration

### Flyway

Spring Boot başlangıçta `backend/src/main/resources/db/migration/` altındaki SQL dosyalarını sırayla çalıştırır.

### Mevcut Migration'lar (25 dosya)

```
V1__init.sql                    Schema, 16 tablo
V2__seed_users.sql              4 test user
V3__seed_zones.sql              15 zone
V4__seed_occupancy.sql          Yoğunluk seed
V5__seed_airlines.sql           Havayolu
V6__seed_flights.sql            Uçuş
V7__seed_tickets.sql            Bilet
V8__seed_energy.sql             Enerji
V9__reseed_energy_and_occupancy
V10__add_ai_prediction_fields
V11__add_notification_zone
V12__add_fcm_token
V13__seed_notifications
V14__update_loyalty_schema
V15__seed_loyalty
V16__yolov8_zones
V17__heatmap_positions
V18__add_occupancy_unique_constraint
V19__data_retention_indexes
V20__refresh_seed_data
V21__realistic_terminal_layout  ⭐ V17 koordinatları override eder
V22__email_verification
V23__route_checkins
V24__route_completions
V25__zone_connections           ⭐ Yeni eklenen — Dijkstra graf
```

### Yeni Migration Ekleme

```bash
# backend/src/main/resources/db/migration/V26__yeni_feature.sql
CREATE TABLE yeni_tablo (
    id BIGSERIAL PRIMARY KEY,
    ...
);

INSERT INTO yeni_tablo VALUES ...;
```

```bash
docker-compose restart backend
# Logda: "Migrating schema public to version 26"
```

### Migration Rollback

Flyway community sürümünde otomatik rollback **yok**. Manuel SQL ile geri al:

```bash
docker-compose exec postgres psql -U postgres -d ecoterminal -c "
  DELETE FROM flyway_schema_history WHERE version = '26';
  DROP TABLE yeni_tablo;
"
```

---

## Environment Variables

### Backend

| Variable | Default | Açıklama |
|----------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Profile (dev / prod / test) |
| `POSTGRES_HOST` | `postgres` | DB host (container name) |
| `POSTGRES_PORT` | `5432` | DB port |
| `POSTGRES_DB` | `ecoterminal` | DB name |
| `POSTGRES_USER` | `postgres` | DB user |
| `POSTGRES_PASSWORD` | — | DB password (required) |
| `REDIS_HOST` | `redis` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | — | JWT signing secret (required, 32+ char) |
| `LLM_SERVICE_INTERNAL_TOKEN` | — | Internal auth secret |
| `LLM_SERVICE_BASE_URL` | `http://llm-service:5002` | LLM service URL |
| `AI_SERVICE_BASE_URL` | `http://ai-service:5000` | AI service URL |
| `YOLOV8_SERVICE_BASE_URL` | `http://yolov8-service:5001` | YOLOv8 URL |
| `GEMINI_API_KEY` | — | (Eski ChatbotProvider için, llm-service kullanıyorsa boş olabilir) |
| `MAIL_*` | — | SMTP konfigürasyonu (opsiyonel) |
| `FIREBASE_CONFIG_PATH` | `/app/firebase-config.json` | FCM JSON path (opsiyonel) |

### LLM Service

| Variable | Default | Açıklama |
|----------|---------|----------|
| `GEMINI_API_KEY` | — | Gemini API key (required) |
| `GEMINI_MODEL` | `gemini-2.0-flash` | Model adı |
| `BACKEND_BASE_URL` | `http://backend:8080` | Backend URL (RAG için) |
| `BACKEND_INTERNAL_TOKEN` | — | Internal auth token |
| `LOG_LEVEL` | `INFO` | Python logging level |
| `ENABLE_INTENT_CLASSIFIER` | `true` | Hibrit classifier on/off |
| `ENABLE_RAG` | `true` | RAG pipeline on/off |
| `INTENT_CLASSIFIER_MODE` | `hybrid` | hybrid / distilbert / rule_based |

### AI Service

| Variable | Default | Açıklama |
|----------|---------|----------|
| `DATABASE_URL` | — | psycopg2 connection string |
| `MODEL_DIR` | `/app/models` | XGBoost model path |

---

## Sorun Giderme

### Port Çakışması

```bash
# 8080 portu kullanımda
sudo lsof -i :8080
# kapatmak için
sudo kill -9 <PID>
```

### Database Connection Failed

```bash
# Postgres healthy mi?
docker-compose exec postgres pg_isready -U postgres

# Logları kontrol et
docker-compose logs postgres
```

### Backend Başlamıyor

```bash
# Detaylı log
docker-compose logs --tail=200 backend

# Yaygın sebepler:
# 1. JWT_SECRET çok kısa (32+ karakter olmalı)
# 2. Postgres henüz hazır değil → depends_on healthcheck doğru mu?
# 3. Flyway migration hatası → SQL syntax kontrol
```

### LLM Service "Out of Memory"

```bash
# Docker Desktop'ta RAM artır (Settings → Resources)
# Veya model'i lazy-load yap (zaten yapılmış)

# Container memory'sini kontrol et
docker stats eco-llm-service
```

### Gemini "429 Too Many Requests"

```bash
# Free tier limits:
# - 15 RPM (dakikada istek)
# - 1500 RPD (günde istek)

# Çözüm:
# 1. Bekle (saatte sıfırlanır)
# 2. Yeni API key (yeni Google account)
# 3. Billing açık (production için)
```

### Frontend "Backend Unreachable"

```bash
# Frontend container içinden test
docker-compose exec frontend curl http://backend:8080/actuator/health

# Eğer çalışmıyorsa, network problem
docker network ls
docker network inspect eco-network
```

### Disk Doldu

```bash
# Docker resource cleanup
docker system prune -a --volumes  # DİKKAT: tüm unused image/container/volume silinir

# Sadece dangling images
docker image prune

# Loki log volume
du -sh $(docker volume inspect eco_loki-data -f '{{ .Mountpoint }}')
```

### "Cannot connect to Docker daemon"

```bash
# Linux
sudo systemctl start docker
sudo usermod -aG docker $USER

# macOS / Windows
# Docker Desktop'ı başlat
```

---

## Performance Tuning

### Backend JVM

```bash
# docker-compose.yml backend.environment:
JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=100
```

### Postgres

`postgresql.conf` (özel config gerekirse):

```
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 16MB
max_connections = 100
```

### Redis

`redis.conf`:

```
maxmemory 256mb
maxmemory-policy allkeys-lru
```

### LLM Service

```bash
# .env
GUNICORN_WORKERS=2  # CPU core sayısı kadar
GUNICORN_THREADS=4
```

---

## Monitoring & Observability

### Grafana Dashboard

Grafana'da hazır dashboard:
- **Eco Terminal Overview** — Backend latency, request rate, error rate
- **JVM Metrics** — Heap, GC, threads
- **Database** — Active connections, slow queries
- **LLM Service** — Inference latency, classifier source distribution

### Loki Log Query

Grafana → Explore → Loki:

```logql
# Backend error'lar
{container_name="eco-backend"} |= "ERROR"

# LLM service intent classifications
{container_name="eco-llm-service"} |= "intent_classified"

# Gemini timeout'lar
{container_name="eco-llm-service"} |= "gemini_timeout"
```

### Prometheus Alert Örnekleri

`prometheus/alerts.yml`:

```yaml
groups:
  - name: eco-terminal
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "Backend error rate > 5%"
      
      - alert: LLMServiceDown
        expr: up{job="llm-service"} == 0
        for: 2m
        annotations:
          summary: "LLM service unreachable"
```
