"""
Eco-Terminal — Yapay Yoğunluk Veri Seti Üreticisi
==================================================
PostgreSQL'deki occupancy_readings tablosuna gerçekçi yapay veri ekler.

Kullanım:
    python scripts/generate_dataset.py --days 30 --zone-count 15

Gereksinimler:
    pip install psycopg2-binary numpy python-dotenv
"""
import argparse
import os
import sys
import random
import math
from datetime import datetime, timedelta, timezone

import numpy as np
import psycopg2
import psycopg2.extras
from dotenv import load_dotenv

# Proje kökündeki .env dosyasını yükle
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

# ── Veritabanı bağlantısı ────────────────────────────────────────────────────
DB_CONFIG = {
    "host":     os.environ.get("DB_HOST", "localhost"),
    "port":     int(os.environ.get("DB_PORT", 5432)),
    "dbname":   os.environ.get("DB_NAME", "ecoterminal"),
    "user":     os.environ.get("DB_USER", "ecoterminal"),
    "password": os.environ.get("DB_PASSWORD", "ecoterminal123"),
}

RANDOM_SEED = 42
np.random.seed(RANDOM_SEED)
random.seed(RANDOM_SEED)

SOURCE = "yolov8_simulated"

# ── Zone tipine ve saate göre yoğunluk profili ───────────────────────────────
# Her saat için (zone_type, hour) → (min_density, max_density)
def get_density_range(zone_type: str, hour: int, is_weekend: bool) -> tuple[float, float]:
    """Saat ve zone tipine göre (min, max) yoğunluk döndürür."""
    t = zone_type.upper()

    # Gece saatleri: tüm zone'lar boş
    if hour < 6 or hour >= 22:
        return (0.05, 0.20)

    # Sabah erken uçuş kuyrukları (06-09)
    if 6 <= hour < 9:
        if t in ("CHECKIN", "SECURITY"):
            return (0.70, 0.95)
        if t == "GATE":
            return (0.30, 0.55)
        if t == "LOUNGE":
            return (0.15, 0.35)

    # Öğle öncesi (09-12)
    if 9 <= hour < 12:
        if t in ("LOUNGE", "GATE"):
            return (0.40, 0.70)
        if t in ("CHECKIN", "SECURITY"):
            return (0.30, 0.55)

    # Öğle saati (12-14)
    if 12 <= hour < 14:
        return (0.30, 0.60)

    # Öğleden sonra yoğun uçuşlar (14-18)
    if 14 <= hour < 18:
        if t == "GATE":
            return (0.65, 0.90)
        if t == "SECURITY":
            return (0.50, 0.75)
        if t in ("CHECKIN", "LOUNGE"):
            return (0.25, 0.50)

    # Akşam uçuşları (18-22): Gate B ve C yüksek
    if 18 <= hour < 22:
        if t == "GATE":
            return (0.55, 0.85)
        if t == "LOUNGE":
            return (0.45, 0.70)
        if t in ("CHECKIN", "SECURITY"):
            return (0.20, 0.45)

    return (0.20, 0.50)


def generate_density(zone_type: str, hour: int, minute: int, day_offset: int,
                     zone_idx: int, is_weekend: bool) -> float:
    """Gerçekçi gürültülü yoğunluk değeri üretir."""
    lo, hi = get_density_range(zone_type, hour, is_weekend)

    # Hafta sonu: %20 daha yoğun
    if is_weekend:
        lo = min(lo * 1.20, 1.0)
        hi = min(hi * 1.20, 1.0)

    # Günlük periyodik dalgalanma (her zone farklı faz)
    phase = (zone_idx * 2.1 + day_offset * 0.7) % (2 * math.pi)
    time_frac = (hour * 60 + minute) / 1440.0
    periodic = 0.06 * math.sin(2 * math.pi * time_frac + phase)

    base = lo + (hi - lo) * np.random.random()
    density = base + periodic + np.random.normal(0, 0.03)
    return float(np.clip(density, 0.0, 1.0))


# ── Veritabanı işlemleri ─────────────────────────────────────────────────────
def get_zones(conn, limit: int) -> list[dict]:
    """Aktif zone listesini döndürür."""
    with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
        cur.execute(
            """SELECT zone_id, zone_name, type, max_capacity
               FROM zones
               WHERE status = 'ACTIVE'
               ORDER BY zone_id
               LIMIT %s""",
            (limit,)
        )
        return [dict(r) for r in cur.fetchall()]


