"""
Eco-Terminal AI Service — Tahmin endpoint'leri.
Blueprint: predict_bp
"""
import logging
from datetime import datetime, timezone, timedelta
from flask import Blueprint, jsonify, request

from database import get_occupancy_history, get_zone_info, get_all_active_zones
from model.lstm_model import predictor
from model.risk_calculator import calculate_risk_level, calculate_trend, calculate_confidence

logger = logging.getLogger(__name__)
predict_bp = Blueprint("predict", __name__)


def _build_prediction(zone_id: int, next_minutes: int) -> dict | None:
    """
    Tek bir bölge için tahmin oluşturur.
    Döndürür: tahmin dict veya None (zone bulunamadı).
    """
    zone = get_zone_info(zone_id)
    if zone is None:
        return None

    # Son 60 okumayi çek
    history = get_occupancy_history(zone_id, limit=60)
    history_length = len(history)

    # Tahmin
    if history_length > 0:
        predicted_load = predictor.predict(history)
        density_history = history[:, 1].tolist()   # density_pct sütunu
    else:
        # Hiç veri yoksa orta değer + hafif noise
        import random
        predicted_load = 0.50 + random.uniform(-0.08, 0.08)
        density_history = []

    predicted_load = round(float(predicted_load), 4)
    risk_level     = calculate_risk_level(predicted_load)
    trend          = calculate_trend(density_history)
    confidence     = calculate_confidence(history_length)

    now           = datetime.now(timezone.utc)
    forecast_time = now + timedelta(minutes=next_minutes)

    return {
        "zone_id":       zone_id,
        "zone_name":     zone["zone_name"],
        "forecast_time": forecast_time.strftime("%Y-%m-%dT%H:%M:%S"),
        "predicted_load": predicted_load,
        "density_pct":   predicted_load,
        "risk_level":    risk_level,
        "trend":         trend,
        "confidence":    round(confidence, 2),
        "generated_at":  now.strftime("%Y-%m-%dT%H:%M:%S"),
    }


@predict_bp.route("/predict", methods=["GET"])
def predict_zone():
    """
    GET /predict?zone_id=1&next_minutes=30
    Tek bölge için tahmin döndürür.
    """
    zone_id_str  = request.args.get("zone_id")
    next_minutes = int(request.args.get("next_minutes", 30))

    if not zone_id_str:
        return jsonify({"error": "zone_id parametresi gerekli"}), 400

    try:
        zone_id = int(zone_id_str)
    except ValueError:
        return jsonify({"error": "zone_id geçersiz"}), 400

    result = _build_prediction(zone_id, next_minutes)
    if result is None:
        return jsonify({"error": "Zone not found", "zone_id": zone_id}), 404

    logger.info("Tahmin: zone=%d risk=%s load=%.3f", zone_id, result["risk_level"], result["predicted_load"])
    return jsonify(result), 200


@predict_bp.route("/predict/all", methods=["GET"])
def predict_all():
    """
    GET /predict/all?next_minutes=30
    Tüm aktif bölgeler için tahmin listesi döndürür.
    """
    next_minutes = int(request.args.get("next_minutes", 30))
    zones = get_all_active_zones()

    results = []
    for zone in zones:
        pred = _build_prediction(zone["zone_id"], next_minutes)
        if pred is not None:
            results.append(pred)

    logger.info("Toplu tahmin: %d bölge", len(results))
    return jsonify(results), 200


@predict_bp.route("/health", methods=["GET"])
def health():
    """GET /health — servis ve model durumu."""
    model_mode = "fallback" if not predictor.is_trained else "lstm"
    return jsonify({
        "status":    "ok",
        "model":     model_mode,
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
    }), 200
