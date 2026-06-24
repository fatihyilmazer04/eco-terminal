#!/usr/bin/env bash
# =============================================================================
# Eco-Terminal — Dev Altyapı Durdurucu
# dev-infra-up.sh ile başlatılan container'ları durdurur.
#
# Kullanım:
#   ./scripts/dev-infra-down.sh           # durdurur (veri korunur)
#   ./scripts/dev-infra-down.sh --clean   # durdurur + volume'ları siler
# =============================================================================
set -e

CLEAN=false
if [ "${1}" = "--clean" ]; then
  CLEAN=true
fi

echo "==> [1/2] eco-postgres-dev durduruluyor..."
docker stop eco-postgres-dev 2>/dev/null && echo "    Durduruldu." || echo "    Zaten durmuş."

echo "==> [2/2] eco-redis-dev durduruluyor..."
docker stop eco-redis-dev 2>/dev/null && echo "    Durduruldu." || echo "    Zaten durmuş."

if [ "${CLEAN}" = true ]; then
  echo ""
  echo "==> --clean: container'lar ve volume'lar siliniyor..."
  docker rm eco-postgres-dev eco-redis-dev 2>/dev/null || true
  docker volume rm eco-postgres-dev-data eco-redis-dev-data 2>/dev/null || true
  echo "    Container ve volume'lar silindi. Veriler kayboldu."
else
  echo ""
  echo "    Veriler korundu. Tekrar başlatmak için: ./scripts/dev-infra-up.sh"
fi
