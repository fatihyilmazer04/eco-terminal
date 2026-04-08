"""
Eco-Terminal AI Service — Flask REST API
LSTM tabanlı yoğunluk tahmin servisi.
"""
import logging
import os

from flask import Flask
from flask_cors import CORS
from dotenv import load_dotenv

load_dotenv()

# ── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

# ── Flask App ─────────────────────────────────────────────────────────────────
app = Flask(__name__)

# CORS: Spring Boot ve frontend'den gelen çağrılara izin ver
CORS(app, origins=[
    "http://localhost:8080",
    "http://localhost:5173",
    "http://localhost:3000",
    "http://eco-backend:8080",
])

# ── Blueprint kayıt ──────────────────────────────────────────────────────────
from routes.predict import predict_bp
app.register_blueprint(predict_bp)

# ── Startup Logu ─────────────────────────────────────────────────────────────
@app.before_request
def _startup():
    pass  # her request öncesi çalışır, şu an boş

logger.info("AI Service baslatildi, model: fallback (LSTM egitim verisi bekleniyor)")

# ── Entry Point ───────────────────────────────────────────────────────────────
if __name__ == "__main__":
    port  = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("FLASK_ENV", "production") == "development"
    logger.info("Dinleniyor: 0.0.0.0:%d  (debug=%s)", port, debug)
    app.run(host="0.0.0.0", port=port, debug=debug)