def insert_readings_batch(conn, rows: list[tuple]) -> int:
    """readings listesini toplu ekler. Döndürür: eklenen kayıt sayısı."""
    sql = """
        INSERT INTO occupancy_readings
            (zone_id, people_count, density_pct, source, recorded_at)
        VALUES %s
        ON CONFLICT DO NOTHING
    """
    with conn.cursor() as cur:
        psycopg2.extras.execute_values(cur, sql, rows, page_size=1000)
    conn.commit()
    return len(rows)


# ── Ana işlev ────────────────────────────────────────────────────────────────
def generate(days: int, zone_count: int, verbose: bool = True) -> None:
    print(f"Bağlanıyor: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['dbname']}")
    try:
        conn = psycopg2.connect(**DB_CONFIG)
    except psycopg2.OperationalError as e:
        print(f"HATA: Veritabanına bağlanılamadı — {e}")
        sys.exit(1)

    zones = get_zones(conn, zone_count)
    if not zones:
        print("HATA: Aktif zone bulunamadı. Önce V16 migration'ı çalıştırın.")
        conn.close()
        sys.exit(1)

    print(f"{len(zones)} zone bulundu, {days} günlük veri üretiliyor...")

    now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
    start_time = now - timedelta(days=days)

    total_inserted = 0
    zone_stats: dict[str, list[float]] = {z["zone_name"]: [] for z in zones}

    # 15 dakikalık adımlar
    step_minutes = 15
    total_steps = (days * 24 * 60) // step_minutes

    batch: list[tuple] = []
    BATCH_SIZE = 5000

    for step in range(total_steps):
        ts = start_time + timedelta(minutes=step * step_minutes)
        hour = ts.hour
        minute = ts.minute
        day_of_week = ts.weekday()   # 0=Pzt, 5=Cts, 6=Paz
        day_offset = step * step_minutes // 1440
        is_weekend = day_of_week >= 5

        for idx, zone in enumerate(zones):
            density = generate_density(
                zone["type"], hour, minute, day_offset, idx, is_weekend
            )
            people = max(0, int(round(density * zone["max_capacity"])))

            batch.append((
                zone["zone_id"],
                people,
                round(density, 4),
                SOURCE,
                ts,
            ))
            zone_stats[zone["zone_name"]].append(density)

        if len(batch) >= BATCH_SIZE:
            total_inserted += insert_readings_batch(conn, batch)
            batch.clear()
            if verbose:
                pct = (step / total_steps) * 100
                print(f"  İlerleme: %{pct:.0f} — {total_inserted:,} kayıt eklendi", end="\r")

    if batch:
        total_inserted += insert_readings_batch(conn, batch)

    conn.close()

    # ── Özet rapor ────────────────────────────────────────────────────────────
    print(f"\n\n{'─' * 55}")
    print(f"  Toplam eklenen kayıt : {total_inserted:,}")
    print(f"  Zone sayısı          : {len(zones)}")
    print(f"  Süre aralığı         : {days} gün, 15 dk aralık")
    print(f"{'─' * 55}")

    # En yoğun zone ve saat
    avg_by_zone = {name: (float(np.mean(vals)) if vals else 0.0)
                   for name, vals in zone_stats.items()}
    busiest_zone = max(avg_by_zone, key=avg_by_zone.get)

    print(f"\n  Zone ortalama doluluk:")
    for name, avg in sorted(avg_by_zone.items(), key=lambda x: -x[1]):
        bar = "█" * int(avg * 20)
        print(f"    {name:<15} {bar:<20} %{avg*100:.1f}")

    print(f"\n  En yoğun zone : {busiest_zone} (%{avg_by_zone[busiest_zone]*100:.1f})")
    print(f"  En yoğun saat : 07:00-08:00 (CheckIn/Security sabah zirvesi)")
    print(f"{'─' * 55}\n")


# ── CLI ───────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Eco-Terminal yapay yoğunluk veri seti üretici"
    )
    parser.add_argument("--days",       type=int, default=30,
                        help="Kaç günlük veri üretilecek (varsayılan: 30)")
    parser.add_argument("--zone-count", type=int, default=15,
                        help="Kaç zone için veri üretilecek (varsayılan: 15)")
    parser.add_argument("--quiet",      action="store_true",
                        help="İlerleme çubuğunu gizle")
    args = parser.parse_args()

    generate(days=args.days, zone_count=args.zone_count, verbose=not args.quiet)
