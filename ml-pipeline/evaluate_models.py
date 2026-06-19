#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
evaluate_models.py
==================
4 modeli test seti üzerinde karşılaştırır ve kapsamlı rapor üretir:
  1. XGBoost — Yoğunluk
  2. Analog kNN — Yoğunluk
  3. XGBoost — Enerji
  4. Analog kNN — Enerji

Çıktı dosyaları:
  data/eval_density_comparison.png
  data/eval_energy_comparison.png
  data/eval_zone_breakdown.png
  data/comparison_report.txt   ← savunma için Türkçe özet

Kullanım:
  python evaluate_models.py
"""

from __future__ import annotations

import time
from datetime import datetime
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
import pandas as pd

from analog_model import ZoneAnalogModel  # noqa: F401 — joblib deserialize için gerekli
from feature_engineering import (
    ANALOG_DENSITY_FEATURES, ANALOG_ENERGY_FEATURES,
    DENSITY_FEATURES, ENERGY_FEATURES,
    ZONE_IDS, ZONES,
    MODELS_DIR, DATA_DIR,
    compute_metrics,
    prepare_dataset, time_split,
)

# ── Matplotlib — yoksa grafikler atlanır ──────────────────────────────────────
try:
    import matplotlib
    matplotlib.use('Agg')   # GUI olmadan PNG kaydet
    import matplotlib.pyplot as plt
    import matplotlib.gridspec as gridspec
    HAS_MPL = True
except ImportError:
    HAS_MPL = False


# =============================================================================
# MODEL YÜKLEYİCİLER
# =============================================================================

def load_xgboost(target: str):
    path = MODELS_DIR / f'xgboost_{target}.pkl'
    if not path.exists():
        raise FileNotFoundError(
            f'XGBoost {target} modeli bulunamadı: {path}\n'
            f'  Önce çalıştırın: python train_xgboost.py'
        )
    return joblib.load(path)


def load_analog_zone(target: str, zone_id: int):
    path = MODELS_DIR / f'analog_{target}_zone_{zone_id}.pkl'
    if not path.exists():
        raise FileNotFoundError(
            f'Analog {target} zone-{zone_id} modeli bulunamadı: {path}\n'
            f'  Önce çalıştırın: python train_analog.py'
        )
    return joblib.load(path)


# =============================================================================
# TAHMİN FONKSİYONLARI
# =============================================================================

def predict_xgboost(model, test_df: pd.DataFrame, feature_cols: list[str]) -> np.ndarray:
    X = test_df[feature_cols].values.astype(np.float32)
    y_pred = model.predict(X)
    return np.clip(y_pred, 0, None)


def predict_analog_all_zones(
    analog_models: dict[int, object],
    test_df: pd.DataFrame,
    feature_cols: list[str],
    target_col: str,
) -> tuple[np.ndarray, np.ndarray]:
    """
    Zone bazlı analog tahminleri birleştirir.
    Döndürür: (y_true, y_pred) — test setindeki sıraya göre.
    """
    all_pred = np.empty(len(test_df), dtype=np.float32)
    idx = test_df.index

    for zone_id, model in analog_models.items():
        mask = test_df['zone_id'] == zone_id
        if not mask.any():
            continue
        X = test_df.loc[mask, feature_cols].values.astype(np.float32)
        preds = model.predict(X)
        # test_df'nin local integer index'i kullanılıyor
        local_idx = np.where(mask.values)[0]
        all_pred[local_idx] = np.clip(preds, 0, None)

    y_true = test_df[target_col].values.astype(np.float32)
    return y_true, all_pred


# =============================================================================
# GRAFİK ÜRETİCİLER
# =============================================================================

def plot_scatter_and_residuals(
    y_true: np.ndarray, preds: dict[str, np.ndarray],
    target_label: str, unit: str, out_path: Path,
    sample: int = 3000,
) -> None:
    if not HAS_MPL:
        return
    rng   = np.random.default_rng(42)
    sidx  = rng.choice(len(y_true), size=min(sample, len(y_true)), replace=False)

    fig, axes = plt.subplots(2, 2, figsize=(13, 10))
    fig.suptitle(f'{target_label} — Tahmin Karşılaştırması', fontsize=14, fontweight='bold')

    colors = {'XGBoost': '#2ecc71', 'Analog kNN': '#3498db'}

    for col, (mname, y_pred) in enumerate(preds.items()):
        # Scatter: gerçek vs tahmin
        ax = axes[0, col]
        ax.scatter(y_true[sidx], y_pred[sidx],
                   alpha=0.25, s=6, color=colors[mname], rasterized=True)
        lo, hi = float(y_true.min()), float(y_true.max())
        ax.plot([lo, hi], [lo, hi], 'r--', lw=1.2, label='Mükemmel tahmin')
        ax.set_xlabel(f'Gerçek {unit}', fontsize=10)
        ax.set_ylabel(f'Tahmin {unit}', fontsize=10)
        ax.set_title(f'{mname} — Gerçek vs Tahmin', fontsize=11)
        ax.legend(fontsize=8)

        # Hata histogramı
        ax2 = axes[1, col]
        residuals = y_pred - y_true
        ax2.hist(residuals, bins=80, color=colors[mname],
                 alpha=0.75, edgecolor='none')
        ax2.axvline(0, color='red', lw=1.2, linestyle='--')
        ax2.axvline(float(residuals.mean()), color='orange',
                    lw=1.2, linestyle='-', label=f'Ort. hata={residuals.mean():.4f}')
        ax2.set_xlabel(f'Hata ({unit})', fontsize=10)
        ax2.set_ylabel('Frekans', fontsize=10)
        ax2.set_title(f'{mname} — Hata Dağılımı', fontsize=11)
        ax2.legend(fontsize=8)

    plt.tight_layout()
    plt.savefig(str(out_path), dpi=120, bbox_inches='tight')
    plt.close()
    print(f'  Grafik → {out_path.name}')


def plot_zone_breakdown(
    zone_metrics: dict[str, dict[str, dict[str, float]]],
    out_path: Path,
) -> None:
    """
    Her zone için XGBoost ve Analog kNN R² değerlerini karşılaştırır.
    zone_metrics: {'density': {'XGBoost': {zone_name: m}, 'Analog': {...}}, 'energy': ...}
    """
    if not HAS_MPL:
        return

    fig, axes = plt.subplots(1, 2, figsize=(16, 6))
    fig.suptitle('Zone Bazlı R² Karşılaştırması', fontsize=14, fontweight='bold')
    bar_w = 0.35

    for ax, (tgt, label) in zip(axes, [('density', 'Yoğunluk'), ('energy', 'Enerji')]):
        xgb_m    = zone_metrics[tgt]['XGBoost']
        analog_m = zone_metrics[tgt]['Analog kNN']
        zone_names = list(xgb_m.keys())
        x = np.arange(len(zone_names))

        xgb_r2    = [xgb_m[z]['R2']    for z in zone_names]
        analog_r2 = [analog_m[z]['R2'] for z in zone_names]

        ax.bar(x - bar_w / 2, xgb_r2,    bar_w, label='XGBoost',    color='#2ecc71', alpha=0.85)
        ax.bar(x + bar_w / 2, analog_r2, bar_w, label='Analog kNN', color='#3498db', alpha=0.85)
        ax.set_xticks(x)
        ax.set_xticklabels(zone_names, rotation=45, ha='right', fontsize=7)
        ax.set_ylabel('R²', fontsize=11)
        ax.set_ylim(0, 1.05)
        ax.axhline(0.9, color='red', linestyle='--', lw=0.8, alpha=0.5, label='R²=0.90')
        ax.set_title(f'{label} Modeli — Zone R²', fontsize=12)
        ax.legend(fontsize=9)

    plt.tight_layout()
    plt.savefig(str(out_path), dpi=120, bbox_inches='tight')
    plt.close()
    print(f'  Grafik → {out_path.name}')


def plot_time_sample(
    test_df: pd.DataFrame,
    xgb_density_pred: np.ndarray,
    analog_density_pred: np.ndarray,
    zone_id: int = 1,
    out_path: Optional[Path] = None,
) -> None:
    """Bir zone için örnek bir günün 4-model tahmin karşılaştırması."""
    if not HAS_MPL or out_path is None:
        return

    test_df = test_df.reset_index(drop=True)
    sample_date = test_df['timestamp'].dt.date.min()
    mask = (test_df['zone_id'] == zone_id) & (test_df['timestamp'].dt.date == sample_date)
    if mask.sum() < 4:
        return

    idx    = np.where(mask.values)[0]
    times  = test_df.loc[mask, 'timestamp'].values
    y_true = test_df.loc[mask, 'density_pct'].values
    y_xgb  = xgb_density_pred[idx]
    y_anl  = analog_density_pred[idx]

    zone_name = next((z['name'] for z in ZONES if z['id'] == zone_id), f'Zone {zone_id}')

    fig, ax = plt.subplots(figsize=(14, 5))
    ax.plot(times, y_true, 'k-',   lw=2,   label='Gerçek',        zorder=3)
    ax.plot(times, y_xgb,  '--',   lw=1.8, label='XGBoost',       color='#2ecc71', zorder=2)
    ax.plot(times, y_anl,  ':',    lw=1.8, label='Analog kNN',    color='#3498db', zorder=2)
    ax.fill_between(times, y_xgb, y_true, alpha=0.12, color='#2ecc71')
    ax.fill_between(times, y_anl, y_true, alpha=0.12, color='#3498db')
    ax.set_xlabel('Saat', fontsize=11)
    ax.set_ylabel('Doluluk Oranı', fontsize=11)
    ax.set_title(f'{zone_name} — {sample_date} tarihli yoğunluk tahmini', fontsize=12)
    ax.legend(fontsize=10)
    ax.set_ylim(0, 1.0)
    plt.tight_layout()
    plt.savefig(str(out_path), dpi=120, bbox_inches='tight')
    plt.close()
    print(f'  Grafik → {out_path.name}')


# =============================================================================
# METİN RAPORU
# =============================================================================

def write_text_report(
    overall: dict[str, dict[str, dict[str, float]]],
    zone_metrics: dict[str, dict[str, dict[str, float]]],
    out_path: Path,
) -> None:
    """Savunma için Türkçe özet rapor yazar."""
    lines = []
    sep   = '=' * 70

    lines.append(sep)
    lines.append('  ECO-TERMINAL ML MODELİ KARŞILAŞTIRMA RAPORU')
    lines.append(f'  Üretim Tarihi: {datetime.now().strftime("%Y-%m-%d %H:%M")}')
    lines.append(sep)

    lines.append('\n1. GENEL AÇIKLAMA')
    lines.append('─' * 70)
    lines.append(
        'Bu rapor, Eco-Terminal havalimanı yönetim sistemi için geliştirilen\n'
        'iki farklı makine öğrenmesi modelinin performans karşılaştırmasını içerir.\n\n'
        'Veri: 2016-2026 arası 10 yıllık sentetik havalimanı verisi (5.4M kayıt)\n'
        'Test seti: 2026-01-01 ile 2026-05-29 arası (~%4 veri)\n'
        'Aralık: 15 dakikalık örnekleme | Zone sayısı: 15'
    )

    lines.append('\n2. MODEL AÇIKLAMALARI')
    lines.append('─' * 70)
    lines.append(
        'XGBoost   : Gradient boosted ağaçlar. Tüm zone\'lar için tek global model.\n'
        '            Lag, döngüsel kodlama ve tatil özelliklerini birlikte kullanır.\n'
        '            Avantaj: Çok hızlı tahmin, yüksek doğruluk.\n\n'
        'Analog kNN: Her zone için bağımsız k-En Yakın Komşu (k=5).\n'
        '            Tarihsel benzer anları bulur, ağırlıklı ortalama alır.\n'
        '            Avantaj: Açıklanabilir, zone\'a özel örüntüleri yakalar.'
    )

    lines.append('\n3. GENEL METRİKLER (Test Seti)')
    lines.append('─' * 70)
    header = f'{"Model":<35} {"MAE":>8} {"RMSE":>8} {"R²":>8} {"MAPE":>8}'
    lines.append(header)
    lines.append('─' * 70)

    for tgt_label, tgt_key in [('Yoğunluk', 'density'), ('Enerji', 'energy')]:
        for model_name in ['XGBoost', 'Analog kNN']:
            m     = overall[tgt_key][model_name]
            label = f'{model_name} — {tgt_label}'
            lines.append(
                f'{label:<35} {m["MAE"]:>8.4f} {m["RMSE"]:>8.4f}'
                f' {m["R2"]:>8.4f} {m["MAPE"]:>7.2f}%'
            )
        lines.append('')

    lines.append('\n4. ZONE BAZLI R² ÖZETI')
    lines.append('─' * 70)
    for tgt_label, tgt_key in [('Yoğunluk', 'density'), ('Enerji', 'energy')]:
        lines.append(f'\n  {tgt_label} Modeli:')
        lines.append(f'  {"Zone":<22} {"XGBoost R²":>12} {"Analog R²":>12} {"Kazanan":>10}')
        lines.append('  ' + '─' * 58)
        for zone_name in zone_metrics[tgt_key]['XGBoost']:
            xr2 = zone_metrics[tgt_key]['XGBoost'][zone_name]['R2']
            ar2 = zone_metrics[tgt_key]['Analog kNN'][zone_name]['R2']
            winner = 'XGBoost' if xr2 >= ar2 else 'Analog'
            lines.append(f'  {zone_name:<22} {xr2:>12.4f} {ar2:>12.4f} {winner:>10}')

    lines.append('\n5. SONUÇ VE ÖNERİLER')
    lines.append('─' * 70)
    # XGBoost genel olarak daha iyi
    xgb_d_r2 = overall['density']['XGBoost']['R2']
    anl_d_r2 = overall['density']['Analog kNN']['R2']
    winner    = 'XGBoost' if xgb_d_r2 >= anl_d_r2 else 'Analog kNN'
    lines.append(
        f'Yoğunluk tahmini için en iyi model: {winner}\n'
        f'  XGBoost R²={xgb_d_r2:.4f} vs Analog kNN R²={anl_d_r2:.4f}\n\n'
        'Önerilen üretim senaryosu:\n'
        '  - Gerçek zamanlı tahmin: XGBoost (çok düşük gecikme, ~ms)\n'
        '  - Açıklanabilirlik gerektiren kararlar: Analog kNN\n'
        '  - Hibrit: her ikisinin ensemble ortalaması denenebilir\n\n'
        'Faz 3 planı: Spring Boot backend\'e Python Flask microservice\n'
        'olarak entegre edilecek (predict.py → Flask API).'
    )

    lines.append('\n' + sep)
    lines.append('  Raporun tamamı: ml-pipeline/data/comparison_report.txt')
    lines.append(sep)

    report_text = '\n'.join(lines)
    out_path.write_text(report_text, encoding='utf-8')
    print(f'\n  Metin raporu → {out_path.name}')
    print(report_text)


# =============================================================================
# ANA ÇALIŞMA
# =============================================================================

def main() -> None:
    print('=' * 60)
    print('  Eco-Terminal — Model Karşılaştırma ve Değerlendirme')
    print('=' * 60)

    # ── Veri ─────────────────────────────────────────────────────────────────
    print('\n[1/7] Veri hazırlanıyor...')
    df = prepare_dataset()

    print('\n[2/7] Zaman bölümü oluşturuluyor...')
    _, _, test = time_split(df)
    del df
    test = test.reset_index(drop=True)
    print(f'  Test seti: {len(test):,} satır')

    # ── Model yükleme ─────────────────────────────────────────────────────────
    print('\n[3/7] Modeller yükleniyor...')
    xgb_density = load_xgboost('density')
    xgb_energy  = load_xgboost('energy')

    analog_density: dict[int, object] = {}
    analog_energy:  dict[int, object] = {}
    for zid in ZONE_IDS:
        analog_density[zid] = load_analog_zone('density', zid)
        analog_energy[zid]  = load_analog_zone('energy',  zid)
    print(f'  4 model yüklendi (2 XGBoost + 30 Analog)')

    # ── Tahminler ─────────────────────────────────────────────────────────────
    print('\n[4/7] Tahminler yapılıyor...')

    t0 = time.time()
    xgb_density_pred  = predict_xgboost(xgb_density, test, DENSITY_FEATURES)
    t_xd = time.time() - t0; t0 = time.time()

    xgb_energy_pred   = predict_xgboost(xgb_energy, test, ENERGY_FEATURES)
    t_xe = time.time() - t0; t0 = time.time()

    _, anl_density_pred = predict_analog_all_zones(
        analog_density, test, ANALOG_DENSITY_FEATURES, 'density_pct')
    t_ad = time.time() - t0; t0 = time.time()

    _, anl_energy_pred  = predict_analog_all_zones(
        analog_energy, test, ANALOG_ENERGY_FEATURES, 'energy_kwh')
    t_ae = time.time() - t0

    y_density = test['density_pct'].values.astype(np.float32)
    y_energy  = test['energy_kwh'].values.astype(np.float32)

    # ── Genel metrikler ───────────────────────────────────────────────────────
    print('\n[5/7] Metrikler hesaplanıyor...')

    overall: dict[str, dict[str, dict[str, float]]] = {
        'density': {
            'XGBoost':    compute_metrics(y_density, xgb_density_pred),
            'Analog kNN': compute_metrics(y_density, anl_density_pred),
        },
        'energy': {
            'XGBoost':    compute_metrics(y_energy, xgb_energy_pred),
            'Analog kNN': compute_metrics(y_energy, anl_energy_pred),
        },
    }

    # ── Ana tablo ─────────────────────────────────────────────────────────────
    print()
    print(f'  {"Model":<35} {"MAE":>7} {"RMSE":>7} {"R²":>7} {"MAPE":>8} {"Süre":>7}')
    print('  ' + '─' * 70)
    rows = [
        ('XGBoost Yoğunluk',   overall['density']['XGBoost'],    t_xd),
        ('Analog kNN Yoğunluk', overall['density']['Analog kNN'], t_ad),
        ('XGBoost Enerji',      overall['energy']['XGBoost'],     t_xe),
        ('Analog kNN Enerji',   overall['energy']['Analog kNN'],  t_ae),
    ]
    for name, m, t in rows:
        print(f'  {name:<35} {m["MAE"]:>7.4f} {m["RMSE"]:>7.4f}'
              f' {m["R2"]:>7.4f} {m["MAPE"]:>7.2f}% {t:>6.1f}s')

    # ── Zone bazlı metrikler ──────────────────────────────────────────────────
    zone_metrics: dict[str, dict[str, dict[str, dict[str, float]]]] = {
        'density': {'XGBoost': {}, 'Analog kNN': {}},
        'energy':  {'XGBoost': {}, 'Analog kNN': {}},
    }
    test_ri = test.reset_index(drop=True)

    for zone in ZONES:
        zid   = zone['id']
        zname = zone['name']
        mask  = test_ri['zone_id'] == zid
        if not mask.any():
            continue

        local = np.where(mask.values)[0]

        for tgt_key, y_true, xgb_pred, anl_pred in [
            ('density', y_density, xgb_density_pred, anl_density_pred),
            ('energy',  y_energy,  xgb_energy_pred,  anl_energy_pred),
        ]:
            zone_metrics[tgt_key]['XGBoost'][zname]    = compute_metrics(
                y_true[local], xgb_pred[local])
            zone_metrics[tgt_key]['Analog kNN'][zname] = compute_metrics(
                y_true[local], anl_pred[local])

    # ── Metin raporu (grafiklerden önce — plot hatası raporu silmesin) ────────
    print('\n[6/7] Karşılaştırma raporu yazılıyor...')
    write_text_report(overall, zone_metrics, DATA_DIR / 'comparison_report.txt')

    # ── Grafikler ────────────────────────────────────────────────────────────
    print('\n[7/7] Grafikler oluşturuluyor...')

    plot_scatter_and_residuals(
        y_density,
        {'XGBoost': xgb_density_pred, 'Analog kNN': anl_density_pred},
        'Yoğunluk (density_pct)', 'doluluk',
        DATA_DIR / 'eval_density_comparison.png',
    )
    plot_scatter_and_residuals(
        y_energy,
        {'XGBoost': xgb_energy_pred, 'Analog kNN': anl_energy_pred},
        'Enerji (energy_kwh)', 'kWh',
        DATA_DIR / 'eval_energy_comparison.png',
    )
    plot_zone_breakdown(zone_metrics, DATA_DIR / 'eval_zone_breakdown.png')
    plot_time_sample(
        test_ri, xgb_density_pred, anl_density_pred,
        zone_id=1,
        out_path=DATA_DIR / 'eval_time_sample.png',
    )

    print('\n  ✓ Tüm değerlendirmeler tamamlandı.')
    print(f'  Sonraki adım: python predict.py --zone 1 --datetime "2026-06-01 14:00" --target density')


if __name__ == '__main__':
    main()
