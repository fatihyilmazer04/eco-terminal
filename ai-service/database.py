"""
Eco-Terminal AI Service — PostgreSQL bağlantısı ve veri çekme.
psycopg2 ile doğrudan bağlantı kullanılır (SQLAlchemy değil).
"""
import logging
import numpy as np
import psycopg2
import psycopg2.extras
from config import DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD

logger = logging.getLogger(__name__)


def get_connection():
    """Yeni bir PostgreSQL bağlantısı döndürür."""
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD,
        connect_timeout=5
    )


def get_occupancy_history(zone_id: int, limit: int = 60) -> np.ndarray:
    """
    Belirli bir bölgenin son `limit` adetlik doluluk okumasını döndürür.
    Döndürür: shape (n, 2) numpy array — [people_count, density_pct]
    Veri yoksa boş array döndürür.
    """
    sql = """
        SELECT people_count, density_pct
        FROM occupancy_readings
        WHERE zone_id = %s
        ORDER BY recorded_at DESC
        LIMIT %s
    """
    try:
        with get_connection() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
                cur.execute(sql, (zone_id, limit))
                rows = cur.fetchall()
                if not rows:
                    return np.array([]).reshape(0, 2)
                # Kronolojik sıra (DESC'ten ASC'e çevir)
                data = np.array(
                    [(row["people_count"], float(row["density_pct"])) for row in reversed(rows)],
                    dtype=np.float32
                )
                return data
    except psycopg2.Error as e:
        logger.error("DB hatası: %s", e)
        return np.array([]).reshape(0, 2)


def get_zone_info(zone_id: int) -> dict | None:
    """Zone adı ve kapasitesini döndürür."""
    sql = "SELECT zone_name, max_capacity, type FROM zones WHERE zone_id = %s AND status = 'ACTIVE'"
    try:
        with get_connection() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
                cur.execute(sql, (zone_id,))
                row = cur.fetchone()
                return dict(row) if row else None
    except psycopg2.Error as e:
        logger.error("Zone bilgisi alınamadı: %s", e)
        return None


def get_all_active_zones() -> list[dict]:
    """Tüm aktif zone'ları döndürür."""
    sql = "SELECT zone_id, zone_name, max_capacity, type FROM zones WHERE status = 'ACTIVE' ORDER BY zone_id"
    try:
        with get_connection() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
                cur.execute(sql)
                return [dict(row) for row in cur.fetchall()]
    except psycopg2.Error as e:
        logger.error("Zone listesi alınamadı: %s", e)
        return []
