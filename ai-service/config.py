"""
Eco-Terminal AI Service — Konfigürasyon sabitleri.
.env dosyasından ortam değişkenlerini okur.
"""
import os
from dotenv import load_dotenv

load_dotenv()

# ── Veritabanı ──────────────────────────────────────────────────────────────
DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://ecoterminal:ecoterminal123@localhost:5432/ecoterminal"
)

# Docker compose ortamında parçalı env değişkenleri
DB_HOST     = os.environ.get("DB_HOST", "localhost")
DB_PORT     = int(os.environ.get("DB_PORT", 5432))
DB_NAME     = os.environ.get("DB_NAME", "ecoterminal")
DB_USER     = os.environ.get("DB_USER", "ecoterminal")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "ecoterminal123")

PORT = int(os.environ.get("PORT", 5000))

# ── LSTM Model Sabitleri ─────────────────────────────────────────────────────
SEQUENCE_LENGTH    = 60    # son 60 dakikalık veri girdisi
PREDICTION_MINUTES = 30    # 30 dakika sonrası tahmin

# ── Yoğunluk Eşikleri ───────────────────────────────────────────────────────
LOW_THRESHOLD  = 0.60   # bu değerin altı → LOW risk
HIGH_THRESHOLD = 0.85   # bu değerin üstü → HIGH risk

# ── Uygulama ─────────────────────────────────────────────────────────────────
FLASK_ENV  = os.environ.get("FLASK_ENV", "production")
DEBUG_MODE = FLASK_ENV == "development"
