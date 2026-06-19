"""
Eco-Terminal AI Service — XGBoost Yoğunluk Tahmin Modeli.

Faz 2'de ml-pipeline/ ile eğitilen xgboost_density.pkl ve
xgboost_energy.pkl dosyalarını yükler.

Volume mount (docker-compose.yml):
  ./ml-pipeline/models:/app/models:ro

Singleton: modül düzeyinde xgb_predictor = XGBoostPredictor() ile
oluşturulur; uygulama başlangıcında bir kez yüklenir.

Fallback: is_available=False ise routes/predict.py otomatik olarak
LSTM/ağırlıklı-ortalama moduna geçer.
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

# ── Model dosya yolları ───────────────────────────────────────────────────────
MODELS_DIR = Path('/app/models')

# ── Feature listeleri — ml-pipeline/feature_engineering.py ile BİREBİR AYNI SIRA
# DENSITY_FEATURES: 27 sütun
DENSITY_FEATURES = [
    'year', 'month', 'day', 'hour', 'minute', 'dow',
    'is_weekend', 'is_holiday',
    'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos', 'month_sin', 'month_cos',
    'zone_id', 'capacity',
    'zone_type_GATE', 'zone_type_SECURITY', 'zone_type_CHECKIN', 'zone_type_LOUNGE',
    'density_lag_1', 'density_lag_4', 'density_lag_96', 'density_lag_672',
    'energy_lag_1',  'energy_lag_4',  'energy_lag_96',
]

# ENERGY_FEATURES: 27 sütun (density_lag_672 YOK, energy_lag_672 VAR)
ENERGY_FEATURES = [
    'year', 'month', 'day', 'hour', 'minute', 'dow',
    'is_weekend', 'is_holiday',
    'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos', 'month_sin', 'month_cos',
    'zone_id', 'capacity',
    'zone_type_GATE', 'zone_type_SECURITY', 'zone_type_CHECKIN', 'zone_type_LOUNGE',
    'density_lag_1', 'density_lag_4', 'density_lag_96',
    'energy_lag_1',  'energy_lag_4',  'energy_lag_96', 'energy_lag_672',
]

# ── Tatil tarihleri — feature_engineering.py ile aynı mantık ─────────────────
_HOLIDAY_PERIODS = [
    # Ramazan Bayramı
    (2016, 7,  5, 7,  8), (2017, 6, 25, 6, 28), (2018, 6, 14, 6, 17),
    (2019, 6,  4, 6,  7), (2020, 5, 23, 5, 26), (2021, 5, 13, 5, 16),
    (2022, 5,  2, 5,  5), (2023, 4, 21, 4, 24), (2024, 4, 10, 4, 13),
    (2025, 3, 30, 4,  2), (2026, 3, 20, 3, 23),
    # Kurban Bayramı
    (2016, 9, 12, 9, 16), (2017, 9,  1, 9,  5), (2018, 8, 21, 8, 25),
    (2019, 8, 11, 8, 15), (2020, 7, 31, 8,  4), (2021, 7, 20, 7, 24),
    (2022, 7,  9, 7, 13), (2023, 6, 28, 7,  2), (2024, 6, 17, 6, 21),
    (2025, 6,  6, 6, 10),
]


def _build_holiday_dates() -> set:
    dates: set = set()
    for yr, sm, sd, em, ed in _HOLIDAY_PERIODS:
        cur = pd.Timestamp(f'{yr}-{sm:02d}-{sd:02d}')
        end = pd.Timestamp(f'{yr}-{em:02d}-{ed:02d}')
        while cur <= end:
            dates.add(cur.date())
            cur += pd.Timedelta(days=1)
    for yr in range(2016, 2030):
        for d in range(24, 32):   # 24–31 (Aralık sonları)
            try:
                dates.add(pd.Timestamp(f'{yr}-12-{d:02d}').date())
            except Exception:
                pass
        for d in range(1, 8):    # 1–7 (Yılbaşı haftası)
            dates.add(pd.Timestamp(f'{yr}-01-{d:02d}').date())
    return dates


_HOLIDAY_DATES: set = _build_holiday_dates()


# ── DB'den lag değerleri ──────────────────────────────────────────────────────

def _get_lag_values(zone_id: int) -> dict[str, float]:
    """
    occupancy_readings (density) ve environmental_metrics (energy)
    tablolarından geriye dönük lag değerlerini çeker.

    Lag adımları (15-dakikalık aralıkta):
      lag_1   → 15 dk önce
      lag_4   → 1 saat önce
      lag_96  → 24 saat önce
      lag_672 → 1 hafta önce

    Yeterli kayıt yoksa mevcut kayıtların ortalaması, hiç kayıt yoksa
    varsayılan değer kullanılır.
    """
    _DEF_DENSITY = 0.45   # Veri yoksa makul bir havalimanı lounge/gate doluluk tahmini
    _DEF_ENERGY  = 5.0

    density_rows: list[float] = []
    energy_rows:  list[float] = []

    try:
        from database import get_connection

        with get_connection() as conn:
            with conn.cursor() as cur:
                # Son 700 doluluk okuması (DESC → en yeni önce)
                cur.execute(
                    """
                    SELECT density_pct
                    FROM occupancy_readings
                    WHERE zone_id = %s
                    ORDER BY recorded_at DESC
                    LIMIT 700
                    """,
                    (zone_id,),
                )
                density_rows = [float(r[0]) for r in cur.fetchall()]

                # Son 700 enerji ölçümü (DESC → en yeni önce)
                cur.execute(
                    """
                    SELECT energy_kwh
                    FROM environmental_metrics
                    WHERE zone_id = %s
                    ORDER BY recorded_at DESC
                    LIMIT 700
                    """,
                    (zone_id,),
                )
                energy_rows = [float(r[0]) for r in cur.fetchall()]

    except Exception as exc:
        logger.warning("Lag değerleri çekilemedi (zone=%d): %s", zone_id, exc)

    def _lag(rows: list[float], n: int, default: float) -> float:
        """DESC sıralı listede rows[n-1] = n adım önceki değer."""
        if len(rows) >= n:
            return rows[n - 1]
        return float(np.mean(rows)) if rows else default

    def _lag_672_density(rows: list[float]) -> float:
        """
        lag_672 (1 hafta önce) için özel fallback:
        - Yeterli veri varsa (≥672): gerçek 1-hafta-önceki değer
        - Yetersiz veri: son 96 okumanın ortalaması (son 24 saat)
          → mevcut günün trendini referans alır, eski simülasyon
            ortalamalarından etkilenmez
        - Hiç veri yoksa: varsayılan
        """
        if len(rows) >= 672:
            return rows[671]
        if len(rows) > 0:
            recent_96 = rows[:min(96, len(rows))]
            return float(np.mean(recent_96))
        return _DEF_DENSITY

    return {
        'density_lag_1':   _lag(density_rows, 1,   _DEF_DENSITY),
        'density_lag_4':   _lag(density_rows, 4,   _DEF_DENSITY),
        'density_lag_96':  _lag(density_rows, 96,  _DEF_DENSITY),
        'density_lag_672': _lag_672_density(density_rows),
        'energy_lag_1':    _lag(energy_rows,  1,   _DEF_ENERGY),
        'energy_lag_4':    _lag(energy_rows,  4,   _DEF_ENERGY),
        'energy_lag_96':   _lag(energy_rows,  96,  _DEF_ENERGY),
        'energy_lag_672':  _lag(energy_rows,  672, _DEF_ENERGY),
    }


# ── Feature dict oluşturma ────────────────────────────────────────────────────

def _build_feature_dict(
    zone_id:   int,
    dt:        datetime,
    zone_type: str,
    capacity:  int,
    lag:       dict[str, float],
) -> dict[str, float]:
    """
    Tek tahmin noktası için feature dict oluşturur.
    feature_engineering.py::prepare_dataset() ile aynı hesaplamalar.
    """
    dow = dt.weekday()   # 0=Pzt … 6=Paz
    return {
        'year':    dt.year,
        'month':   dt.month,
        'day':     dt.day,
        'hour':    dt.hour,
        'minute':  dt.minute,
        'dow':     dow,
        'is_weekend':  int(dow >= 5),
        'is_holiday':  int(dt.date() in _HOLIDAY_DATES),
        'hour_sin':    float(np.sin(2 * np.pi * dt.hour  / 24)),
        'hour_cos':    float(np.cos(2 * np.pi * dt.hour  / 24)),
        'dow_sin':     float(np.sin(2 * np.pi * dow       / 7)),
        'dow_cos':     float(np.cos(2 * np.pi * dow       / 7)),
        'month_sin':   float(np.sin(2 * np.pi * dt.month / 12)),
        'month_cos':   float(np.cos(2 * np.pi * dt.month / 12)),
        'zone_id':     zone_id,
        'capacity':    capacity,
        'zone_type_GATE':     int(zone_type == 'GATE'),
        'zone_type_SECURITY': int(zone_type == 'SECURITY'),
        'zone_type_CHECKIN':  int(zone_type == 'CHECKIN'),
        'zone_type_LOUNGE':   int(zone_type == 'LOUNGE'),
        **lag,
    }


# ── Ana sınıf ─────────────────────────────────────────────────────────────────

class XGBoostPredictor:
    """
    Faz 2 XGBoost modellerini (density + energy) yükler ve
    Spring Boot sözleşmesiyle uyumlu tahminler üretir.

    Attributes
    ----------
    is_available : bool
        True → modeller başarıyla yüklendi.
        False → fallback moda geç (routes/predict.py bunu kontrol eder).
    """

    def __init__(self) -> None:
        self.density_model = None
        self.energy_model  = None
        self.is_available  = False
        self._load_models()

    # ── Model yükleme ─────────────────────────────────────────────────────────

    def _load_models(self) -> None:
        try:
            import joblib
        except ImportError:
            logger.error(
                "joblib kurulu değil — 'pip install joblib' çalıştırın. Fallback aktif."
            )
            return

        density_path = MODELS_DIR / 'xgboost_density.pkl'
        energy_path  = MODELS_DIR / 'xgboost_energy.pkl'

        missing = [p.name for p in (density_path, energy_path) if not p.exists()]
        if missing:
            logger.warning(
                "XGBoost dosyaları bulunamadı: %s  |  "
                "Volume mount kontrolü: ./ml-pipeline/models:/app/models:ro  |  "
                "Fallback (ağırlıklı ortalama) aktif.",
                missing,
            )
            return

        try:
            self.density_model = joblib.load(density_path)
            self.energy_model  = joblib.load(energy_path)
            self.is_available  = True
            logger.info(
                "XGBoost modelleri yüklendi: density (%.1f MB) + energy (%.1f MB)  →  %s",
                density_path.stat().st_size / 1_000_000,
                energy_path.stat().st_size  / 1_000_000,
                MODELS_DIR,
            )
        except Exception as exc:
            logger.error("XGBoost model yükleme hatası: %s  |  Fallback aktif.", exc)

    # ── Tek zone tahmini ──────────────────────────────────────────────────────

    def predict(self, zone_id: int, next_minutes: int = 30) -> dict:
        """
        Tek zone için Spring Boot sözleşmesiyle uyumlu tahmin dict'i döndürür.

        Döndürülen 9 alan (AIPredictionClient.AiRawPrediction ile eşleşmeli):
          zone_id, zone_name, forecast_time, predicted_load, density_pct,
          risk_level, trend, confidence, generated_at

        Raises
        ------
        RuntimeError : model yüklü değilse
        ValueError   : zone DB'de bulunamazsa veya aktif değilse
        """
        if not self.is_available:
            raise RuntimeError("XGBoost modeli mevcut değil (is_available=False)")

        from database import get_zone_info
        from model.risk_calculator import calculate_risk_level, calculate_trend

        # ── Zone bilgisi ──────────────────────────────────────────────────────
        zone_info = get_zone_info(zone_id)
        if zone_info is None:
            raise ValueError(f"Zone {zone_id} DB'de bulunamadı veya aktif değil")

        zone_name = zone_info['zone_name']
        zone_type = zone_info['type']                   # 'GATE'|'SECURITY'|'CHECKIN'|'LOUNGE'
        capacity  = int(zone_info.get('max_capacity') or 200)

        # ── Zaman ─────────────────────────────────────────────────────────────
        now           = datetime.now(timezone.utc)
        forecast_time = now + timedelta(minutes=next_minutes)

        # ── Lag değerleri ─────────────────────────────────────────────────────
        lag = _get_lag_values(zone_id)

        # ── Feature satırı ────────────────────────────────────────────────────
        feat = _build_feature_dict(zone_id, now, zone_type, capacity, lag)

        # ── XGBoost tahmini (density) ─────────────────────────────────────────
        X_d = np.array(
            [[feat[col] for col in DENSITY_FEATURES]], dtype=np.float32
        )
        predicted_density = float(np.clip(self.density_model.predict(X_d)[0], 0.0, 1.0))

        # ── Risk + trend + confidence ─────────────────────────────────────────
        risk_level = calculate_risk_level(predicted_density)

        # Trend: lag değerlerinden kronolojik sıra (eski → yeni → tahmin)
        density_history = [
            lag['density_lag_96'],
            lag['density_lag_4'],
            lag['density_lag_1'],
            predicted_density,
        ]
        trend = calculate_trend(density_history)

        return {
            "zone_id":        zone_id,
            "zone_name":      zone_name,
            "forecast_time":  forecast_time.strftime("%Y-%m-%dT%H:%M:%S"),
            "predicted_load": round(predicted_density, 4),
            "density_pct":    round(predicted_density, 4),
            "risk_level":     risk_level,
            "trend":          trend,
            "confidence":     0.95,   # XGBoost: yüksek güven skoru
            "generated_at":   now.strftime("%Y-%m-%dT%H:%M:%S"),
        }

    # ── Toplu tahmin ──────────────────────────────────────────────────────────

    def predict_all(self, next_minutes: int = 30) -> list[dict]:
        """
        Tüm aktif zone'lar için tahmin listesi döndürür.
        Hata veren zone'lar listeye dahil edilmez (diğerleri etkilenmez).
        """
        from database import get_all_active_zones

        zones   = get_all_active_zones()
        results = []
        for zone in zones:
            zid = zone.get('zone_id')
            try:
                results.append(self.predict(zid, next_minutes))
            except Exception as exc:
                logger.warning(
                    "XGBoost tahmin hatası (zone=%s): %s — zone atlandı", zid, exc
                )
        return results


# ── Singleton — uygulama boyunca tek instance, bir kez yüklenir ──────────────
xgb_predictor = XGBoostPredictor()
