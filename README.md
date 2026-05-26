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

## YOLOv8 Kalabalık Tespiti & Yapay Dataset

### Yapay veri üretme

```bash
# PostgreSQL çalışıyor olmalı (docker compose up postgres)
# Proje kökünde:
pip install psycopg2-binary numpy python-dotenv

python scripts/generate_dataset.py --days 30 --zone-count 15
```

Çıktı: Eklenen kayıt sayısı, zone bazlı ortalama doluluk, en yoğun zone.

### YOLOv8 Servisi (port 5001)

```bash
# Docker ile (tüm servislerle birlikte)
docker compose up --build yolov8-service

# Manuel test
curl http://localhost:5001/health

# Tüm zone'lar için anlık detection (sentetik frame)
curl -X POST http://localhost:5001/detect/batch

# Tekil zone detection (gerçek görüntü yoksa sentetik)
curl -X POST http://localhost:5001/detect \
  -H "Content-Type: application/json" \
  -d '{"zone_id": 1}'
```

### Kalabalık Analiz Endpoint'leri

```bash
# Token ile (admin veya user)
TOKEN="Bearer <jwt_token>"

# Tüm zone'ların anlık durumu (Spring Boot)
curl -H "Authorization: $TOKEN" http://localhost:8080/api/crowd/status

# Flask AI kalabalık analizi (Spring Boot proxy)
curl -H "Authorization: $TOKEN" http://localhost:8080/api/ai/crowd-analysis

# Flask'a doğrudan (auth gerekmez)
curl http://localhost:5000/analyze/crowd
```

### Admin UI — Kalabalık İzleme

`http://localhost:3000` → Admin girişi → **Kalabalık İzleme** (sidebar)

---

## Monitoring — Grafana & Loki

### Erişim Adresleri

| Servis     | URL                       | Kullanıcı / Şifre     |
|-----------|---------------------------|----------------------|
| Grafana    | http://localhost:3001     | admin / admin123     |
| Prometheus | http://localhost:9090     | —                    |
| Loki       | http://localhost:3100     | —                    |

### Otomatik Provisioning

`docker-compose up` sonrası Grafana **otomatik olarak** şunlarla gelir — elle yapılandırma gerekmez:

| Kaynak        | Detay                                                      |
|--------------|-----------------------------------------------------------|
| Datasource   | Prometheus (`http://prometheus:9090`, varsayılan)          |
| Datasource   | Loki (`http://loki:3100`)                                  |
| Dashboard    | **Eco-Terminal — Backend Metrikleri** (Spring Boot / JVM)  |
| Dashboard    | **Eco-Terminal — Container Logları** (Loki log akışı)      |

### Mevcut Dashboard Panelleri

**Backend Metrikleri (`eco-terminal.json`)**

| Panel | Metrik |
|-------|--------|
| Toplam İstek / dk | `http_server_requests_seconds_count` |
| Hata Oranı (5xx) | Durum kodu filtrelenmiş istek oranı |
| Aktif DB Bağlantısı | `hikaricp_connections_active` |
| JVM Heap Kullanımı | `jvm_memory_used_bytes{area="heap"}` |
| JVM Bellek Trendi | Heap / non-heap / max MB zaman serisi |
| HTTP İstek Oranı | 2xx / 4xx / 5xx req/s |
| Yanıt Süresi (p50/p95/p99) | `http_server_requests_seconds_bucket` histogramı |
| HikariCP Havuzu | Aktif / boşta / bekleyen / max bağlantı |
| Top-10 Endpoint | URI bazında istek hacmi (bar gauge) |
| 4xx & 5xx Hata % | Toplam isteğe oranla hata yüzdesi |
| JVM Thread / GC / CPU | Thread sayısı, GC süresi, process CPU |

**Container Logları (`eco-logs.json`)**

| Panel | İçerik |
|-------|--------|
| Toplam Log / Hata / Uyarı | 1 saatlik istatistikler |
| Servis Log Hacmi | Container başına dakikadaki satır sayısı |
| Backend Logları | `eco-backend` container stream |
| AI Servisi Logları | `eco-ai-service` container stream |
| YOLOv8 Logları | `eco-yolov8` container stream |
| Tüm Hatalar | Tüm container'lardan `ERROR` filtresi |

### Log Toplama Mimarisi

```
Container stdout/stderr
    │
    ▼ (Docker log driver → /var/lib/docker/containers/)
Promtail
  • Docker SD (socket) ile container'ları keşfeder
  • compose_project / container_name / level label'ları ekler
    │
    ▼ push
Loki :3100  →  Grafana :3001
```

### Sık Kullanılan PromQL Sorguları

```promql
# Son 5 dk ortalama yanıt süresi (ms)
sum(rate(http_server_requests_seconds_sum{job="eco-terminal-backend"}[5m]))
/ sum(rate(http_server_requests_seconds_count{job="eco-terminal-backend"}[5m])) * 1000

# p95 yanıt süresi
histogram_quantile(0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{job="eco-terminal-backend"}[5m]))
) * 1000

# Endpoint bazında hata oranı
sum by (uri) (rate(http_server_requests_seconds_count{status=~"5..",job="eco-terminal-backend"}[5m]))

# Heap doluluk yüzdesi
100 * sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"})
```

### Sık Kullanılan LogQL Sorguları

```logql
# Backend'deki tüm hatalar
{container_name="eco-backend"} |= "ERROR"

# Belirli exception'ları filtrele
{container_name="eco-backend"} |= "Exception" | json | line_format "{{.log}}"

# Son 5 dk'da tüm servislerin hata oranı
sum(rate({compose_project="eco-terminal"} |= "ERROR" [5m])) by (container_name)
```

