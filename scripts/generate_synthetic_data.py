#!/usr/bin/env python3
"""
Eco-Terminal — Gerçekçi Havalimanı Yoğunluk Veri Üretici
=========================================================
Zone tipine göre farklı profiller, uçuş bazlı gate spike'ları,
mevsimsel + günlük varyasyon ve korelasyon katmanları içerir.

Kullanım:
    python scripts/generate_synthetic_data.py --days 30 --interval-minutes 15
    python scripts/generate_synthetic_data.py --days 7 --db-url postgresql://user:pass@localhost/db
"""

import argparse
import math
import os
import random
import sys
from datetime import datetime, timedelta, timezone

import numpy as np

try:
    import psycopg2
    from psycopg2.extras import execute_values
except ImportError:
    sys.exit("psycopg2-binary kurulu değil: pip install psycopg2-binary")

try:
    from tqdm import tqdm
except ImportError:
    sys.exit("tqdm kurulu değil: pip install tqdm")

# ── Zone Tanımları ─────────────────────────────────────────────────────────────

ZONES = [
    {"name": "Gate A1",    "zone_type": "GATE",     "capacity": 150, "concourse": "A"},
    {"name": "Gate A2",    "zone_type": "GATE",     "capacity": 150, "concourse": "A"},
    {"name": "Gate A3",    "zone_type": "GATE",     "capacity": 120, "concourse": "A"},
    {"name": "Gate B1",    "zone_type": "GATE",     "capacity": 180, "concourse": "B"},
    {"name": "Gate B2",    "zone_type": "GATE",     "capacity": 180, "concourse": "B"},
    {"name": "Gate C1",    "zone_type": "GATE",     "capacity": 200, "concourse": "C"},
    {"name": "Gate C2",    "zone_type": "GATE",     "capacity": 200, "concourse": "C"},
    {"name": "Gate C3",    "zone_type": "GATE",     "capacity": 160, "concourse": "C"},
    {"name": "Security-1", "zone_type": "SECURITY", "capacity":  80},
    {"name": "Security-2", "zone_type": "SECURITY", "capacity":  80},
    {"name": "Lounge-1",   "zone_type": "LOUNGE",   "capacity": 300},
    {"name": "Lounge-2",   "zone_type": "LOUNGE",   "capacity": 250},
    {"name": "CheckIn-1",  "zone_type": "CHECKIN",  "capacity": 100},
    {"name": "CheckIn-2",  "zone_type": "CHECKIN",  "capacity": 100},
    {"name": "CheckIn-3",  "zone_type": "CHECKIN",  "capacity": 100},
]

# ── Rastgele tohum (tekrarlanabilirlik) ────────────────────────────────────────
RNG = np.random.default_rng(42)

# ── Profil Fonksiyonları ───────────────────────────────────────────────────────

def checkin_profile(hour: float) -> float:
    """Sabah early + iki peak (sabah rush + akşam rush)."""
    if 4 <= hour < 7:
        return 0.20 + (hour - 4) * (0.50 / 3)  # ramp 0.20→0.70
    elif 7 <= hour < 10:
        return 0.75 + RNG.uniform(-0.05, 0.20)   # sabah peak
    elif 10 <= hour < 13:
        return 0.45 + RNG.uniform(-0.05, 0.15)   # orta
    elif 13 <= hour < 16:
        return 0.65 + RNG.uniform(-0.05, 0.15)   # ikinci peak
    elif 16 <= hour < 19:
        return 0.75 + RNG.uniform(-0.05, 0.15)   # akşam peak
    elif 19 <= hour < 22:
        return 0.35 + RNG.uniform(-0.05, 0.15)   # düşüş
    else:
        return 0.07 + RNG.uniform(0, 0.08)        # gece

def security_profile(hour: float) -> float:
    """CheckIn'den ~40 dk gecikmeli, daha sert peak'ler."""
    delayed_hour = hour - 0.67  # 40 dakika geri
    base = checkin_profile(delayed_hour % 24)
    # Peak saatlerde ek kalabalık (kuyruk birikimi)
    if 7.5 <= hour < 10.5 or 16.5 <= hour < 19.5:
        base = min(0.97, base * 1.25)
    if 22 <= hour or hour < 4:
        return 0.04 + RNG.uniform(0, 0.04)
    return base

