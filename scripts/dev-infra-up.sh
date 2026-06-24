#!/usr/bin/env bash
# =============================================================================
# Eco-Terminal — Dev Altyapı Başlatıcı
# Backend'i IDE'den çalıştırırken gerekli container'ları ayağa kaldırır.
#
# Kullanım:
#   chmod +x scripts/dev-infra-up.sh
#   ./scripts/dev-infra-up.sh
# =============================================================================
set -e

# .env dosyasından değişkenleri yükle (proje kökünde çalıştırılmalı)
if [ -f ".env" ]; then
  export $(grep -v '^#' .env | grep -v '^$' | xargs)
fi

DB_NAME=${DB_NAME:-ecoterminal}
DB_USER=${DB_USER:-ecoterminal}
DB_PASSWORD=${DB_PASSWORD:-ecoterminal123}

echo "==> [1/2] PostgreSQL container başlatılıyor..."
if docker ps -a --format '{{.Names}}' | grep -q '^eco-postgres-dev$'; then
  docker start eco-postgres-dev
  echo "    eco-postgres-dev zaten var, başlatıldı."
else
  docker run -d \
    --name eco-postgres-dev \
    -e POSTGRES_DB="${DB_NAME}" \
    -e POSTGRES_USER="${DB_USER}" \
    -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
    -p 5432:5432 \
    -v eco-postgres-dev-data:/var/lib/postgresql/data \
    --restart unless-stopped \
    postgres:15-alpine
  echo "    eco-postgres-dev oluşturuldu ve başlatıldı."
fi

echo "==> [2/2] Redis container başlatılıyor..."
if docker ps -a --format '{{.Names}}' | grep -q '^eco-redis-dev$'; then
  docker start eco-redis-dev
  echo "    eco-redis-dev zaten var, başlatıldı."
else
  docker run -d \
    --name eco-redis-dev \
    -p 6379:6379 \
    -v eco-redis-dev-data:/data \
    --restart unless-stopped \
    redis:7-alpine
  echo "    eco-redis-dev oluşturuldu ve başlatıldı."
fi

echo ""
echo "==> Hazırlık kontrolü yapılıyor..."
until docker exec eco-postgres-dev pg_isready -U "${DB_USER}" -d "${DB_NAME}" -q 2>/dev/null; do
  echo "    PostgreSQL henüz hazır değil, bekleniyor..."
  sleep 2
done
echo "    PostgreSQL hazır."

until docker exec eco-redis-dev redis-cli ping 2>/dev/null | grep -q PONG; do
  echo "    Redis henüz hazır değil, bekleniyor..."
  sleep 1
done
echo "    Redis hazır."

echo ""
echo "============================================"
echo "  Dev altyapı tamamen hazır!"
echo "  PostgreSQL : localhost:5432  (DB: ${DB_NAME})"
echo "  Redis      : localhost:6379"
echo ""
echo "  Artık backend'i IDE'den başlatabilirsin."
echo "  Run config : IntelliJ → 'Backend (Dev)'"
echo "               VS Code  → 'Backend (Dev)'"
echo "============================================"
