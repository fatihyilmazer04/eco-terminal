#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
analog_model.py
===============
ZoneAnalogModel sınıfı — tek bir zone için Analog kNN modeli.

Bu dosya train_analog.py, evaluate_models.py ve predict.py tarafından
import edilir. joblib.load() sırasında sınıf tanımının burada olması
gerekir; aksi takdirde "Can't get attribute 'ZoneAnalogModel'" hatası alınır.
"""

from __future__ import annotations

import numpy as np
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import StandardScaler

K_NEIGHBORS = 5   # varsayılan komşu sayısı


class ZoneAnalogModel:
    """
    Tek bir zone için Analog Ensemble (kNN) modeli.

    Eğitim (fit):
      Geçmiş verileri StandardScaler ile ölçekler ve BallTree indeksi oluşturur.
      Ağır hesaplama burada yapılır; tahmin çok hızlıdır.

    Tahmin (predict):
      Sorgu noktasına en benzer k örnekler bulunur.
      Sonuç: ters-mesafe ağırlıklı ortalama (yakın komşuya daha fazla ağırlık).

    joblib.dump / joblib.load ile serialize edilebilir.
    """

    def __init__(self, zone_id: int, target: str, k: int = K_NEIGHBORS) -> None:
        self.zone_id   = zone_id
        self.target    = target   # 'density' veya 'energy'
        self.k         = k
        self.scaler    = StandardScaler()
        self.nn        = NearestNeighbors(
            n_neighbors = k,
            algorithm   = 'ball_tree',
            metric      = 'euclidean',
            n_jobs      = -1,
        )
        self.y_train: np.ndarray | None = None
        self._n_train: int = 0

    # ── Eğitim (indexleme) ────────────────────────────────────────────────────

    def fit(self, X: np.ndarray, y: np.ndarray) -> 'ZoneAnalogModel':
        X_sc          = self.scaler.fit_transform(X.astype(np.float64))
        self.nn.fit(X_sc)
        self.y_train  = y.astype(np.float32)
        self._n_train = len(y)
        return self

    # ── Tahmin ────────────────────────────────────────────────────────────────

    def predict(self, X: np.ndarray) -> np.ndarray:
        X_sc = self.scaler.transform(X.astype(np.float64))
        distances, indices = self.nn.kneighbors(X_sc)
        weights = 1.0 / (distances + 1e-8)
        weights /= weights.sum(axis=1, keepdims=True)
        return (weights * self.y_train[indices]).sum(axis=1).astype(np.float32)

    def __repr__(self) -> str:
        return (f'ZoneAnalogModel(zone_id={self.zone_id}, target={self.target!r}, '
                f'k={self.k}, n_train={self._n_train:,})')