def lounge_profile(hour: float) -> float:
    """Yavaş dolum/boşalma — sürekli yüksek orta bölge."""
    if 2 <= hour < 6:
        return 0.06 + RNG.uniform(0, 0.04)
    elif 6 <= hour < 10:
        t = (hour - 6) / 4
        return 0.15 + t * 0.40 + RNG.uniform(-0.03, 0.05)  # 0.15→0.55
    elif 10 <= hour < 18:
        return 0.58 + RNG.uniform(-0.08, 0.17)   # sabit yüksek
    elif 18 <= hour < 22:
        return 0.65 + RNG.uniform(-0.05, 0.15)   # akşam
    elif 22 <= hour < 24:
        t = (hour - 22) / 2
        return 0.30 - t * 0.20 + RNG.uniform(-0.05, 0.05)  # düşüş
    else:
        return 0.08 + RNG.uniform(0, 0.04)

def generate_gate_flights(zone_name: str, capacity: int) -> list:
    """Her gate için günlük 4-8 uçuş çizelgesi üretir (deterministic per zone)."""
    seed = sum(ord(c) for c in zone_name)
    local_rng = np.random.default_rng(seed)
    num_flights = local_rng.integers(4, 9)  # 4-8 uçuş
    # Uçuşlar 06:00–23:00 arasında dağıtılır, minimum 90 dk aralıklı
    departures = sorted(local_rng.uniform(6.0, 23.0, num_flights).tolist())
    # Minimum aralık kontrolü
    filtered = [departures[0]]
    for t in departures[1:]:
        if t - filtered[-1] >= 1.5:  # 90 dakika
            filtered.append(t)
    return filtered

def gate_profile(hour: float, flights: list, capacity: int) -> float:
    """Uçuş bazlı spike modeli."""
    density = 0.07  # baseline (boş bekleme)

    for dep_hour in flights:
        diff = hour - dep_hour

        if -1.5 <= diff < -0.75:        # 90-45 dk önce: erken birikme
            t = (diff + 1.5) / 0.75
            density = max(density, 0.15 + t * 0.45)
        elif -0.75 <= diff < -0.25:     # 45-15 dk önce: boarding
            t = (diff + 0.75) / 0.50
            density = max(density, 0.60 + t * 0.35)
        elif -0.25 <= diff < 0.0:       # son 15 dk: dolmuş durumda
            density = max(density, 0.88 + RNG.uniform(-0.05, 0.07))
        elif 0.0 <= diff < 0.25:        # kalkış sonrası: hızlı boşalma
            t = diff / 0.25
            density = max(density, 0.85 - t * 0.75)
        elif 0.25 <= diff < 0.5:        # tam boşalmış
            density = max(density, 0.06 + RNG.uniform(0, 0.05))

    return min(1.0, max(0.0, density))

# ── Varyasyon Katmanları ───────────────────────────────────────────────────────

def weekday_multiplier(dt: datetime) -> float:
    """Pazartesi/Cuma +15%, Cumartesi -10%, Pazar -5%."""
    wd = dt.weekday()  # 0=Mon ... 6=Sun
    if wd in (0, 4):  return 1.15  # Pazartesi, Cuma
    if wd == 5:        return 0.90  # Cumartesi
    if wd == 6:        return 0.95  # Pazar
    return 1.00

def seasonal_multiplier(dt: datetime) -> float:
    """Yaz (Haziran-Ağustos) %10 daha yoğun."""
    if dt.month in (6, 7, 8): return 1.10
    return 1.00

def gaussian_noise() -> float:
    """μ=0, σ=0.03 gürültü."""
    return float(RNG.normal(0, 0.03))

def concourse_correlation(concourse_densities: dict, my_concourse: str,
                           my_density: float) -> float:
    """Aynı concourse'taki gate'ler %30 koreleli."""
    if my_concourse is None:
        return my_density
    peers = [d for k, d in concourse_densities.items() if k != my_concourse]
    if not peers:
        return my_density
    peer_avg = sum(peers) / len(peers)
    return my_density * 0.70 + peer_avg * 0.30

# ── Yoğunluk Hesaplama ────────────────────────────────────────────────────────

