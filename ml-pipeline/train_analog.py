#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
train_analog.py
===============
Analog kNN model hazırlama — yoğunluk ve enerji.

"Eğitim" klasik anlamda değil, geçmiş verilerin indexlenmesidir.
Tahmin anında sorgu noktasına en benzer k örnekler bulunur ve
ağırlıklı ortalama alınır (Analog Ensemble / AnEn yöntemi).

Her zone için ayrı model dosyası üretilir:
  models/analog_density_zone_{zone_id}.pkl   (15 dosya)
  models/analog_energy_zone_{zone_id}.pkl    (15 dosya)

Kullanım:
  python train_analog.py
"""

from __future__ import annotations

import time
from pathlib import Path

import joblib
import numpy as np

from analog_model import ZoneAnalogModel
from feature_engineering import (
    ANALOG_DENSITY_FEATURES, ANALOG_ENERGY_FEATURES,
    ZONE_IDS, ZONES,
    MODELS_DIR,
    compute_metrics, print_metrics,
    prepare_dataset, time_split,
)

# ── Sabitler ──────────────────────────────────────────────────────────────────
K_NEIGHBORS  = 5      # en benzer kaç örnek kullanılsın
RANDOM_STATE = 42


# =============================================================================
# TÜM ZONE'LAR İÇİN EĞİTİM + DEĞERLENDİRME
# =============================================================================

def train_all_zones(
    target: str,
    feature_cols: list[str],
    target_col: str,
    train_df,
    test_df,
) -> dict[str, float]:
    """
    15 zone'un her biri için bağımsız bir ZoneAnalogModel eğitir,
    kaydeder ve test seti üzerindeki toplu metrikleri döndürür.
    """
    print(f'\n{"─" * 60}')
    print(f'  HEDEF: {target.upper()}  |  Özellikler: {feature_cols}')
    print(f'{"─" * 60}')

    all_y_true:  list[float] = []
    all_y_pred:  list[float] = []
    zone_metrics: list[tuple] = []

    total_t0 = time.time()

    for i, zone_id in enumerate(ZONE_IDS, start=1):
        zone_name = next(z['name'] for z in ZONES if z['id'] == zone_id)
        t0 = time.time()

        # Zone'a özel eğitim ve test bölümü
        z_train = train_df[train_df['zone_id'] == zone_id]
        z_test  = test_df[test_df['zone_id'] == zone_id]

        if len(z_train) == 0:
            print(f'  [{i:02d}/15] {zone_name:<22} — eğitim verisi yok, atlandı.')
            continue

        X_tr = z_train[feature_cols].values.astype(np.float32)
        y_tr = z_train[target_col].values.astype(np.float32)
        X_te = z_test[feature_cols].values.astype(np.float32)
        y_te = z_test[target_col].values.astype(np.float32)

        # Model oluştur ve indexle
        model = ZoneAnalogModel(zone_id=zone_id, target=target)
        model.fit(X_tr, y_tr)

        # Test tahmini
        y_pred = model.predict(X_te)
        y_pred = np.clip(y_pred, 0, None)

        m = compute_metrics(y_te, y_pred)
        zone_metrics.append((zone_name, m))

        all_y_true.extend(y_te.tolist())
        all_y_pred.extend(y_pred.tolist())

        # Kaydet
        save_path = MODELS_DIR / f'analog_{target}_zone_{zone_id}.pkl'
        joblib.dump(model, save_path, compress=3)

        elapsed = time.time() - t0
        print(f'  [{i:02d}/15] {zone_name:<22}  '
              f'n_tr={len(X_tr):>7,}  '
              f'MAE={m["MAE"]:.4f}  R²={m["R2"]:.4f}  '
              f'({elapsed:.1f}s)')

    total_elapsed = time.time() - total_t0

    # Genel metrikler (tüm zone'lar toplu)
    overall = compute_metrics(
        np.array(all_y_true, dtype=np.float32),
        np.array(all_y_pred, dtype=np.float32),
    )
    print(f'\n  Toplam süre: {total_elapsed:.1f}s')
    print_metrics(f'Analog {target.capitalize()} GENEL (test)', overall)

    # Zone bazlı özet
    print(f'\n  {"Zone":<22} {"MAE":>7} {"RMSE":>7} {"R²":>7} {"MAPE":>7}')
    print('  ' + '─' * 52)
    for zone_name, m in zone_metrics:
        print(f'  {zone_name:<22} {m["MAE"]:>7.4f} {m["RMSE"]:>7.4f}'
              f' {m["R2"]:>7.4f} {m["MAPE"]:>6.2f}%')

    return overall


# =============================================================================
# ANA ÇALIŞMA
# =============================================================================

def main() -> None:
    print('=' * 60)
    print('  Eco-Terminal — Analog kNN Model Hazırlama')
    print(f'  k = {K_NEIGHBORS} komşu  |  metrik = Euclidean')
    print('=' * 60)

    # ── Veri hazırlama ────────────────────────────────────────────────────────
    print('\n[1/5] Veri hazırlanıyor...')
    df = prepare_dataset()

    print('\n[2/5] Zaman bazlı bölüm oluşturuluyor...')
    train, val, test = time_split(df)
    del df  # belleği boşalt; val kullanılmıyor (kNN için validation gerekmez)

    results: dict[str, dict[str, float]] = {}

    # ── Yoğunluk modelleri ────────────────────────────────────────────────────
    print('\n[3/5] Analog kNN Yoğunluk modelleri...')
    results['density'] = train_all_zones(
        target       = 'density',
        feature_cols = ANALOG_DENSITY_FEATURES,
        target_col   = 'density_pct',
        train_df     = train,
        test_df      = test,
    )

    # ── Enerji modelleri ──────────────────────────────────────────────────────
    print('\n[4/5] Analog kNN Enerji modelleri...')
    results['energy'] = train_all_zones(
        target       = 'energy',
        feature_cols = ANALOG_ENERGY_FEATURES,
        target_col   = 'energy_kwh',
        train_df     = train,
        test_df      = test,
    )

    # ── Özet ─────────────────────────────────────────────────────────────────
    print('\n[5/5] ─── ÖZET TABLO ───────────────────────────────────────')
    print(f'  {"Model":<35} {"MAE":>7} {"RMSE":>7} {"R²":>7} {"MAPE":>8}')
    print('  ' + '─' * 60)
    for tgt, m in results.items():
        label = f'Analog {"Yoğunluk" if tgt == "density" else "Enerji"}'
        print(f'  {label:<35} {m["MAE"]:>7.4f} {m["RMSE"]:>7.4f}'
              f' {m["R2"]:>7.4f} {m["MAPE"]:>7.2f}%')

    print()
    print(f'  30 model dosyası kaydedildi → {MODELS_DIR}/')
    print(f'  analog_density_zone_1.pkl … analog_energy_zone_15.pkl')
    print()
    print('  Sonraki adım: python evaluate_models.py')


if __name__ == '__main__':
    main()
