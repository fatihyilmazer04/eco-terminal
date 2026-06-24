#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
predict.py
==========
Eğitilmiş modelleri kullanarak tek nokta tahmini yapar.
Faz 3'te Spring Boot'tan çağrılacak Flask servisine temel oluşturur.

Kullanım:
  python predict.py --zone 1 --datetime "2026-06-01 14:00" --target density
  python predict.py --zone 10 --datetime "2026-03-15 08:30" --target energy
  python predict.py --zone 5 --datetime "2026-06-01 14:00" --target both

Parametreler:
  --zone      : zone_id (1–15)
  --datetime  : hedef tarih-saat  "YYYY-MM-DD HH:MM"
  --target    : density | energy | both
  --model     : xgboost | analog | both   (varsayılan: both)
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from analog_model import ZoneAnalogModel  # noqa: F401 — joblib deserialize için gerekli
from feature_engineering import (
    ANALOG_DENSITY_FEATURES, ANALOG_ENERGY_FEATURES,
    DENSITY_FEATURES, ENERGY_FEATURES,
    HOLIDAY_DATES, ZONES,
    MODELS_DIR, DATA_DIR, CACHE_PARQUET, RAW_PARQUET,
)

# =============================================================================
# ZONE YARDIMCISI
# =============================================================================

ZONE_MAP: dict[int, dict] = {z['id']: z for z in ZONES}


def zone_info(zone_id: int) -> dict:
    if zone_id not in ZONE_MAP:
        sys.exit(f'Hata: zone_id={zone_id} geçerli değil. (1–15 arası)')
    return ZONE_MAP[zone_id]


# =============================================================================
# LAG DEĞERİ BULMA
# Tahmin için lag özelliklerine ihtiyaç var.
# Önbellekten (processed_features.parquet) geçmiş verileri okur.
# =============================================================================

def get_lag_values(zone_id: int, target_dt: pd.Timestamp) -> dict[str, float]:
    """
    Hedef zaman damgasından önceki geçmiş lag değerlerini parquet'ten çeker.
    Önbellek yoksa ham veriyi tarar (daha yavaş).
    """
    src = CACHE_PARQUET if CACHE_PARQUET.exists() else RAW_PARQUET
    if not src.exists():
        print('  UYARI: Veri önbelleği bulunamadı — lag değerleri 0.0 olarak alındı.')
        return {
            'density_lag_1': 0.0,  'density_lag_4': 0.0,
            'density_lag_96': 0.0, 'density_lag_672': 0.0,
            'energy_lag_1': 0.0,   'energy_lag_4': 0.0,
            'energy_lag_96': 0.0,  'energy_lag_672': 0.0,
        }

    # Sadece ilgili zone ve son 2 haftalık dilimi oku
    df = pd.read_parquet(src, columns=['timestamp', 'zone_id', 'density_pct', 'energy_kwh'])
    df['timestamp'] = pd.to_datetime(df['timestamp'])
    df = df[df['zone_id'] == zone_id].sort_values('timestamp')
    df = df[df['timestamp'] < target_dt].tail(700)   # 1 hafta + buffer

    def lag_val(col: str, steps: int) -> float:
        idx = len(df) - steps
        if idx < 0:
            return float(df[col].mean()) if len(df) > 0 else 0.0
        return float(df.iloc[idx][col])

    return {
        'density_lag_1':   lag_val('density_pct', 1),
        'density_lag_4':   lag_val('density_pct', 4),
        'density_lag_96':  lag_val('density_pct', 96),
        'density_lag_672': lag_val('density_pct', 672),
        'energy_lag_1':    lag_val('energy_kwh',  1),
        'energy_lag_4':    lag_val('energy_kwh',  4),
        'energy_lag_96':   lag_val('energy_kwh',  96),
        'energy_lag_672':  lag_val('energy_kwh',  672),
    }


# =============================================================================
# FEATURE VEKTÖRÜ
# =============================================================================