def compute_density(zone: dict, dt: datetime,
                    gate_flights: dict,
                    event_zones: set,
                    concourse_densities: dict) -> float:
    """Zone için belirli zaman diliminde doluluk hesaplar (0.0–1.0)."""
    hour = dt.hour + dt.minute / 60.0
    ztype = zone["zone_type"]

    if ztype == "CHECKIN":
        base = checkin_profile(hour)
    elif ztype == "SECURITY":
        base = security_profile(hour)
    elif ztype == "LOUNGE":
        base = lounge_profile(hour)
    elif ztype == "GATE":
        flights = gate_flights.get(zone["name"], [])
        base = gate_profile(hour, flights, zone["capacity"])
    else:
        base = 0.20 + RNG.uniform(0, 0.10)

    # Varyasyon katmanları
    base *= weekday_multiplier(dt)
    base *= seasonal_multiplier(dt)
    base += gaussian_noise()

    # Havalimanı olayı spike (+20-30%)
    if zone["name"] in event_zones:
        base += RNG.uniform(0.20, 0.30)

    # Concourse korelasyonu (gate'ler için)
    if ztype == "GATE" and zone.get("concourse"):
        base = concourse_correlation(
            concourse_densities, zone["concourse"], base
        )

    return float(np.clip(base, 0.01, 1.0))

# ── Olay Üreticisi ─────────────────────────────────────────────────────────────

def generate_events(start_dt: datetime, days: int) -> dict:
    """Rastgele gün/saat → etkilenen zone'lar (uçuş iptali/VIP etkinlikleri)."""
    events = {}  # key: date string, value: {hour_start, hour_end, zones}
    event_rng = np.random.default_rng(99)
    for day_offset in range(days):
        if event_rng.random() < 0.15:  # %15 olasılıkla olay
            day = start_dt + timedelta(days=day_offset)
            h_start = event_rng.integers(7, 20)
            h_end = h_start + event_rng.integers(2, 4)
            n_zones = event_rng.integers(1, 4)
            affected = event_rng.choice(
                [z["name"] for z in ZONES], size=n_zones, replace=False
            ).tolist()
            events[day.date()] = {
                "hour_start": int(h_start),
                "hour_end": int(h_end),
                "zones": set(affected)
            }
    return events

# ── Veritabanı ─────────────────────────────────────────────────────────────────

def get_zone_ids(conn) -> dict:
    """zone_name → zone_id eşlemesi."""
    with conn.cursor() as cur:
        cur.execute("SELECT zone_id, zone_name FROM zones WHERE status = 'ACTIVE'")
        return {row[1]: row[0] for row in cur.fetchall()}

def insert_batch(conn, rows: list):
    """Toplu ekleme — ON CONFLICT ile idempotent."""
    sql = """
        INSERT INTO occupancy_readings (zone_id, people_count, density_pct, source, recorded_at)
        VALUES %s
        ON CONFLICT DO NOTHING
    """
    with conn.cursor() as cur:
        execute_values(cur, sql, rows, template="(%s, %s, %s, %s, %s)", page_size=5000)
    conn.commit()

