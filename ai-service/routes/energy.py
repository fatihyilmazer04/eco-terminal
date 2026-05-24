"""
Energy Blueprint — kural tabanlı enerji optimizasyon önerileri.
Endpoint: GET /energy/recommendations?zone_id=<id>
         GET /energy/recommendations/all
"""
import logging
from flask import Blueprint, jsonify, request
from database import get_connection

logger = logging.getLogger(__name__)
energy_bp = Blueprint("energy", __name__, url_prefix="/energy")


def _get_zone_energy_data(zone_id=None):
    """
    Son enerji + doluluk verilerini çeker.
    zone_id verilmezse tüm aktif bölgeler döner.
    """
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            base_sql = """
                SELECT
                    z.zone_id,
                    z.zone_name,
                    z.type,
                    z.max_capacity,
                    COALESCE(em.energy_kwh, 0)   AS energy_kwh,
                    COALESCE(em.temp, 22.0)       AS temp,
                    COALESCE(em.lighting_lux, 400) AS lighting_lux,
                    COALESCE(occ.density_pct, 0)  AS density_pct,
                    COALESCE(occ.people_count, 0) AS people_count
                FROM zones z
                LEFT JOIN LATERAL (
                    SELECT energy_kwh, temp, lighting_lux
                    FROM environmental_metrics
                    WHERE zone_id = z.zone_id
                    ORDER BY recorded_at DESC
                    LIMIT 1
                ) em ON true
                LEFT JOIN LATERAL (
                    SELECT density_pct, people_count
                    FROM occupancy_readings
                    WHERE zone_id = z.zone_id
                    ORDER BY recorded_at DESC
                    LIMIT 1
                ) occ ON true
                WHERE z.status = 'ACTIVE'
            """
            if zone_id is not None:
                cur.execute(base_sql + " AND z.zone_id = %s", (zone_id,))
            else:
                cur.execute(base_sql + " ORDER BY z.zone_id")
            cols = [d[0] for d in cur.description]
            rows = [dict(zip(cols, row)) for row in cur.fetchall()]
        return rows
    except Exception as e:
        logger.error("Enerji verisi alınamadı: %s", e)
        return []
    finally:
        conn.close()


def _build_recommendation(zone_data):
    """
    Kural tabanlı enerji optimizasyon önerisi üretir.
    """
    density    = float(zone_data.get("density_pct", 0))
    energy_kwh = float(zone_data.get("energy_kwh", 0))
    temp       = float(zone_data.get("temp", 22))
    lux        = int(zone_data.get("lighting_lux", 400))
    zone_type  = zone_data.get("type", "GATE")

    recommendations = []
    priority = "LOW"
    saving_pct = 0

    # Kural 1: Düşük doluluk, yüksek enerji → tasarruf modu
    if density < 0.20 and energy_kwh > 15:
        recommendations.append("Bölge boş: aydınlatmayı %40 azaltın")
        saving_pct = max(saving_pct, 35)
        priority = "HIGH"

    # Kural 2: Sıcaklık optimizasyonu
    optimal_temp = 21.0 if zone_type in ("LOUNGE", "CHECKIN") else 22.0
    if abs(temp - optimal_temp) > 1.5:
        direction = "düşürün" if temp > optimal_temp else "artırın"
        recommendations.append(f"Sıcaklık {temp:.1f}°C — {optimal_temp:.1f}°C'ye {direction}")
        saving_pct = max(saving_pct, 12)
        if priority == "LOW":
            priority = "MEDIUM"

    # Kural 3: Aydınlatma optimizasyonu
    optimal_lux = 200 if density < 0.15 else 350 if density < 0.50 else 500
    if lux > optimal_lux + 100:
        recommendations.append(f"Aydınlatma {lux} lux — {optimal_lux} lux'a düşürün")
        saving_pct = max(saving_pct, 10)
        if priority == "LOW":
            priority = "MEDIUM"

    # Kural 4: Yüksek doluluk, düşük enerji → enerji artır (konfor)
    if density > 0.75 and energy_kwh < 5:
        recommendations.append("Bölge çok kalabalık: HVAC kapasitesini artırın")
        saving_pct = 0
        priority = "HIGH"

    if not recommendations:
        recommendations.append("Enerji tüketimi optimal seviyede")

    return {
        "zone_id":         zone_data["zone_id"],
        "zone_name":       zone_data["zone_name"],
        "density_pct":     round(density, 3),
        "energy_kwh":      round(energy_kwh, 2),
        "current_temp":    round(temp, 1),
        "current_lux":     lux,
        "recommendations": recommendations,
        "priority":        priority,
        "estimated_saving_pct": saving_pct,
    }


@energy_bp.route("/recommendations", methods=["GET"])
def get_zone_recommendation():
    """GET /energy/recommendations?zone_id=<id>"""
    zone_id = request.args.get("zone_id", type=int)
    if zone_id is None:
        return jsonify({"error": "zone_id parametresi gerekli"}), 400
    rows = _get_zone_energy_data(zone_id)
    if not rows:
        return jsonify({"error": "Bölge bulunamadı"}), 404
    return jsonify(_build_recommendation(rows[0]))


@energy_bp.route("/recommendations/all", methods=["GET"])
def get_all_recommendations():
    """GET /energy/recommendations/all — tüm bölgeler için öneriler"""
    rows = _get_zone_energy_data()
    results = [_build_recommendation(r) for r in rows]
    # Önce HIGH priority, sonra MEDIUM, sonra LOW
    order = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
    results.sort(key=lambda x: order.get(x["priority"], 3))
    return jsonify({
        "total": len(results),
        "high_priority": sum(1 for r in results if r["priority"] == "HIGH"),
        "recommendations": results,
    })
