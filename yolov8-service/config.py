"""
YOLOv8 Servisi — Konfigürasyon sabitleri.
"""
import os
from dotenv import load_dotenv

load_dotenv()

# ── Veritabanı ───────────────────────────────────────────────────────────────
DB_HOST     = os.environ.get("POSTGRES_HOST", os.environ.get("DB_HOST", "localhost"))
DB_PORT     = int(os.environ.get("DB_PORT", 5432))
DB_NAME     = os.environ.get("POSTGRES_DB", os.environ.get("DB_NAME", "ecoterminal"))
DB_USER     = os.environ.get("POSTGRES_USER", os.environ.get("DB_USER", "ecoterminal"))
DB_PASSWORD = os.environ.get("POSTGRES_PASSWORD", os.environ.get("DB_PASSWORD", "ecoterminal123"))

# ── YOLOv8 ───────────────────────────────────────────────────────────────────
YOLO_MODEL      = os.environ.get("YOLO_MODEL", "yolov8n.pt")
PERSON_CLASS_ID = 0          # COCO dataset: class 0 = person
CONF_THRESHOLD  = 0.35       # Minimum detection confidence

# ── Servis ───────────────────────────────────────────────────────────────────
PORT       = int(os.environ.get("PORT", 5001))
FLASK_ENV  = os.environ.get("FLASK_ENV", "production")
DEBUG_MODE = FLASK_ENV == "development"

# ── Scheduler ────────────────────────────────────────────────────────────────
DETECTION_INTERVAL_MINUTES = 15   # Her 15 dakikada bir batch detection

# ── Sentetik frame ───────────────────────────────────────────────────────────
FRAME_WIDTH  = 640
FRAME_HEIGHT = 480

# Saat bazlı kalabalık yoğunluk katsayısı (0-23 saat için)
HOUR_DENSITY_FACTOR = [
    0.1, 0.05, 0.05, 0.05, 0.05, 0.10,   # 00-05
    0.40, 0.75, 0.85, 0.70, 0.60, 0.55,  # 06-11
    0.50, 0.55, 0.65, 0.70, 0.75, 0.72,  # 12-17
    0.80, 0.75, 0.65, 0.50, 0.30, 0.15,  # 18-23
]