def build_feature_row(zone_id: int, dt: pd.Timestamp, lag: dict[str, float]) -> pd.DataFrame:
    """Tek satırlık feature DataFrame'i oluşturur."""
    zone   = zone_info(zone_id)
    ztype  = zone['type']

    row = {
        'year':    dt.year,
        'month':   dt.month,
        'day':     dt.day,
        'hour':    dt.hour,
        'minute':  dt.minute,
        'dow':     dt.dayofweek,
        'is_weekend':  int(dt.dayofweek >= 5),
        'is_holiday':  int(dt.date() in HOLIDAY_DATES),
        'year_norm':   (dt.year - 2016) / 10.0,
        'hour_sin':    np.sin(2 * np.pi * dt.hour  / 24),
        'hour_cos':    np.cos(2 * np.pi * dt.hour  / 24),
        'dow_sin':     np.sin(2 * np.pi * dt.dayofweek / 7),
        'dow_cos':     np.cos(2 * np.pi * dt.dayofweek / 7),
        'month_sin':   np.sin(2 * np.pi * dt.month / 12),
        'month_cos':   np.cos(2 * np.pi * dt.month / 12),
        'zone_id':     zone_id,
        'capacity':    zone['capacity'],
        'zone_type_GATE':     int(ztype == 'GATE'),
        'zone_type_SECURITY': int(ztype == 'SECURITY'),
        'zone_type_CHECKIN':  int(ztype == 'CHECKIN'),
        'zone_type_LOUNGE':   int(ztype == 'LOUNGE'),
        **lag,
    }
    return pd.DataFrame([row])


# =============================================================================
# MODEL TAHMİNLERİ
# =============================================================================

def predict_xgboost(target: str, zone_id: int, feature_row: pd.DataFrame) -> float:
    path = MODELS_DIR / f'xgboost_{target}.pkl'
    if not path.exists():
        return float('nan')
    model = joblib.load(path)
    feat_cols = DENSITY_FEATURES if target == 'density' else ENERGY_FEATURES
    X = feature_row[feat_cols].values.astype(np.float32)
    return float(np.clip(model.predict(X)[0], 0, None))


def predict_analog(target: str, zone_id: int, feature_row: pd.DataFrame) -> float:
    path = MODELS_DIR / f'analog_{target}_zone_{zone_id}.pkl'
    if not path.exists():
        return float('nan')
    model = joblib.load(path)
    feat_cols = ANALOG_DENSITY_FEATURES if target == 'density' else ANALOG_ENERGY_FEATURES
    X = feature_row[feat_cols].values.astype(np.float32)
    return float(np.clip(model.predict(X)[0], 0, None))


# =============================================================================
# API FONKSİYONLARI  (Faz 3 Flask entegrasyonu için)
# =============================================================================

def predict_density(zone_id: int, target_datetime: str, model: str = 'xgboost') -> float:
    """
    Verilen zone ve tarih için yoğunluk tahmini (density_pct) döndürür.

    Parametreler
    ------------
    zone_id          : 1–15 arası zone numarası
    target_datetime  : 'YYYY-MM-DD HH:MM' formatında zaman
    model            : 'xgboost' | 'analog'

    Döndürür
    --------
    float : tahmin edilen density_pct (0.0–1.0)
    """
    dt  = pd.Timestamp(target_datetime)
    lag = get_lag_values(zone_id, dt)
    row = build_feature_row(zone_id, dt, lag)
    if model == 'analog':
        return predict_analog('density', zone_id, row)
    return predict_xgboost('density', zone_id, row)


def predict_energy(zone_id: int, target_datetime: str, model: str = 'xgboost') -> float:
    """
    Verilen zone ve tarih için enerji tahmini (energy_kwh) döndürür.

    Parametreler
    ------------
    zone_id          : 1–15 arası zone numarası
    target_datetime  : 'YYYY-MM-DD HH:MM' formatında zaman
    model            : 'xgboost' | 'analog'

    Döndürür
    --------
    float : tahmin edilen energy_kwh (15 dakikalık periyot)
    """
    dt  = pd.Timestamp(target_datetime)
    lag = get_lag_values(zone_id, dt)
    row = build_feature_row(zone_id, dt, lag)
    if model == 'analog':
        return predict_analog('energy', zone_id, row)
    return predict_xgboost('energy', zone_id, row)


