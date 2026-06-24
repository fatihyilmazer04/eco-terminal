"""
Eco-Terminal AI Service — Tahmin endpoint'leri.
Blueprint: predict_bp

Tahmin akışı:
  1. XGBoostPredictor.is_available == True  →  XGBoost (Faz 2 modeli, güven=0.95)
  2. XGBoost exception  →  log + fallback'e düş
  3. Fallback: LSTM/ağırlıklı-ortalama (lstm_model.predictor)
"""
import logging
from datetime import datetime, timezone, timedelta
from flask import Blueprint, jsonify, request

from database import get_occupancy_history, get_zone_info, get_all_active_zones
from model.lstm_model import predictor as lstm_predictor
from model.xgboost_predictor import xgb_predictor
from model.risk_calculator import calculate_risk_level, calculate_trend, calculate_confidence

logger = logging.getLogger(__name__)
predict_bp = Blueprint("predict", __name__)


# =============================================================================
# FALLBACK TAHMİN  (LSTM / ağırlıklı ortalama)
# =============================================================================

def _fallback_prediction(zone_id: int, next_minutes: int) -> dict | None:
    """
    Mevcut LSTM/ağırlıklı-ortalama tahmini.
    XGBoost kullanılamadığında veya hata verdiğinde devreye girer.
    """
    zone = get_zone_info(zone_id)
    if zone is None:
        return None

    history        = get_occupancy_history(zone_id, limit=60)
    history_length = len(history)

    if history_length > 0:
        predicted_load  = lstm_predictor.predict(history)
        density_history = history[:, 1].tolist()   # density_pct sütunu
    else:
        import random
        predicted_load  = 0.50 + random.uniform(-0.08, 0.08)
        density_history = []

    predicted_load = round(float(predicted_load), 4)
    risk_level     = calculate_risk_level(predicted_load)
    trend          = calculate_trend(density_history)
    confidence     = calculate_confidence(history_length)

    now           = datetime.now(timezone.utc)
    forecast_time = now + timedelta(minutes=next_minutes)

    return {
        "zone_id":        zone_id,
        "zone_name":      zone["zone_name"],
        "forecast_time":  forecast_time.strftime("%Y-%m-%dT%H:%M:%S"),
        "predicted_load": predicted_load,
        "density_pct":    predicted_load,
        "risk_level":     risk_level,
        "trend":          trend,
        "confidence":     round(confidence, 2),
        "generated_at":   now.strftime("%Y-%m-%dT%H:%M:%S"),
    }


# =============================================================================
# ORTAK TAHMİN FONKSİYONU
# =============================================================================

def _build_prediction(zone_id: int, next_minutes: int) -> dict | None:
    """
    XGBoost → fallback zincirini çalıştırır.
    None döndürürse zone bulunamadı demektir.
    """
    # ── 1. XGBoost denemesi ───────────────────────────────────────────────────
    if xgb_predictor.is_available:
        try:
            result = xgb_predictor.predict(zone_id, next_minutes)
            logger.debug("XGBoost tahmin: zone=%d density=%.3f", zone_id, result["predicted_load"])
            return result
        except ValueError:
            # Zone bulunamadı → None döndür (404 için)
            return None
        except Exception as exc:
            logger.error(
                "XGBoost tahmin hatası (zone=%d), fallback'e geçiliyor: %s", zone_id, exc
            )

    # ── 2. Fallback (LSTM / ağırlıklı ortalama) ───────────────────────────────
    return _fallback_prediction(zone_id, next_minutes)


# =============================================================================
# ENDPOINT'LER
# =============================================================================

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
        return jsonify({"error": "Zone bulunamadı", "zone_id": zone_id}), 404

    model_used = "xgboost" if xgb_predictor.is_available else "fallback"
    logger.info(
        "Tahmin [%s]: zone=%d risk=%s load=%.3f",
        model_used, zone_id, result["risk_level"], result["predicted_load"],
    )
    return jsonify(result), 200


@predict_bp.route("/predict/all", methods=["GET"])
def predict_all():
    """
    GET /predict/all?next_minutes=30
    Tüm aktif bölgeler için tahmin listesi döndürür.
    """
    next_minutes = int(request.args.get("next_minutes", 30))

    # XGBoost varsa toplu predict_all (daha verimli)
    if xgb_predictor.is_available:
        try:
            results = xgb_predictor.predict_all(next_minutes)
            logger.info("Toplu tahmin [xgboost]: %d bölge", len(results))
            return jsonify(results), 200
        except Exception as exc:
            logger.error("XGBoost predict_all hatası, fallback'e geçiliyor: %s", exc)

    # Fallback: zone zone ağırlıklı ortalama
    zones   = get_all_active_zones()
    results = []
    for zone in zones:
        pred = _fallback_prediction(zone["zone_id"], next_minutes)
        if pred is not None:
            results.append(pred)

    logger.info("Toplu tahmin [fallback]: %d bölge", len(results))
    return jsonify(results), 200


@predict_bp.route("/health", methods=["GET"])
def health():
    """GET /health — servis ve aktif model durumu."""
    if xgb_predictor.is_available:
        active_model = "xgboost"
    elif lstm_predictor.is_trained:
        active_model = "lstm"
    else:
        active_model = "fallback"

    return jsonify({
        "status":       "ok",
        "model":        active_model,   # geriye dönük uyumluluk
        "active_model": active_model,   # yeni alan — hangi modun aktif olduğunu gösterir
        "timestamp":    datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
    }), 200
