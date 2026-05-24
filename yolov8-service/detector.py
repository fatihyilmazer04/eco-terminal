"""
YOLOv8 Kalabalık Tespiti Modülü
================================
Gerçek veya sentetik görüntüden kişi sayar, DB'ye kaydeder.
"""
import base64
import io
import logging
import random
from datetime import datetime, timezone

import numpy as np
import psycopg2
import psycopg2.extras
from PIL import Image

from config import (
    DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD,
    YOLO_MODEL, PERSON_CLASS_ID, CONF_THRESHOLD,
    FRAME_WIDTH, FRAME_HEIGHT, HOUR_DENSITY_FACTOR,
)

logger = logging.getLogger(__name__)

# ── YOLOv8 modeli (lazy load) ─────────────────────────────────────────────────
_model = None

def get_model():
    global _model
    if _model is None:
        from ultralytics import YOLO
        logger.info("YOLOv8 modeli yükleniyor: %s", YOLO_MODEL)
        _model = YOLO(YOLO_MODEL)
        logger.info("YOLOv8 modeli hazır.")
    return _model


# ── Veritabanı ───────────────────────────────────────────────────────────────
def _get_conn():
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT,
        dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD,
        connect_timeout=5,
    )


def _save_reading(zone_id: int, people_count: int, density_pct: float,
                  source: str = "yolov8_live") -> None:
    """Tespit sonucunu occupancy_readings tablosuna kaydeder."""
    sql = """
        INSERT INTO occupancy_readings (zone_id, people_count, density_pct, source)
        VALUES (%s, %s, %s, %s)
    """
    try:
        with _get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, (zone_id, people_count, round(density_pct, 4), source))
            conn.commit()
    except psycopg2.Error as e:
        logger.error("DB kayıt hatası (zone=%d): %s", zone_id, e)


def _get_zone_capacity(zone_id: int) -> int | None:
    """Zone kapasitesini döndürür."""
    sql = "SELECT max_capacity FROM zones WHERE zone_id = %s AND status = 'ACTIVE'"
    try:
        with _get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, (zone_id,))
                row = cur.fetchone()
                return row[0] if row else None
    except psycopg2.Error as e:
        logger.error("Zone kapasitesi alınamadı: %s", e)
        return None


def _get_all_active_zones() -> list[dict]:
    """Tüm aktif zone'ları döndürür."""
    sql = "SELECT zone_id, zone_name, max_capacity, type FROM zones WHERE status = 'ACTIVE' ORDER BY zone_id"
    try:
        with _get_conn() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
                cur.execute(sql)
                return [dict(r) for r in cur.fetchall()]
    except psycopg2.Error as e:
        logger.error("Zone listesi alınamadı: %s", e)
        return []


# ── Sentetik frame üretici ───────────────────────────────────────────────────
def generate_synthetic_frame(zone_id: int, hour: int,
                              capacity: int = 100) -> np.ndarray:
    """
    Gerçek kamera olmadığında o zone ve saate özgü rastgele nokta
    dağılımlı 640×480 BGR numpy array döndürür.
    Noktalar YOLO ile algılanmaz — sadece density hesabı için kullanılır.
    """
    factor = HOUR_DENSITY_FACTOR[hour % 24]
    # Zone index'e göre küçük fark
    factor = min(factor + (zone_id % 5) * 0.03, 1.0)

    # Sentetik kişi sayısı
    max_people = max(1, int(capacity * factor))
    noise = random.randint(-max(1, max_people // 5), max(1, max_people // 5))
    people_count = max(0, min(capacity, max_people + noise))

    # Boş (siyah) frame — gerçek YOLO bunu işleyemez; sadece mock için
    frame = np.zeros((FRAME_HEIGHT, FRAME_WIDTH, 3), dtype=np.uint8)

    # people_count'u frame metadata olarak encode et (custom dict ile dönecek)
    frame._synthetic_people = people_count  # type: ignore[attr-defined]
    return frame


# ── Ana tespit fonksiyonu ─────────────────────────────────────────────────────
def detect_crowd(
    image_source,          # file path (str), base64 str veya np.ndarray
    zone_id: int,
    capacity: int,
    save_to_db: bool = True,
    source_tag: str = "yolov8_live",
) -> dict:
    """
    YOLOv8 ile kişi say, density hesapla, DB'ye kaydet.

    Returns:
        {zone_id, people_count, density_pct, confidence, timestamp, source}
    """
    now = datetime.now(timezone.utc)
    confidence_scores = []
    people_count = 0

    # ── Sentetik frame kısayolu ───────────────────────────────────────────────
    if isinstance(image_source, np.ndarray) and hasattr(image_source, "_synthetic_people"):
        people_count = image_source._synthetic_people  # type: ignore[attr-defined]
        confidence_scores = [0.90] * people_count
        source_tag = "yolov8_simulated"
        logger.debug("Sentetik frame: zone=%d kişi=%d", zone_id, people_count)

    else:
        # ── Gerçek YOLO inference ─────────────────────────────────────────────
        try:
            # base64 → PIL Image → numpy
            if isinstance(image_source, str) and not image_source.startswith("/"):
                img_bytes = base64.b64decode(image_source)
                image_source = np.array(Image.open(io.BytesIO(img_bytes)))

            model = get_model()
            results = model(image_source, conf=CONF_THRESHOLD, verbose=False)

            for result in results:
                boxes = result.boxes
                if boxes is not None:
                    for cls, conf in zip(boxes.cls, boxes.conf):
                        if int(cls.item()) == PERSON_CLASS_ID:
                            people_count += 1
                            confidence_scores.append(float(conf.item()))

        except Exception as e:
            logger.error("YOLO inference hatası (zone=%d): %s", zone_id, e)
            people_count = 0

    density_pct = min(1.0, people_count / max(1, capacity))
    avg_conf = float(np.mean(confidence_scores)) if confidence_scores else 0.0

    if save_to_db:
        _save_reading(zone_id, people_count, density_pct, source_tag)
        logger.info("Kaydedildi: zone=%d kişi=%d density=%.3f kaynak=%s",
                    zone_id, people_count, density_pct, source_tag)

    return {
        "zone_id":     zone_id,
        "people_count": people_count,
        "density_pct":  round(density_pct, 4),
        "confidence":   round(avg_conf, 3),
        "timestamp":    now.strftime("%Y-%m-%dT%H:%M:%S"),
        "source":       source_tag,
    }


def batch_detect_all_zones() -> list[dict]:
    """
    Tüm aktif zone'lar için sentetik frame ile detection çalıştırır.
    APScheduler veya /detect/batch endpoint'i tarafından çağrılır.
    """
    zones = _get_all_active_zones()
    hour = datetime.now(timezone.utc).hour
    results = []

    for zone in zones:
        frame = generate_synthetic_frame(zone["zone_id"], hour, zone["max_capacity"])
        result = detect_crowd(
            image_source=frame,
            zone_id=zone["zone_id"],
            capacity=zone["max_capacity"],
            save_to_db=True,
            source_tag="yolov8_simulated",
        )
        result["zone_name"] = zone["zone_name"]
        results.append(result)

    logger.info("Batch detection tamamlandı: %d zone", len(results))
    return results
