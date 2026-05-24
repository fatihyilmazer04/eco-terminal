"""
Eco-Terminal AI Service — Kalabalık Analiz endpoint'i.
Blueprint: crowd_bp
Mevcut predict_bp yapısını korur, ayrı blueprint olarak kayıt edilir.
"""
import logging
from datetime import datetime, timezone, timedelta

import psycopg2
import psycopg2.extras
from flask import Blueprint, jsonify

from config import DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, LOW_THRESHOLD, HIGH_THRESHOLD

logger = logging.getLogger(__name__)
crowd_bp = Blueprint("crowd", __name__)


# ── Veritabanı yardımcıları ───────────────────────────────────────────────────
def _get_conn():
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD, connect_timeout=5,
    )


def _get_last_hour_readings() -> list[dict]:
    """
    Her aktif zone için son 1 saatlik occupancy_readings döndürür.
    Her satır: zone_id, zone_name, type, density_pct, recorded_at
    """
    since = datetime.now(timezone.utc) - timedelta(hours=1)
    sql = """
        SELECT
            z.zone_id,
            z.zone_name,
            z.type       AS zone_type,
            r.density_pct,
            r.people_count,
            r.recorded_at
        FROM occupancy_readings r
        JOIN zones z ON z.zone_id = r.zone_id
        WHERE z.status = 'ACTIVE'
          AND r.recorded_at >= %s
        ORDER BY z.zone_id, r.recorded_at ASC
    """
    try:
        with _get_conn() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
                cur.execute(sql, (since,))
                return [dict(row) for row in cur.fetchall()]
    except psycopg2.Error as e:
        logger.error("Son 1 saatlik veri çekilemedi: %s", e)
        return []


# ── Analiz yardımcıları ───────────────────────────────────────────────────────
def _density_to_status(density: float) -> str:
    if density >= HIGH_THRESHOLD:
        return "Dolu"
    if density >= 0.65:
        return "Yoğun"
    if density >= LOW_THRESHOLD:
        return "Normal"
    return "Boş"


def _calculate_trend(densities: list[float]) -> str:
    """Son 4 okumayı karşılaştırır → 'rising', 'falling', 'stable'."""
    if len(densities) < 2:
        return "stable"
    half = max(1, len(densities) // 2)
    avg_first = sum(densities[:half]) / half
    avg_last  = sum(densities[-half:]) / half
    diff = avg_last - avg_first
    if diff >  0.05:
        return "rising"
    if diff < -0.05:
        return "falling"
    return "stable"


def _make_recommendation(zone_name: str, zone_type: str,
                          status: str, trend: str) -> str:
    """Zone durumuna göre öneri üretir."""
    t = zone_type.upper()
    if status == "Dolu":
        if t == "GATE":
            # Aynı harfli başka gate öner
            prefix = zone_name[:6]  # 'Gate A'
            return f"{prefix}X'e alternatif yönlendirme yapın"
        if t == "SECURITY":
            return "Yolcuları ikinci güvenlik hattına yönlendirin"
        if t == "CHECKIN":
            return "Self check-in kioskları aktif edin"
        if t == "LOUNGE":
            return "Lounge girişi kısıtlanabilir"
    if status == "Yoğun" and trend == "rising":
        return f"{zone_name} dolmaya yakın, erken önlem alın"
    return "Normal operasyon"


# ── Endpoint ─────────────────────────────────────────────────────────────────
@crowd_bp.route("/analyze/crowd", methods=["GET"])
def analyze_crowd():
    """
    GET /analyze/crowd
    Tüm zone'ların son 1 saatlik verisini analiz eder.
    Gerçek DB verisinden özet üretir (hardcode değil).
    """
    rows = _get_last_hour_readings()
    now  = datetime.now(timezone.utc)

    # Zone başına grupla
    zone_map: dict[int, dict] = {}
    for row in rows:
        zid = row["zone_id"]
        if zid not in zone_map:
            zone_map[zid] = {
                "zone_id":   zid,
                "zone_name": row["zone_name"],
                "zone_type": row["zone_type"],
                "densities": [],
                "people":    [],
            }
        zone_map[zid]["densities"].append(float(row["density_pct"]))
        zone_map[zid]["people"].append(int(row["people_count"]))

    # Veri yoksa aktif zone'ları DB'den çek, 0 ile göster
    if not zone_map:
        logger.warning("Son 1 saatte occupancy_readings verisi yok")

    zone_results = []
    alert_zones: list[str] = []
    empty_zones:  list[str] = []

    for zid, info in sorted(zone_map.items()):
        densities = info["densities"]
        avg_density = sum(densities) / len(densities) if densities else 0.0
        trend  = _calculate_trend(densities)
        status = _density_to_status(avg_density)
        rec    = _make_recommendation(info["zone_name"], info["zone_type"], status, trend)

        zone_results.append({
            "zone_name":      info["zone_name"],
            "zone_type":      info["zone_type"],
            "status":         status,
            "density":        round(avg_density, 3),
            "trend":          trend,
            "recommendation": rec,
        })

        if status == "Dolu":
            alert_zones.append(info["zone_name"])
        elif status == "Boş":
            empty_zones.append(info["zone_name"])

    # Özet cümle oluştur (gerçek veriden)
    summary = _build_summary(zone_results, alert_zones, empty_zones)

    return jsonify({
        "timestamp":   now.strftime("%Y-%m-%dT%H:%M:%S"),
        "summary":     summary,
        "zones":       zone_results,
        "alert_zones": alert_zones,
        "empty_zones": empty_zones,
    }), 200


def _build_summary(zones: list[dict], alert: list[str], empty: list[str]) -> str:
    """Gerçek veriden özet cümle oluşturur."""
    if not zones:
        return "Henüz yeterli veri yok."

    parts = []
    if alert:
        parts.append(f"{', '.join(alert)} dolu")

    yogun = [z["zone_name"] for z in zones if z["status"] == "Yoğun"]
    if yogun:
        parts.append(f"{', '.join(yogun)} yoğun")

    if empty and len(empty) <= 3:
        parts.append(f"{', '.join(empty)} boş")
    elif empty:
        parts.append(f"{len(empty)} zone boş")

    normal_count = sum(1 for z in zones if z["status"] == "Normal")
    if normal_count > 0 and not parts:
        return f"Tüm zone'lar normal yoğunlukta ({len(zones)} bölge izleniyor)."

    summary = "; ".join(parts) + "." if parts else "Tüm zone'lar normal düzeyde."
    return summary[0].upper() + summary[1:]
