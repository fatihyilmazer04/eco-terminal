"""
YOLOv8 Servis — Flask REST API
================================
Port 5001 üzerinde çalışır. APScheduler ile her 15 dk batch detection.
"""
import logging
from datetime import datetime, timezone

from flask import Flask, jsonify, request
from flask_cors import CORS
from apscheduler.schedulers.background import BackgroundScheduler
from dotenv import load_dotenv

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

from config import PORT, DEBUG_MODE, DETECTION_INTERVAL_MINUTES
from detector import detect_crowd, batch_detect_all_zones, _get_zone_capacity

# ── Flask ─────────────────────────────────────────────────────────────────────
app = Flask(__name__)
CORS(app, origins=[
    "http://localhost:3000",
    "http://localhost:5173",
    "http://localhost:8080",
    "http://backend:8080",
    "http://eco-backend:8080",
])

# ── Son batch sonuçları (in-memory cache) ─────────────────────────────────────
_last_results: list[dict] = []
_last_run_at: str | None = None


def _run_batch_and_cache() -> list[dict]:
    """Batch detection çalıştırır ve sonucu önbellekte tutar."""
    global _last_results, _last_run_at
    results = batch_detect_all_zones()
    _last_results = results
    _last_run_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")
    return results


# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status":      "ok",
        "service":     "yolov8-service",
        "scheduler":   "running",
        "last_run_at": _last_run_at,
        "zones_cached": len(_last_results),
    }), 200


@app.route("/status", methods=["GET"])
def status():
    """
    GET /status
    Son batch detection sonuçlarını döndürür.
    Admin paneli tarafından YOLOv8 kamera durumu için kullanılır.
    """
    return jsonify({
        "last_run_at": _last_run_at,
        "zone_count":  len(_last_results),
        "zones":       _last_results,
    }), 200


@app.route("/detect", methods=["POST"])
def detect():
    """
    POST /detect
    Body: {"zone_id": 1, "image_base64": "<base64_string>"}
    image_base64 yoksa → sentetik sayım kullanılır.
    """
    body = request.get_json(silent=True) or {}
    zone_id = body.get("zone_id")
    image_b64 = body.get("image_base64") or None

    if zone_id is None:
        return jsonify({"error": "zone_id gerekli"}), 400

    try:
        zone_id = int(zone_id)
    except (ValueError, TypeError):
        return jsonify({"error": "zone_id geçersiz"}), 400

    capacity = _get_zone_capacity(zone_id)
    if capacity is None:
        return jsonify({"error": f"Zone bulunamadı: {zone_id}"}), 404

    # image_b64=None → detect_crowd sentetik sayım yapar
    source_tag = "yolov8_live" if image_b64 else "yolov8_simulated"
    result = detect_crowd(
        image_source=image_b64,
        zone_id=zone_id,
        capacity=capacity,
        save_to_db=True,
        source_tag=source_tag,
    )
    return jsonify(result), 200


@app.route("/detect/batch", methods=["POST"])
def detect_batch():
    """
    POST /detect/batch
    Tüm aktif zone'lar için sentetik detection çalıştırır ve önbelleğe alır.
    """
    try:
        results = _run_batch_and_cache()
        return jsonify({
            "detected_zones": len(results),
            "results":        results,
        }), 200
    except Exception as e:
        logger.error("Batch detection hatası: %s", e)
        return jsonify({"error": str(e)}), 500


# ── APScheduler ───────────────────────────────────────────────────────────────
def start_scheduler():
    scheduler = BackgroundScheduler(timezone="UTC")
    scheduler.add_job(
        func=_run_batch_and_cache,
        trigger="interval",
        minutes=DETECTION_INTERVAL_MINUTES,
        id="batch_detection",
        replace_existing=True,
    )
    scheduler.start()
    logger.info("Scheduler başlatıldı: her %d dakikada batch detection",
                DETECTION_INTERVAL_MINUTES)
    return scheduler


# ── Entry Point ───────────────────────────────────────────────────────────────
if __name__ == "__main__":
    _scheduler = start_scheduler()
    # İlk çalıştırmada hemen bir batch al (önbelleği doldur)
    try:
        _run_batch_and_cache()
    except Exception as exc:
        logger.warning("İlk batch başarısız (DB henüz hazır değil?): %s", exc)
    logger.info("YOLOv8 Servisi başlatılıyor: 0.0.0.0:%d (debug=%s)", PORT, DEBUG_MODE)
    app.run(host="0.0.0.0", port=PORT, debug=DEBUG_MODE, use_reloader=False)
