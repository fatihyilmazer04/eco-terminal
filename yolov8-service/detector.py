"""
YOLOv8 Kalabalık Tespiti Modülü
================================
Gerçek veya sentetik görüntüden kişi sayar, DB'ye kaydeder.
"""
import base64
import io
import logging
import os
import random
from datetime import datetime, timezone, timedelta

import numpy as np
import psycopg2
import psycopg2.extras
from PIL import Image, ImageDraw

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


def _has_recent_live_reading(zone_id: int, minutes: int = 20) -> bool:
    """
    Son `minutes` dakika içinde zone için yolov8_live kaydı var mı?
    Batch simülasyon bu zone'u atlamalı.
    """
    cutoff = datetime.now(timezone.utc) - timedelta(minutes=minutes)
    sql = """
        SELECT 1 FROM occupancy_readings
        WHERE zone_id = %s AND source = 'yolov8_live'
          AND recorded_at >= %s
        LIMIT 1
    """
    try:
        with _get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, (zone_id, cutoff))
                return cur.fetchone() is not None
    except psycopg2.Error as e:
        logger.error("Canlı okuma kontrolü hatası (zone=%d): %s", zone_id, e)
        return False  # Hata durumunda simülasyona izin ver


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


# ── Sentetik kişi sayısı hesaplayıcı ────────────────────────────────────────
def _compute_synthetic_count(zone_id: int, hour: int, capacity: int) -> int:
    """
    Saat ve zone index'e göre gerçekçi kişi sayısı üretir.
    Numpy array attribute magic kullanmaz — saf hesaplama.
    """
    factor = HOUR_DENSITY_FACTOR[hour % 24]
    factor = min(factor + (zone_id % 5) * 0.03, 1.0)
    max_people = max(1, int(capacity * factor))
    noise = random.randint(-max(1, max_people // 5), max(1, max_people // 5))
    return max(0, min(capacity, max_people + noise))


def generate_synthetic_frame(zone_id: int, hour: int,
                              capacity: int = 100) -> np.ndarray:
    """
    Görsel olarak kişileri temsil eden dikdörtgenler içeren 640×480
    BGR numpy array döndürür. Sadece görsel amaçlıdır; kişi sayısı
    _compute_synthetic_count ile ayrıca hesaplanır.
    """
    count = _compute_synthetic_count(zone_id, hour, capacity)

    img = Image.new("RGB", (FRAME_WIDTH, FRAME_HEIGHT), color=(20, 20, 20))
    draw = ImageDraw.Draw(img)

    rng = random.Random(zone_id * 1000 + hour)
    for _ in range(count):
        x = rng.randint(10, FRAME_WIDTH - 30)
        y = rng.randint(10, FRAME_HEIGHT - 60)
        w, h = rng.randint(15, 25), rng.randint(40, 60)
        r = rng.randint(150, 220)
        g = rng.randint(100, 180)
        b = rng.randint(80, 140)
        draw.rectangle([x, y, x + w, y + h], fill=(r, g, b), outline=(255, 255, 255))

    return np.array(img)


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
    image_source=None → doğrudan sentetik sayım (kamera yok).

    Returns:
        {zone_id, people_count, density_pct, confidence, timestamp, source}
    """
    now = datetime.now(timezone.utc)
    confidence_scores = []
    people_count = 0
    detections = []

    if image_source is None:
        # ── Kamera yok: sentetik sayım ───────────────────────────────────────
        people_count = _compute_synthetic_count(zone_id, now.hour, capacity)
        confidence_scores = [0.90] * people_count
        source_tag = "yolov8_simulated"
        logger.debug("Sentetik sayım: zone=%d kişi=%d", zone_id, people_count)

    else:
        # ── Gerçek YOLO inference ─────────────────────────────────────────────
        try:
            # base64 → PIL Image → numpy
            if isinstance(image_source, str) and not os.path.exists(image_source):
                img_bytes = base64.b64decode(image_source)
                image_source = np.array(Image.open(io.BytesIO(img_bytes)))

            model = get_model()
            results = model(image_source, conf=CONF_THRESHOLD, iou=0.4, verbose=False)

            for result in results:
                boxes = result.boxes
                if boxes is not None:
                    for box in boxes:
                        if int(box.cls[0].item()) == PERSON_CLASS_ID:
                            conf = float(box.conf[0].item())
                            x1, y1, x2, y2 = box.xyxy[0].tolist()
                            people_count += 1
                            confidence_scores.append(conf)
                            detections.append({
                                "bbox":       [round(x1, 1), round(y1, 1),
                                               round(x2, 1), round(y2, 1)],
                                "confidence": round(conf, 3),
                            })

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
        "zone_id":      zone_id,
        "people_count": people_count,
        "density_pct":  round(density_pct, 4),
        "confidence":   round(avg_conf, 3),
        "timestamp":    now.strftime("%Y-%m-%dT%H:%M:%S"),
        "source":       source_tag,
        "detections":   detections,
    }


def batch_detect_all_zones() -> list[dict]:
    """
    Tüm aktif zone'lar için sentetik sayım yapar, DB'ye kaydeder.
    APScheduler veya /detect/batch endpoint'i tarafından çağrılır.
    """
    zones = _get_all_active_zones()
    results = []

    for zone in zones:
        zone_id  = zone["zone_id"]
        capacity = zone.get("max_capacity") or zone.get("capacity") or 100

        # Son 20 dk içinde gerçek kamera analizi yapıldıysa simülasyonu atla
        if _has_recent_live_reading(zone_id, minutes=20):
            logger.info("Zone %d son 20dk'da canlı analiz aldı — simülatör atlandı", zone_id)
            continue

        # Kamera yok → None geç, detect_crowd sentetik sayım yapar
        result = detect_crowd(
            image_source=None,
            zone_id=zone_id,
            capacity=capacity,
            save_to_db=True,
            source_tag="yolov8_simulated",
        )
        result["zone_name"] = zone.get("zone_name", f"Zone {zone_id}")
        results.append(result)

    logger.info("Batch detection tamamlandı: %d zone", len(results))
    return results
