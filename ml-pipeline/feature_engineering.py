#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
feature_engineering.py
======================
Eco-Terminal ML Pipeline — Ortak Özellik Mühendisliği Modülü.

train_xgboost.py ve train_analog.py tarafından import edilir.
evaluate_models.py ve predict.py de bu modülü kullanır.

Birinci çalıştırmada özellikler işlenir ve data/processed_features.parquet
olarak kaydedilir. Sonraki çalıştırmalarda önbellekten okunur.
"""

from __future__ import annotations

import time
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

# ── Dizin yapısı ──────────────────────────────────────────────────────────────
ML_DIR    = Path(__file__).parent
DATA_DIR  = ML_DIR / 'data'
MODELS_DIR = ML_DIR / 'models'
MODELS_DIR.mkdir(parents=True, exist_ok=True)

RAW_PARQUET    = DATA_DIR / 'synthetic_terminal_data.parquet'
CACHE_PARQUET  = DATA_DIR / 'processed_features.parquet'

# ── Zaman bazlı bölüm sınırları ───────────────────────────────────────────────
TRAIN_END = pd.Timestamp('2024-12-31 23:45:00')   # eğitim sonu
VAL_END   = pd.Timestamp('2025-12-31 23:45:00')   # doğrulama sonu
# Test: VAL_END sonrası (2026-01-01 → 2026-05-29)

# ── Zone listesi (DB ile uyumlu) ──────────────────────────────────────────────
ZONES = [
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
ZONE_IDS = [z['id'] for z in ZONES]

# =============================================================================
# TATİL GÜNLERİ  (generate_synthetic_data.py ile aynı mantık)
# =============================================================================
_HOLIDAY_PERIODS = [
    # Ramazan Bayramı
    (2016, 7, 5, 7, 8), (2017, 6, 25, 6, 28), (2018, 6, 14, 6, 17),
    (2019, 6, 4, 6, 7), (2020, 5, 23, 5, 26), (2021, 5, 13, 5, 16),
    (2022, 5, 2, 5, 5), (2023, 4, 21, 4, 24), (2024, 4, 10, 4, 13),
    (2025, 3, 30, 4, 2), (2026, 3, 20, 3, 23),
    # Kurban Bayramı
    (2016, 9, 12, 9, 16), (2017, 9, 1, 9, 5), (2018, 8, 21, 8, 25),
    (2019, 8, 11, 8, 15), (2020, 7, 31, 8, 4), (2021, 7, 20, 7, 24),
    (2022, 7, 9, 7, 13), (2023, 6, 28, 7, 2), (2024, 6, 17, 6, 21),
    (2025, 6, 6, 6, 10),
]


def _build_holiday_dates() -> set:
    dates: set = set()
    for yr, sm, sd, em, ed in _HOLIDAY_PERIODS:
        cur = pd.Timestamp(f'{yr}-{sm:02d}-{sd:02d}')
        end = pd.Timestamp(f'{yr}-{em:02d}-{ed:02d}')
        while cur <= end:
            dates.add(cur.date())
            cur += pd.Timedelta(days=1)
    for yr in range(2016, 2027):
        for d in range(24, 32):
            dates.add(pd.Timestamp(f'{yr}-12-{d:02d}').date())
        for d in range(1, 8):
            dates.add(pd.Timestamp(f'{yr}-01-{d:02d}').date())
    return dates


HOLIDAY_DATES: set = _build_holiday_dates()

# =============================================================================
# FEATURE SÜTUNLARı
# =============================================================================

# XGBoost — yoğunluk modeli
DENSITY_FEATURES = [
    'year', 'month', 'day', 'hour', 'minute', 'dow',
    'is_weekend', 'is_holiday',
    'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos', 'month_sin', 'month_cos',
    'zone_id', 'capacity',
    'zone_type_GATE', 'zone_type_SECURITY', 'zone_type_CHECKIN', 'zone_type_LOUNGE',
    'density_lag_1', 'density_lag_4', 'density_lag_96', 'density_lag_672',
    'energy_lag_1',  'energy_lag_4',  'energy_lag_96',
]

# XGBoost — enerji modeli
ENERGY_FEATURES = [
    'year', 'month', 'day', 'hour', 'minute', 'dow',
    'is_weekend', 'is_holiday',
    'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos', 'month_sin', 'month_cos',
    'zone_id', 'capacity',
    'zone_type_GATE', 'zone_type_SECURITY', 'zone_type_CHECKIN', 'zone_type_LOUNGE',
    'density_lag_1', 'density_lag_4', 'density_lag_96',
    'energy_lag_1',  'energy_lag_4',  'energy_lag_96', 'energy_lag_672',
]

# Analog kNN — yoğunluk (daha az özellik, mesafe hesabı için)
ANALOG_DENSITY_FEATURES = [
    'hour', 'dow', 'month', 'is_weekend', 'is_holiday', 'year_norm',
    'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos',
    'density_lag_4', 'density_lag_96',
]

# Analog kNN — enerji
ANALOG_ENERGY_FEATURES = [
    'hour', 'dow', 'month', 'is_weekend', 'is_holiday', 'year_norm',
    'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos',
    'density_lag_4', 'energy_lag_4', 'energy_lag_96',
]


# =============================================================================
# VERİ HAZIRLAMA
# =============================================================================

def prepare_dataset(force_rebuild: bool = False) -> pd.DataFrame:
    """
    Ham Parquet'i yükler, özellik mühendisliği uygular ve önbelleğe alır.

    İkinci çalıştırmada 'data/processed_features.parquet' önbelleğinden
    okunur (~10× daha hızlı). Yeniden hesaplamak için force_rebuild=True geç.

    Döndürür
    --------
    pd.DataFrame – tüm özellikler dahil, timestamp sütunu ile sıralı.
    """
    if not force_rebuild and CACHE_PARQUET.exists():
        print(f'  Önbellekten yükleniyor: {CACHE_PARQUET.name}...', end=' ', flush=True)
        df = pd.read_parquet(CACHE_PARQUET)
        df['timestamp'] = pd.to_datetime(df['timestamp'])
        print(f'{len(df):,} satır.')
        return df

    if not RAW_PARQUET.exists():
        raise FileNotFoundError(
            f'Ham veri bulunamadı: {RAW_PARQUET}\n'
            '  Önce çalıştırın: python generate_synthetic_data.py'
        )

    t0 = time.time()

    # ── Ham veri ──────────────────────────────────────────────────────────────
    print(f'  Ham veri yükleniyor ({RAW_PARQUET.name})...', end=' ', flush=True)
    df = pd.read_parquet(RAW_PARQUET)
    df['timestamp']  = pd.to_datetime(df['timestamp'])
    df['zone_type']  = df['zone_type'].astype(str)
    df['zone_name']  = df['zone_name'].astype(str)
    df['zone_id']    = df['zone_id'].astype(np.int8)
    df['capacity']   = df['capacity'].astype(np.int16)
    df['density_pct'] = df['density_pct'].astype(np.float32)
    df['energy_kwh']  = df['energy_kwh'].astype(np.float32)
    print(f'{len(df):,} satır yüklendi.')

    # ── Zaman özellikleri ─────────────────────────────────────────────────────
    print('  Zaman özellikleri...', end=' ', flush=True)
    df['year']     = df['timestamp'].dt.year.astype(np.int16)
    df['month']    = df['timestamp'].dt.month.astype(np.int8)
    df['day']      = df['timestamp'].dt.day.astype(np.int8)
    df['hour']     = df['timestamp'].dt.hour.astype(np.int8)
    df['minute']   = df['timestamp'].dt.minute.astype(np.int8)
    df['dow']      = df['timestamp'].dt.dayofweek.astype(np.int8)
    df['is_weekend'] = (df['dow'] >= 5).astype(np.int8)
    df['year_norm']  = ((df['year'] - 2016) / 10.0).astype(np.float32)
    date_series      = df['timestamp'].dt.date
    df['is_holiday'] = date_series.apply(
        lambda d: np.int8(1) if d in HOLIDAY_DATES else np.int8(0)
    )
    print('tamam.')

    # ── Döngüsel encoding ─────────────────────────────────────────────────────
    print('  Döngüsel encoding (sin/cos)...', end=' ', flush=True)
    df['hour_sin']  = np.sin(2 * np.pi * df['hour']  / 24).astype(np.float32)
    df['hour_cos']  = np.cos(2 * np.pi * df['hour']  / 24).astype(np.float32)
    df['dow_sin']   = np.sin(2 * np.pi * df['dow']   / 7 ).astype(np.float32)
    df['dow_cos']   = np.cos(2 * np.pi * df['dow']   / 7 ).astype(np.float32)
    df['month_sin'] = np.sin(2 * np.pi * df['month'] / 12).astype(np.float32)
    df['month_cos'] = np.cos(2 * np.pi * df['month'] / 12).astype(np.float32)
    print('tamam.')

    # ── Zone tipi one-hot ─────────────────────────────────────────────────────
    print('  Zone one-hot encoding...', end=' ', flush=True)
    for zt in ['GATE', 'SECURITY', 'CHECKIN', 'LOUNGE']:
        df[f'zone_type_{zt}'] = (df['zone_type'] == zt).astype(np.int8)
    print('tamam.')

    # ── Lag özellikleri (zone bazında, zaman sıralı) ──────────────────────────
    print('  Lag özellikler hesaplanıyor (1-3 dakika)...', end=' ', flush=True)
    df = df.sort_values(['zone_id', 'timestamp']).reset_index(drop=True)

    lag_map = {'1': 1, '4': 4, '96': 96, '672': 672}
    for label, n in lag_map.items():
        df[f'density_lag_{label}'] = (
            df.groupby('zone_id')['density_pct'].shift(n).astype(np.float32)
        )
        df[f'energy_lag_{label}']  = (
            df.groupby('zone_id')['energy_kwh'].shift(n).astype(np.float32)
        )
    print('tamam.')

    # ── NaN temizleme (sadece 1 haftalık lagın eksik olduğu ilk kayıtlar) ─────
    lag_cols = [c for c in df.columns if 'lag_' in c]
    before   = len(df)
    df.dropna(subset=lag_cols, inplace=True)
    df.reset_index(drop=True, inplace=True)
    print(f'  NaN silindi: {before:,} → {len(df):,} satır '
          f'({before - len(df):,} lag başlangıç satırı).')

    # ── Önbelleğe kaydet ──────────────────────────────────────────────────────
    print(f'  Önbelleğe yazılıyor → {CACHE_PARQUET.name}...', end=' ', flush=True)
    df.to_parquet(CACHE_PARQUET, index=False, engine='pyarrow', compression='snappy')
    print('tamam.')

    print(f'  Özellik hazırlama tamamlandı. ({time.time()-t0:.1f}s)')
    return df


def time_split(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """
    Zaman bazlı train / validation / test bölümü.
    Random shuffle YAPILMAZ — veri sırası korunur.
    """
    train = df[df['timestamp'] <= TRAIN_END].copy()
    val   = df[(df['timestamp'] > TRAIN_END) & (df['timestamp'] <= VAL_END)].copy()
    test  = df[df['timestamp'] > VAL_END].copy()
    print(f'  Split → eğitim: {len(train):,} | doğrulama: {len(val):,} | test: {len(test):,}')
    return train, val, test


# =============================================================================
# METRİKLER
# =============================================================================

def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    """MAE, RMSE, R² ve MAPE hesaplar. Sıfır bölme güvenli."""
    y_true = np.asarray(y_true, dtype=np.float64)
    y_pred = np.asarray(y_pred, dtype=np.float64)
    mask   = y_true > 1e-6
    mape   = float(
        np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100
    ) if mask.any() else float('nan')
    return {
        'MAE':  float(mean_absolute_error(y_true, y_pred)),
        'RMSE': float(np.sqrt(mean_squared_error(y_true, y_pred))),
        'R2':   float(r2_score(y_true, y_pred)),
        'MAPE': mape,
    }


def print_metrics(name: str, m: dict[str, float]) -> None:
    print(f'  {name:<30} MAE={m["MAE"]:.4f}  RMSE={m["RMSE"]:.4f}'
          f'  R²={m["R2"]:.4f}  MAPE=%{m["MAPE"]:.2f}')
