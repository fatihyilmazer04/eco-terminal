"""
YOLOv8 Servis — Flask REST API
================================
Port 5001 üzerinde çalışır. APScheduler ile her 15 dk batch detection.
"""
import logging
import os

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
    "http://localhost:8080",
    "http://backend:8080",
    "http://eco-backend:8080",
])


# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status":    "ok",
        "service":   "yolov8-service",
        "scheduler": "running",
    }), 200


@app.route("/detect", methods=["POST"])
def detect():
    """
    POST /detect
    Body: {"zone_id": 1, "image_base64": "<base64_string>"}
    """
    body = request.get_json(silent=True) or {}
    zone_id = body.get("zone_id")
    image_b64 = body.get("image_base64")

    if zone_id is None:
        return jsonify({"error": "zone_id gerekli"}), 400

    try:
        zone_id = int(zone_id)
    except (ValueError, TypeError):
        return jsonify({"error": "zone_id geçersiz"}), 400

    capacity = _get_zone_capacity(zone_id)
    if capacity is None:
        return jsonify({"error": f"Zone bulunamadı: {zone_id}"}), 404

    if not image_b64:
        # Gerçek görüntü yok → sentetik frame kullan
        from detector import generate_synthetic_frame
        from datetime import datetime, timezone
        frame = generate_synthetic_frame(zone_id, datetime.now(timezone.utc).hour, capacity)
        result = detect_crowd(frame, zone_id, capacity, save_to_db=True,
                              source_tag="yolov8_simulated")
    else:
        result = detect_crowd(image_b64, zone_id, capacity, save_to_db=True,
                              source_tag="yolov8_live")

    return jsonify(result), 200


@app.route("/detect/batch", methods=["POST"])
def detect_batch():
    """
    POST /detect/batch
    Tüm aktif zone'lar için sentetik detection çalıştırır.
    """
    try:
        results = batch_detect_all_zones()
        return jsonify({
            "detected_zones": len(results),
            "results": results,
        }), 200
    except Exception as e:
        logger.error("Batch detection hatası: %s", e)
        return jsonify({"error": str(e)}), 500


# ── APScheduler ───────────────────────────────────────────────────────────────
def start_scheduler():
    scheduler = BackgroundScheduler(timezone="UTC")
    scheduler.add_job(
        func=batch_detect_all_zones,
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
    logger.info("YOLOv8 Servisi başlatılıyor: 0.0.0.0:%d (debug=%s)", PORT, DEBUG_MODE)
    app.run(host="0.0.0.0", port=PORT, debug=DEBUG_MODE, use_reloader=False)