---

## Kubernetes ile Çalıştırma

`k8s/` klasöründeki manifest'ler minikube veya kind ile lokal olarak test edilebilir.
Mevcut `docker-compose.yml` korunmaktadır — K8s manifest'leri onun yanına eklenmiştir.

### Gereksinimler

- [minikube](https://minikube.sigs.k8s.io/) veya [kind](https://kind.sigs.k8s.io/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- Docker

### minikube ile Hızlı Başlangıç

```bash
# 1. Cluster başlat
minikube start --cpus=4 --memory=6g

# 2. Ingress controller etkinleştir
minikube addons enable ingress

# 3. Minikube'un Docker ortamını kullan (lokal image build için)
eval $(minikube docker-env)

# 4. Image'ları derle (minikube'un Docker daemon'ına)
docker build -t eco-terminal-backend:latest ./backend
docker build -t eco-terminal-frontend:latest ./frontend
docker build -t eco-terminal-ai:latest ./ai-service
docker build -t eco-terminal-yolov8:latest ./yolov8-service

# 5. Secret dosyasını üretim için özelleştir
# k8s/secret.yaml içindeki varsayılan değerleri değiştirin:
#   echo -n "gercek-sifreniz" | base64

# 6. Tüm manifest'leri uygula (sıralı: önce config, sonra infra, sonra app)
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/ai-service.yaml
kubectl apply -f k8s/yolov8-service.yaml
kubectl apply -f k8s/backend.yaml
kubectl apply -f k8s/frontend.yaml
kubectl apply -f k8s/prometheus.yaml
kubectl apply -f k8s/grafana.yaml
kubectl apply -f k8s/ingress.yaml

# Ya da tek komutla (sıra garantisi olmadan):
kubectl apply -f k8s/
```

### kind ile Hızlı Başlangıç

```bash
# 1. Cluster oluştur
kind create cluster --name eco-terminal

# 2. Ingress controller yükle
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

# 3. Image'ları kind cluster'ına yükle
docker build -t eco-terminal-backend:latest ./backend
docker build -t eco-terminal-frontend:latest ./frontend
docker build -t eco-terminal-ai:latest ./ai-service
docker build -t eco-terminal-yolov8:latest ./yolov8-service
kind load docker-image eco-terminal-backend:latest --name eco-terminal
kind load docker-image eco-terminal-frontend:latest --name eco-terminal
kind load docker-image eco-terminal-ai:latest --name eco-terminal
kind load docker-image eco-terminal-yolov8:latest --name eco-terminal

# 4. Manifest'leri uygula
kubectl apply -f k8s/
```

### Servislere Erişim

#### minikube

```bash
# Ingress IP'sini öğren
minikube ip
# Çıktı örneği: 192.168.49.2

# Frontend (React SPA)
open http://$(minikube ip)/

# Backend API
open http://$(minikube ip)/api/actuator/health

# Swagger UI
open http://$(minikube ip)/swagger-ui.html
```

#### Port-Forward (minikube ve kind için)

```bash
# Prometheus (NodePort 30090 veya port-forward)
kubectl port-forward svc/prometheus 9090:9090
# → http://localhost:9090

# Grafana (NodePort 30301 veya port-forward)
kubectl port-forward svc/grafana 3001:3001
# → http://localhost:3001  (admin / admin123)

# Backend doğrudan (Ingress'i bypass et)
kubectl port-forward svc/backend 8080:8080
```

### Durum Kontrolü

```bash
# Tüm pod'ların durumu
kubectl get pods

# Servisler
kubectl get services

# Ingress
kubectl get ingress

# Pod logları
kubectl logs -f deployment/backend
kubectl logs -f deployment/ai-service

# Pod başlatma sorunlarını incele
kubectl describe pod <pod-adı>
```

### Ölçeklendirme

```bash
# Backend'i 3 replica'ya çıkar
kubectl scale deployment backend --replicas=3

# Frontend'i 2'ye çıkar
kubectl scale deployment frontend --replicas=2
```

### Temizlik

```bash
# Tüm kaynakları sil
kubectl delete -f k8s/

# Minikube cluster'ı sil
minikube delete

# Kind cluster'ı sil
kind delete cluster --name eco-terminal
```

### k8s/ Klasör Yapısı

```
k8s/
├── configmap.yaml        # Env değişkenleri + Prometheus config
├── secret.yaml           # JWT secret, DB şifresi (git'e commit etme!)
├── postgres.yaml         # PVC + Deployment + Service
├── redis.yaml            # PVC + Deployment + Service
├── backend.yaml          # Spring Boot (liveness/readiness: /actuator/health)
├── frontend.yaml         # React/Nginx SPA
├── ai-service.yaml       # Flask LSTM tahmin servisi
├── yolov8-service.yaml   # YOLOv8 kalabalık tespit servisi
├── prometheus.yaml       # Prometheus + ConfigMap mount (NodePort: 30090)
├── grafana.yaml          # Grafana (NodePort: 30301)
└── ingress.yaml          # Nginx Ingress: /api → backend, / → frontend
```

> **Not:** `k8s/secret.yaml` dosyasını `.gitignore`'a ekleyin.
> Gerçek üretim ortamında [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)
> veya HashiCorp Vault gibi bir secret yönetim aracı kullanın.

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
| 9   | YOLOv8 tespiti, yapay dataset, AI analiz | ✅    |
| 10  | Grafana otomatik provisioning, Loki log toplama | ✅  |