# ── Ana Fonksiyon ──────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Eco-Terminal — Gerçekçi Havalimanı Yoğunluk Veri Üretici"
    )
    parser.add_argument("--days",              type=int,   default=30,
                        help="Kaç günlük veri üretilsin (varsayılan: 30)")
    parser.add_argument("--interval-minutes",  type=int,   default=15,
                        help="Okumalar arası dakika (varsayılan: 15)")
    parser.add_argument("--db-url",            type=str,   default=None,
                        help="PostgreSQL bağlantı URL'si (varsayılan: .env)")
    parser.add_argument("--quiet",             action="store_true",
                        help="Özet raporu bastır")
    args = parser.parse_args()

    # ── Bağlantı ──────────────────────────────────────────────────────────────
    db_url = args.db_url or os.environ.get("DATABASE_URL")
    if not db_url:
        host     = os.environ.get("DB_HOST", "localhost")
        port     = os.environ.get("DB_PORT", "5432")
        dbname   = os.environ.get("DB_NAME", os.environ.get("POSTGRES_DB", "ecoterminal"))
        user     = os.environ.get("DB_USER", os.environ.get("POSTGRES_USER", "ecoterminal"))
        password = os.environ.get("DB_PASSWORD", os.environ.get("POSTGRES_PASSWORD", "ecoterminal123"))
        db_url   = f"postgresql://{user}:{password}@{host}:{port}/{dbname}"

    try:
        conn = psycopg2.connect(db_url)
    except Exception as e:
        sys.exit(f"Veritabanına bağlanılamadı: {e}")

    zone_ids = get_zone_ids(conn)
    if not zone_ids:
        print("Uyarı: zones tablosunda aktif zone bulunamadı. V16 migration çalıştırıldı mı?")

    # ── Zaman aralığı ──────────────────────────────────────────────────────────
    end_dt   = datetime.now(timezone.utc).replace(second=0, microsecond=0)
    start_dt = end_dt - timedelta(days=args.days)

    # ── Gate uçuş çizelgeleri ──────────────────────────────────────────────────
    gate_flights = {}
    for zone in ZONES:
        if zone["zone_type"] == "GATE":
            gate_flights[zone["name"]] = generate_gate_flights(zone["name"], zone["capacity"])

    # ── Olaylar ────────────────────────────────────────────────────────────────
    events = generate_events(start_dt, args.days)

    # ── Veri üretimi ──────────────────────────────────────────────────────────
    interval  = timedelta(minutes=args.interval_minutes)
    total_pts = args.days * 24 * 60 // args.interval_minutes
    batch     = []
    stats     = {z["name"]: [] for z in ZONES}

    print(f"\nEco-Terminal Sentetik Veri Üretici")
    print(f"  Süre: {args.days} gün  |  Aralık: {args.interval_minutes} dk")
    print(f"  Başlangıç: {start_dt.strftime('%Y-%m-%d %H:%M')} UTC")
    print(f"  Beklenen kayıt: ~{total_pts * len(ZONES):,}\n")

    current = start_dt
    with tqdm(total=total_pts, unit="slot", desc="Üretiliyor") as pbar:
        while current < end_dt:
            day_event = events.get(current.date(), None)
            event_zones = set()
            if day_event and day_event["hour_start"] <= current.hour < day_event["hour_end"]:
                event_zones = day_event["zones"]

            # Concourse korelasyonu için önce ham değerleri hesapla
            concourse_densities: dict = {}  # concourse → density

            for zone in ZONES:
                if zone["zone_type"] == "GATE" and zone.get("concourse"):
                    d = compute_density(zone, current, gate_flights, event_zones, {})
                    concourse_densities[zone["concourse"]] = d

            # Şimdi tüm zone'ları hesapla (concourse korelasyonu dahil)
            for zone in ZONES:
                name = zone["name"]
                if name not in zone_ids:
                    continue

                density = compute_density(zone, current, gate_flights, event_zones, concourse_densities)
                people  = max(0, int(density * zone["capacity"]))

                batch.append((
                    zone_ids[name],  # zone_id
                    people,
                    round(density, 4),
                    "yolov8_synthetic",
                    current
                ))
                stats[name].append(density)

            if len(batch) >= 10_000:
                insert_batch(conn, batch)
                batch.clear()

            current += interval
            pbar.update(1)

    # Son batch
    if batch:
        insert_batch(conn, batch)

    conn.close()

    # ── Özet Rapor ─────────────────────────────────────────────────────────────
    if not args.quiet:
        total_written = sum(len(v) for v in stats.values())
        sep = "-" * 60
        print(f"\n{sep}")
        print(f"  Toplam kayit eklendi : {total_written:,}")
        print(sep)
        print(f"  {'Zone':<14} {'Ort. Doluluk':>14} {'Maks':>6} {'Min':>6}")
        print(f"  {'-'*14} {'-'*14} {'-'*6} {'-'*6}")

        zone_avgs = []
        for zone in ZONES:
            name = zone["name"]
            ds   = stats.get(name, [])
            if not ds:
                continue
            avg_d = sum(ds) / len(ds)
            zone_avgs.append((name, avg_d, max(ds), min(ds)))
            bar_len = int(avg_d * 20)
            bar = "#" * bar_len + "." * (20 - bar_len)
            print(f"  {name:<14} {bar} {avg_d:.2f}  max={max(ds):.2f}  min={min(ds):.2f}")

        # En yoğun 5 zone-saat (anlık)
        print(f"\n  En yogun 5 zone: " +
              ", ".join(z[0] for z in sorted(zone_avgs, key=lambda x: x[1], reverse=True)[:5]))
        print(f"  En bos 5 zone: " +
              ", ".join(z[0] for z in sorted(zone_avgs, key=lambda x: x[1])[:5]))
        print(f"{sep}\n")

if __name__ == "__main__":
    main()
