#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_synthetic_data.py
==========================
Eco-Terminal ML pipeline için 10 yıllık (2016-2026) sentetik havalimanı
yoğunluk ve enerji veri seti üreticisi.

Üretilen veri ileride XGBoost + Analog kNN modellerinin eğitiminde kullanılacak.

Çıktı : ml-pipeline/data/synthetic_terminal_data.parquet
Boyut : ~5.4 milyon satır · 15 zone · 15 dakikalık aralık
Süre  : yaklaşık 5-10 dakika

Kullanım:
    pip install -r requirements.txt
    python generate_synthetic_data.py
"""

import sys
import time
import numpy as np
import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
from pathlib import Path

# ── Tekrar üretilebilirlik — sabit seed ────────────────────────────────────────
RNG = np.random.default_rng(42)

# ── Dizin ve dosya yolları ─────────────────────────────────────────────────────
ROOT_DIR = Path(__file__).parent
DATA_DIR = ROOT_DIR / 'data'
DATA_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT   = DATA_DIR / 'synthetic_terminal_data.parquet'

# ── Tarih aralığı ──────────────────────────────────────────────────────────────
START_DATE = pd.Timestamp('2016-01-01')
END_DATE   = pd.Timestamp('2026-05-29 23:45:00')   # bugün (dahil)

# =============================================================================
# ZONE TANIMLARI
# Eco-Terminal DB'sindeki zones tablosuyla uyumlu değerler.
# zone_id'ler DB'deki sırayla eşleştirilmiştir.
# =============================================================================
ZONES = [
    # id   name                  type        capacity
    {'id':  1, 'name': 'Gate A1',        'type': 'GATE',     'capacity': 200},
    {'id':  2, 'name': 'Gate A2',        'type': 'GATE',     'capacity': 180},
    {'id':  3, 'name': 'Gate A3',        'type': 'GATE',     'capacity': 220},
    {'id':  4, 'name': 'Gate B1',        'type': 'GATE',     'capacity': 250},
    {'id':  5, 'name': 'Gate B2',        'type': 'GATE',     'capacity': 200},
    {'id':  6, 'name': 'Gate B3',        'type': 'GATE',     'capacity': 180},
    {'id':  7, 'name': 'Gate C1',        'type': 'GATE',     'capacity': 300},
    {'id':  8, 'name': 'Gate C2',        'type': 'GATE',     'capacity': 280},
    {'id':  9, 'name': 'Gate C3',        'type': 'GATE',     'capacity': 250},
    {'id': 10, 'name': 'Security-1',     'type': 'SECURITY', 'capacity': 500},
    {'id': 11, 'name': 'Security-2 VIP', 'type': 'SECURITY', 'capacity': 150},
    {'id': 12, 'name': 'CheckIn-1',      'type': 'CHECKIN',  'capacity': 400},
    {'id': 13, 'name': 'CheckIn-2',      'type': 'CHECKIN',  'capacity': 400},
    {'id': 14, 'name': 'Lounge-1',       'type': 'LOUNGE',   'capacity': 200},
    {'id': 15, 'name': 'Lounge-2',       'type': 'LOUNGE',   'capacity': 200},
]

# =============================================================================
# SAATLİK YOĞ UNLUK PROFİLLERİ (0–23)
# Her zone tipi için gün içindeki temel doluluk oranı (0.0–1.0).
#
# GATE     : Sabah + öğleden sonra + akşam pik (uçuş saatlerine paralel)
# SECURITY : Gate ile benzer ama sabah piki daha keskin (2 saat erkene alınır)
# CHECKIN  : En keskin sabah piği (check-in kapanma saatleri kritik)
# LOUNGE   : Gün boyu görece sabit, küçük dalgalanma
# =============================================================================
HOURLY_PROFILES = {
    'GATE': np.array([
        0.04, 0.03, 0.03, 0.04, 0.12, 0.55,  # 00–05: gece → erken sabah artışı
        0.75, 0.80, 0.72, 0.60, 0.55, 0.50,  # 06–11: sabah piği, öğleye iniş
        0.52, 0.60, 0.72, 0.80, 0.82, 0.80,  # 12–17: öğleden sonra + akşam piği
        0.75, 0.70, 0.60, 0.45, 0.25, 0.08,  # 18–23: akşam sonrası iniş
    ]),
    'SECURITY': np.array([
        0.03, 0.02, 0.02, 0.03, 0.10, 0.50,  # 00–05
        0.78, 0.85, 0.80, 0.65, 0.52, 0.48,  # 06–11: sabah piki GATE'den keskin
        0.50, 0.55, 0.68, 0.78, 0.85, 0.82,  # 12–17
        0.75, 0.65, 0.52, 0.40, 0.20, 0.06,  # 18–23
    ]),
    'CHECKIN': np.array([
        0.02, 0.02, 0.02, 0.03, 0.15, 0.60,  # 00–05: en erken açılış (04:00'te çalışmaya başlar)
        0.82, 0.88, 0.80, 0.60, 0.48, 0.45,  # 06–11: en yüksek sabah piği
        0.48, 0.55, 0.70, 0.82, 0.85, 0.78,  # 12–17: ikinci pik
        0.65, 0.55, 0.42, 0.30, 0.15, 0.05,  # 18–23
    ]),
    'LOUNGE': np.array([
        0.05, 0.04, 0.03, 0.04, 0.08, 0.30,  # 00–05: lounge geceleri neredeyse boş
        0.52, 0.60, 0.62, 0.58, 0.55, 0.52,  # 06–11: sabah yolcuları
        0.55, 0.58, 0.62, 0.65, 0.65, 0.62,  # 12–17: öğleden sonra sabit
        0.58, 0.55, 0.48, 0.38, 0.20, 0.08,  # 18–23: akşam sonrası iniş
    ]),
}

# =============================================================================
# HAFTALıK ÇARPANLAR  (0=Pazartesi … 6=Pazar)
# İş seyahatleri Pazartesi+Cuma'yı; tatil seyahatleri Cumartesi+Pazar'ı şişirir
# =============================================================================
WEEKLY_MUL = np.array([
    1.05,   # Pazartesi
    1.00,   # Salı
    1.03,   # Çarşamba
    1.00,   # Perşembe
    1.20,   # Cuma — iş + tatil seyahatleri üst üste gelir, en yoğun gün
    1.12,   # Cumartesi
    1.15,   # Pazar — dönüş seyahatleri
])

# =============================================================================
# YILLIK BÜYÜME ÇARPANLARI
# Gerçek havalimanı yolcu verilerine dayalı kaba tahmin.
# 2016 baz yıl (1.000); COVID etkisi 2020-2021'de modellendi.
# =============================================================================
ANNUAL_GROWTH = {
    2016: 1.000,
    2017: 1.035,
    2018: 1.071,
    2019: 1.108,
    2020: 0.280,   # COVID — uçuşların %70+ durması
    2021: 0.520,   # Kısmi toparlanma
    2022: 0.850,   # Hızlı toparlanma dönemi
    2023: 1.000,   # COVID öncesi seviyeye dönüş
    2024: 1.038,
    2025: 1.077,
    2026: 1.110,   # Kısmi yıl — büyüme devam ediyor
}

# =============================================================================
# TATİL DÖNEMLERİ
# (yıl, başlangıç_ay, başlangıç_gün, bitiş_ay, bitiş_gün, yoğunluk_çarpanı)
#
# Ramazan + Kurban Bayramı tarihleri her yıl ~11 gün kaydığı için
# 2016-2026 arası yaklaşık tarihler kullanıldı.
# =============================================================================
HOLIDAY_PERIODS = [
    # ── Ramazan Bayramı ─────────────────────────────────────────────────────
    (2016, 7,  5, 7,  8, 1.35),
    (2017, 6, 25, 6, 28, 1.35),
    (2018, 6, 14, 6, 17, 1.35),
    (2019, 6,  4, 6,  7, 1.35),
    (2020, 5, 23, 5, 26, 1.10),   # COVID — düşük çarpan
    (2021, 5, 13, 5, 16, 1.20),
    (2022, 5,  2, 5,  5, 1.30),
    (2023, 4, 21, 4, 24, 1.35),
    (2024, 4, 10, 4, 13, 1.35),
    (2025, 3, 30, 4,  2, 1.35),
    (2026, 3, 20, 3, 23, 1.35),
    # ── Kurban Bayramı ──────────────────────────────────────────────────────
    (2016, 9, 12, 9, 16, 1.40),
    (2017, 9,  1, 9,  5, 1.40),
    (2018, 8, 21, 8, 25, 1.40),
    (2019, 8, 11, 8, 15, 1.40),
    (2020, 7, 31, 8,  4, 1.15),
    (2021, 7, 20, 7, 24, 1.25),
    (2022, 7,  9, 7, 13, 1.35),
    (2023, 6, 28, 7,  2, 1.40),
    (2024, 6, 17, 6, 21, 1.40),
    (2025, 6,  6, 6, 10, 1.40),
    # 2026 Kurban Bayramı (veri Mayıs'ta bitiyor, bu dönem dışında)
]

# =============================================================================
# ENERJİ PARAMETRELERİ
# energy_kwh = baz + kişi_başı_değişken  (15 dakikalık periyot için)
# =============================================================================
ENERGY_PARAMS = {
    'GATE':     {'base_h': 10.0, 'per_person': 0.0050},  # base_h = saatlik baz kWh
    'SECURITY': {'base_h': 15.0, 'per_person': 0.0080},  # X-ray, tarayıcılar
    'CHECKIN':  {'base_h': 12.0, 'per_person': 0.0060},  # check-in konveyörleri
    'LOUNGE':   {'base_h':  8.0, 'per_person': 0.0040},  # premium klima/ikram
}


# =============================================================================
# TAT İL HARİTASI OLUŞTURUCU
# =============================================================================

def build_holiday_map() -> dict:
    """
    Tüm tatil günlerini {date: çarpan} sözlüğü olarak döner.
    Aynı güne birden fazla tatil denk gelirse büyük çarpan kazanır.
    """
    hmap: dict = {}

    # Sabit tatil dönemleri (Bayramlar)
    for (yr, sm, sd, em, ed, mult) in HOLIDAY_PERIODS:
        current = pd.Timestamp(f'{yr}-{sm:02d}-{sd:02d}')
        end_d   = pd.Timestamp(f'{yr}-{em:02d}-{ed:02d}')
        while current <= end_d:
            key = current.date()
            hmap[key] = max(hmap.get(key, 1.0), mult)
            current += pd.Timedelta(days=1)

    # Yılbaşı tatili — her yıl (24 Aralık – 7 Ocak)
    for yr in range(2016, 2027):
        # Aralık 24–31
        for day in range(24, 32):
            d = pd.Timestamp(f'{yr}-12-{day:02d}')
            hmap[d.date()] = max(hmap.get(d.date(), 1.0), 1.30)
        # Ocak 1–7
        for day in range(1, 8):
            try:
                d = pd.Timestamp(f'{yr}-01-{day:02d}')
                hmap[d.date()] = max(hmap.get(d.date(), 1.0), 1.28)
            except Exception:
                pass

    return hmap


# =============================================================================
# YILLIK VERİ ÜRETİCİ  (vektörel, numpy tabanlı)
# =============================================================================

def generate_year(year: int, holiday_map: dict) -> pd.DataFrame:
    """
    Bir yıllık tüm zone verilerini üretir.
    Tüm hesaplamalar numpy vektörleri üzerinde yapılır — döngü yok, hızlı.

    Parametreler
    ------------
    year        : 2016–2026 arası tam yıl
    holiday_map : {date: çarpan} sözlüğü (build_holiday_map() çıktısı)

    Döndürür
    --------
    pd.DataFrame – tüm zone'lar için birleştirilmiş yıllık kayıtlar
    """
    # ── Zaman damgaları ──────────────────────────────────────────────────────
    y_start = pd.Timestamp(f'{year}-01-01')
    # 2026'da verimiz 29 Mayıs'ta bitiyor; diğer yıllar tam yıl
    y_end = (pd.Timestamp(f'{year + 1}-01-01')
             if year < 2026
             else END_DATE + pd.Timedelta(minutes=15))

    timestamps = pd.date_range(y_start, y_end, freq='15min', inclusive='left')
    n = len(timestamps)

    # ── Temel zaman bileşenleri (vektörel) ──────────────────────────────────
    hours  = timestamps.hour.values           # (n,)
    dows   = timestamps.dayofweek.values      # (n,)  0=Pazartesi
    doys   = timestamps.dayofyear.values      # (n,)  yıl içi gün sırası
    months = timestamps.month.values          # (n,)
    dates  = timestamps.date                  # (n,)  python date nesneleri

    # ── Mevsimsel çarpan: sinüs dalgası ─────────────────────────────────────
    # Maksimum: ~172. gün (21 Haziran) | Minimum: ~355. gün (21 Aralık)
    # Aralık: 0.80 – 1.20  (yaz %20 daha yoğun, kış %20 daha sakin)
    seasonal = 1.00 + 0.20 * np.sin((doys - 80) * 2.0 * np.pi / 365.25)

    # Kış ortasına ek düşüş (Ocak–Şubat)
    winter_dip = np.where((months == 1) | (months == 2), 0.92, 1.0)
    seasonal   = seasonal * winter_dip

    # ── Tatil çarpanı ────────────────────────────────────────────────────────
    # date → çarpan; tatil değilse 1.0
    holiday_mul = np.array([holiday_map.get(d, 1.0) for d in dates], dtype=np.float32)

    # ── Yıllık büyüme ────────────────────────────────────────────────────────
    growth = float(ANNUAL_GROWTH[year])

    # ── Haftalık çarpan ──────────────────────────────────────────────────────
    weekly_mul = WEEKLY_MUL[dows]             # (n,)

    # ── Aydınlatma seviyesi (saat bazlı, tüm zone'lar için ortak) ────────────
    # Gündüz 800 lux, gece 300 lux; sinüs geçişi
    lux_raw      = 550 + 250 * np.sin(np.pi * np.clip(hours - 5, 0, 17) / 17)
    lighting_lux = np.where((hours < 5) | (hours > 22), 300, lux_raw).astype(np.int32)

    # ── Zone bazlı hesaplamalar ──────────────────────────────────────────────
    zone_dfs = []

    for zone in ZONES:
        ztype    = zone['type']
        capacity = zone['capacity']
        ep       = ENERGY_PARAMS[ztype]

        # Saatlik temel profil (her timestamp için bak)
        base_density = HOURLY_PROFILES[ztype][hours].astype(np.float64)  # (n,)

        # Tüm çarpanları uygula
        density = base_density * weekly_mul * seasonal * holiday_mul * growth

        # ±%7 normal dağılım gürültüsü (doğal varyans, ML için gerekli)
        noise   = RNG.normal(0.0, 0.07, size=n)
        density = np.clip(density + noise, 0.01, 1.0)

        # Kişi sayısı → kapasiteyle çarp, tam sayıya yuvarla
        people = np.clip(
            np.round(density * capacity).astype(np.int32), 0, capacity
        )
        # density_pct'yi people'dan yeniden hesapla (tutarlılık)
        density_pct = np.round(people.astype(np.float64) / capacity, 4)

        # ── Enerji (kWh / 15 dakika) ─────────────────────────────────────────
        # Base: saatlik baz kWh / 4 periyot
        # Değişken: kişi başı katsayı × kişi sayısı
        # Gündüz faktörü: gece daha az enerji (aydınlatma, klima azalır)
        base_energy = ep['base_h'] / 4.0
        var_energy  = people * ep['per_person'] / 4.0
        day_factor  = 0.70 + 0.30 * np.clip(
            np.sin(np.pi * np.clip(hours - 6, 0, 14) / 14), 0, 1
        )
        e_noise    = RNG.normal(0.0, 0.04, size=n)
        energy_kwh = np.round(
            (base_energy + var_energy) * day_factor * (1.0 + e_noise), 3
        )
        # Minimum enerji: gece bile baz yükün yarısı çekilir
        energy_kwh = np.clip(energy_kwh, base_energy * 0.50, None)

        # ── Sıcaklık (°C) ─────────────────────────────────────────────────────
        # Klima kontrolü: 22°C baz ± mevsimsel küçük dalgalanma + gürültü
        # Yaz biraz daha sıcak, kış biraz daha soğuk (klima dengesi)
        temp_seasonal = 1.5 * np.sin((doys - 180) * 2.0 * np.pi / 365.25)
        t_noise       = RNG.normal(0.0, 0.4, size=n)
        temp_c        = np.round(
            np.clip(22.0 + temp_seasonal + t_noise, 18.0, 28.0), 1
        )
        if ztype == 'LOUNGE':   # Lounge biraz daha serin (prestij klima)
            temp_c = np.clip(temp_c - 1.0, 18.0, 26.0)

        # ── DataFrame oluştur ────────────────────────────────────────────────
        zone_dfs.append(pd.DataFrame({
            'timestamp':     timestamps,
            'zone_id':       np.int16(zone['id']),
            'zone_name':     zone['name'],        # kategorik (bellek optimizasyonu)
            'zone_type':     ztype,
            'capacity':      np.int16(capacity),
            'people_count':  people,
            'density_pct':   density_pct,
            'energy_kwh':    energy_kwh,
            'temp_c':        temp_c,
            'lighting_lux':  lighting_lux,
        }))

    # Tüm zone'ları birleştir
    year_df = pd.concat(zone_dfs, ignore_index=True)

    # Kategorik sütunlar — parquet'te daha az yer kaplar
    year_df['zone_name'] = year_df['zone_name'].astype('category')
    year_df['zone_type'] = year_df['zone_type'].astype('category')

    return year_df


# =============================================================================
# HIZLI DOĞRULAMA (üretim sonrası özet kontrol)
# =============================================================================

def quick_validate(output_path: Path) -> None:
    """Üretilen veriyi temel kontroller ile doğrular."""
    print('\n' + '=' * 62)
    print('HIZLI DOĞRULAMA')
    print('=' * 62)

    df = pd.read_parquet(output_path)
    df['timestamp'] = pd.to_datetime(df['timestamp'])

    # Kayıt sayısı
    n = len(df)
    print(f'Toplam kayıt         : {n:>12,}')

    # NaN kontrolü
    nan_cnt = df.isnull().sum().sum()
    ok = '✓' if nan_cnt == 0 else '✗ HATA'
    print(f'NaN değer            : {nan_cnt:>12,}  {ok}')

    # Zone eşitliği
    zc = df['zone_id'].value_counts()
    delta = int(zc.max() - zc.min())
    ok = '✓' if delta < 500 else f'~ ({delta} fark, 2026 kesiminden beklenen)'
    print(f'Zone başına kayıt    : min={zc.min():,}  max={zc.max():,}  {ok}')

    # Saatlik pik kontrolü
    df['hour'] = df['timestamp'].dt.hour
    hourly_avg = df.groupby('hour')['density_pct'].mean()
    peak = hourly_avg.nlargest(3).index.tolist()
    low  = hourly_avg.nsmallest(3).index.tolist()
    print(f'En yoğun 3 saat      : {sorted(peak)}  (06–09 ve 14–22 arası beklenir)')
    print(f'En sakin 3 saat      : {sorted(low)}   (01–04 arası beklenir)')

    # Mevsimsel kontrol
    df['month'] = df['timestamp'].dt.month
    summer = df[df['month'].isin([6, 7, 8])]['density_pct'].mean()
    winter = df[df['month'].isin([12, 1, 2])]['density_pct'].mean()
    ok = '✓ yaz > kış' if summer > winter else '✗ HATA'
    print(f'Yaz ort. doluluk     : {summer:.4f}')
    print(f'Kış ort. doluluk     : {winter:.4f}  {ok}')

    # Yıllık büyüme (COVID düşüşü görülmeli)
    df['year'] = df['timestamp'].dt.year
    y16 = df[df['year'] == 2016]['density_pct'].mean()
    y20 = df[df['year'] == 2020]['density_pct'].mean()
    y25 = df[df['year'] == 2025]['density_pct'].mean()
    ok = '✓ büyüme var' if y25 > y16 else '✗ HATA'
    print(f'Ort. doluluk 2016    : {y16:.4f}')
    print(f'Ort. doluluk 2020    : {y20:.4f}  (COVID düşüşü beklenir)')
    print(f'Ort. doluluk 2025    : {y25:.4f}  {ok}')

    # Dosya boyutu
    mb = output_path.stat().st_size / 1024 / 1024
    print(f'Dosya boyutu         : {mb:.1f} MB  (Snappy sıkıştırma)')
    print('=' * 62)
    print('✓ Temel doğrulama tamamlandı.')
    print(f'  Ayrıntılı rapor için: python verify_data.py\n')


# =============================================================================
# ANA ÇALIŞMA BLOĞU
# =============================================================================

def main() -> None:
    print('=' * 62)
    print('  Eco-Terminal — Sentetik Veri Üreticisi')
    print(f'  Tarih aralığı : {START_DATE.date()} → {END_DATE.date()}')
    print(f'  Zone sayısı   : {len(ZONES)}')
    print(f'  Aralık        : 15 dakika')
    print(f'  Beklenen kayıt: ~5.3–5.5 milyon')
    print(f'  Çıktı         : {OUTPUT}')
    print('=' * 62)

    if OUTPUT.exists():
        print(f'\nUYARI: {OUTPUT.name} zaten mevcut. Üzerine yazılacak.')

    total_t0 = time.time()

    # Tatil haritasını bir kez oluştur, tüm yıllara paylaş
    print('\n[1/12] Tatil takvimi oluşturuluyor...')
    holiday_map = build_holiday_map()
    print(f'       {len(holiday_map)} tatil günü tanımlandı.')

    # ParquetWriter: yıl bazında chunk'ları aynı dosyaya yazar
    writer = None
    total_rows = 0

    years = list(range(2016, 2027))   # 2016, 2017, …, 2026

    for idx, year in enumerate(years, start=2):
        t0 = time.time()
        print(f'[{idx}/{len(years)+1}] {year} işleniyor...', end='  ', flush=True)

        df_year = generate_year(year, holiday_map)

        # pandas DataFrame → PyArrow Table → Parquet (incremental write)
        table = pa.Table.from_pandas(df_year, preserve_index=False)

        if writer is None:
            writer = pq.ParquetWriter(str(OUTPUT), table.schema, compression='snappy')

        writer.write_table(table)
        total_rows += len(df_year)

        elapsed = time.time() - t0
        print(f'{len(df_year):>9,} satır  |  {elapsed:.1f}s')

        # Belleği serbest bırak
        del df_year, table

    if writer:
        writer.close()

    total_elapsed = time.time() - total_t0
    print(f'\nToplam süre  : {total_elapsed:.1f}s  ({total_elapsed / 60:.1f} dakika)')
    print(f'Toplam satır : {total_rows:,}')

    # Hızlı doğrulama
    quick_validate(OUTPUT)


if __name__ == '__main__':
    main()
