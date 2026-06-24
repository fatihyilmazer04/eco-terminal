#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
train_xgboost.py
================
XGBoost modelleri eğitimi — yoğunluk (density_pct) ve enerji (energy_kwh).

Çıktı dosyaları:
  models/xgboost_density.pkl
  models/xgboost_energy.pkl
  data/xgboost_density_importance.png
  data/xgboost_energy_importance.png

Kullanım:
  python train_xgboost.py
"""

from __future__ import annotations

import time
from pathlib import Path

import joblib
import numpy as np
import xgboost as xgb

from feature_engineering import (
    DENSITY_FEATURES, ENERGY_FEATURES,
    MODELS_DIR, DATA_DIR,
    compute_metrics, print_metrics,
    prepare_dataset, time_split,
)

# ── Sabitler ──────────────────────────────────────────────────────────────────
RANDOM_STATE = 42

# XGBoost hiperparametreleri — ikisi için de aynı başlangıç seti
XGB_PARAMS = dict(
    objective        = 'reg:squarederror',
    n_estimators     = 500,
    max_depth        = 8,
    learning_rate    = 0.05,
    subsample        = 0.8,
    colsample_bytree = 0.8,
    min_child_weight = 5,
    tree_method      = 'hist',          # GPU yoksa CPU'da çok hızlı
    n_jobs           = -1,
    random_state     = RANDOM_STATE,
    early_stopping_rounds = 20,
)


# =============================================================================
# FEATURE IMPORTANCE GRAFİĞİ
# =============================================================================

def plot_importance(model: xgb.XGBRegressor, feature_names: list[str],
                    title: str, out_path: Path, top_n: int = 20) -> None:
    """En önemli top_n özelliği gösteren yatay bar grafiği oluşturur."""
    try:
        import matplotlib.pyplot as plt

        scores = model.feature_importances_
        pairs  = sorted(zip(feature_names, scores), key=lambda x: x[1])[-top_n:]
        names  = [p[0] for p in pairs]
        vals   = [p[1] for p in pairs]

        fig, ax = plt.subplots(figsize=(9, max(4, top_n * 0.35)))
        bars = ax.barh(names, vals, color='#2ecc71', alpha=0.85, edgecolor='#1a8a4c')
        ax.set_xlabel('Önem Skoru (gain)', fontsize=11)
        ax.set_title(title, fontsize=13, fontweight='bold')
        ax.spines['top'].set_visible(False)
        ax.spines['right'].set_visible(False)
        for bar, v in zip(bars, vals):
            ax.text(v + 0.001, bar.get_y() + bar.get_height() / 2,
                    f'{v:.4f}', va='center', fontsize=8)
        plt.tight_layout()
        plt.savefig(str(out_path), dpi=130, bbox_inches='tight')
        plt.close()
        print(f'  Grafik kaydedildi: {out_path.name}')
    except ImportError:
        print('  (matplotlib kurulu değil — grafik atlandı)')


# =============================================================================
# MODEL EĞİTİMİ
# =============================================================================

def train_model(
    name: str,
    feature_cols: list[str],
    target_col: str,
    train, val, test,
    save_path: Path,
    plot_title: str,
    plot_path: Path,
) -> dict[str, float]:
    """
    Tek bir XGBoost modeli eğitir, kaydeder ve test metriklerini döndürür.
    """
    print(f'\n{"─" * 58}')
    print(f'  MODEL: {name}')
    print(f'{"─" * 58}')

    # ── Veri hazırlama ────────────────────────────────────────────────────────
    X_tr  = train[feature_cols].values.astype(np.float32)
    y_tr  = train[target_col].values.astype(np.float32)
    X_val = val[feature_cols].values.astype(np.float32)
    y_val = val[target_col].values.astype(np.float32)
    X_te  = test[feature_cols].values.astype(np.float32)
    y_te  = test[target_col].values.astype(np.float32)

    print(f'  Eğitim: {X_tr.shape[0]:,} | Doğrulama: {X_val.shape[0]:,} | Test: {X_te.shape[0]:,}')
    print(f'  Özellik sayısı: {X_tr.shape[1]}')

    # ── Eğitim ───────────────────────────────────────────────────────────────
    model = xgb.XGBRegressor(**XGB_PARAMS)

    t0 = time.time()
    print(f'  Eğitim başlıyor (early stopping: {XGB_PARAMS["early_stopping_rounds"]} tur)...')

    model.fit(
        X_tr, y_tr,
        eval_set=[(X_val, y_val)],
        verbose=False,
    )

    elapsed = time.time() - t0
    best_it  = model.best_iteration + 1
    print(f'  Eğitim tamamlandı: {elapsed:.1f}s — en iyi tur: {best_it}')

    # ── Test tahmini ve metrikler ─────────────────────────────────────────────
    y_pred = model.predict(X_te)
    y_pred = np.clip(y_pred, 0, None)

    m = compute_metrics(y_te, y_pred)
    print_metrics(f'XGBoost {name} (test)', m)

    # ── Validation metrikleri (bilgi için) ────────────────────────────────────
    y_val_pred = model.predict(X_val)
    m_val = compute_metrics(y_val, y_val_pred)
    print_metrics(f'XGBoost {name} (val)', m_val)

    # ── Kaydet ───────────────────────────────────────────────────────────────
    joblib.dump(model, save_path)
    print(f'  Model kaydedildi: {save_path}')

    # ── Feature importance ────────────────────────────────────────────────────
    plot_importance(model, feature_cols, plot_title, plot_path)

    # En önemli 5 özellik konsola
    scores  = model.feature_importances_
    top5    = sorted(zip(feature_cols, scores), key=lambda x: x[1], reverse=True)[:5]
    print('  En önemli 5 özellik:')
    for feat, sc in top5:
        print(f'    {feat:<30} {sc:.4f}')

    return m


# =============================================================================
# ANA ÇALIŞMA
# =============================================================================

def main() -> None:
    print('=' * 60)
    print('  Eco-Terminal — XGBoost Model Eğitimi')
    print(f'  Versiyon: xgboost {xgb.__version__}')
    print('=' * 60)

    # ── Veri hazırlama ────────────────────────────────────────────────────────
    print('\n[1/5] Veri hazırlanıyor...')
    df = prepare_dataset()

    print('\n[2/5] Zaman bazlı bölüm oluşturuluyor...')
    train, val, test = time_split(df)

    # Büyük df artık gerekli değil — belleği boşalt
    del df

    results: dict[str, dict[str, float]] = {}

    # ── Model 1: Yoğunluk ────────────────────────────────────────────────────
    print('\n[3/5] XGBoost Yoğunluk Modeli eğitiliyor...')
    results['density'] = train_model(
        name         = 'Yoğunluk (density_pct)',
        feature_cols = DENSITY_FEATURES,
        target_col   = 'density_pct',
        train        = train,
        val          = val,
        test         = test,
        save_path    = MODELS_DIR / 'xgboost_density.pkl',
        plot_title   = 'XGBoost — Yoğunluk Modeli Feature Importance',
        plot_path    = DATA_DIR / 'xgboost_density_importance.png',
    )

    # ── Model 2: Enerji ───────────────────────────────────────────────────────
    print('\n[4/5] XGBoost Enerji Modeli eğitiliyor...')
    results['energy'] = train_model(
        name         = 'Enerji (energy_kwh)',
        feature_cols = ENERGY_FEATURES,
        target_col   = 'energy_kwh',
        train        = train,
        val          = val,
        test         = test,
        save_path    = MODELS_DIR / 'xgboost_energy.pkl',
        plot_title   = 'XGBoost — Enerji Modeli Feature Importance',
        plot_path    = DATA_DIR / 'xgboost_energy_importance.png',
    )

    # ── Özet tablo ────────────────────────────────────────────────────────────
    print('\n[5/5] ─── ÖZET TABLO ───────────────────────────────────────')
    print(f'  {"Model":<35} {"MAE":>7} {"RMSE":>7} {"R²":>7} {"MAPE":>8}')
    print('  ' + '─' * 60)
    for mname, m in results.items():
        label = f'XGBoost {"Yoğunluk" if mname == "density" else "Enerji"}'
        print(f'  {label:<35} {m["MAE"]:>7.4f} {m["RMSE"]:>7.4f}'
              f' {m["R2"]:>7.4f} {m["MAPE"]:>7.2f}%')
    print()
    print('  Modeller kaydedildi:')
    print(f'    {MODELS_DIR / "xgboost_density.pkl"}')
    print(f'    {MODELS_DIR / "xgboost_energy.pkl"}')
    print()
    print('  Sonraki adım: python train_analog.py')


if __name__ == '__main__':
    main()