# =============================================================================
# CLI
# =============================================================================

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description='Eco-Terminal ML — Tek Nokta Tahmin Aracı',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Örnekler:
  python predict.py --zone 1  --datetime "2026-06-01 14:00" --target density
  python predict.py --zone 10 --datetime "2026-03-15 08:30" --target energy
  python predict.py --zone 5  --datetime "2026-06-01 14:00" --target both --model both
        """,
    )
    p.add_argument('--zone',     type=int,  required=True,
                   help='Zone ID (1–15)')
    p.add_argument('--datetime', type=str,  required=True,
                   help='Hedef tarih-saat: "YYYY-MM-DD HH:MM"')
    p.add_argument('--target',   type=str,  default='density',
                   choices=['density', 'energy', 'both'],
                   help='Tahmin hedefi (varsayılan: density)')
    p.add_argument('--model',    type=str,  default='both',
                   choices=['xgboost', 'analog', 'both'],
                   help='Hangi model (varsayılan: both)')
    return p.parse_args()


def run_cli(args: argparse.Namespace) -> None:
    zone_id   = args.zone
    dt_str    = args.datetime
    target    = args.target
    model_sel = args.model

    zone = zone_info(zone_id)
    dt   = pd.Timestamp(dt_str)

    print('=' * 58)
    print('  Eco-Terminal ML Tahmin Aracı')
    print('=' * 58)
    print(f'  Zone      : {zone["name"]} (id={zone_id}, tip={zone["type"]}, kapasite={zone["capacity"]})')
    print(f'  Tarih-Saat: {dt}')
    print(f'  Hedef     : {target}')
    print(f'  Model     : {model_sel}')

    is_holiday = dt.date() in HOLIDAY_DATES
    is_weekend = dt.dayofweek >= 5
    print(f'  Tatil günü: {"EVET" if is_holiday else "Hayır"}  |  '
          f'Hafta sonu: {"EVET" if is_weekend else "Hayır"}')

    # Lag değerleri
    print('\n  Geçmiş veriler çekiliyor...', end=' ', flush=True)
    lag = get_lag_values(zone_id, dt)
    print('tamam.')
    print(f'  Lag değerleri: density_lag_4={lag["density_lag_4"]:.3f}  '
          f'density_lag_96={lag["density_lag_96"]:.3f}')

    row = build_feature_row(zone_id, dt, lag)

    print('\n  ─── TAHMİN SONUÇLARI ───────────────────────────────')

    targets = ['density', 'energy'] if target == 'both' else [target]
    models  = ['xgboost', 'analog'] if model_sel == 'both' else [model_sel]

    for tgt in targets:
        unit = 'doluluk (0–1)' if tgt == 'density' else 'kWh/15dk'
        print(f'\n  {tgt.upper()} — {unit}:')
        for mdl in models:
            if tgt == 'density':
                val = predict_xgboost('density', zone_id, row) if mdl == 'xgboost' \
                      else predict_analog('density', zone_id, row)
                extra = f'  ({int(val * zone["capacity"])} kişi, '
                extra += 'SAKİN' if val < 0.40 else ('YOĞUN' if val >= 0.70 else 'ORTA')
                extra += ')'
            else:
                val = predict_xgboost('energy', zone_id, row) if mdl == 'xgboost' \
                      else predict_analog('energy', zone_id, row)
                extra = f'  ({val * 4:.2f} kWh/saat saatlik tahmini)'

            model_label = 'XGBoost   ' if mdl == 'xgboost' else 'Analog kNN'
            status = '' if not np.isnan(val) else '  (model bulunamadı)'
            print(f'    {model_label}: {val:.4f}{extra}{status}')

    print('\n  ✓ Tahmin tamamlandı.')
    print('=' * 58)


# =============================================================================
# GİRİŞ NOKTASI
# =============================================================================

if __name__ == '__main__':
    args = parse_args()
    run_cli(args)
