"""
Eco-Terminal AI Service — LSTM Yoğunluk Tahmin Modeli.

Üretim modu: TensorFlow Sequential LSTM (eğitilmiş ağırlık gerektir).
Fallback modu: Son N okumanın ağırlıklı ortalaması + küçük sapma.
Şu an fallback modu aktif (gerçek eğitim verisi henüz yok).
"""
import logging
import random
import numpy as np
from config import SEQUENCE_LENGTH

logger = logging.getLogger(__name__)

# TensorFlow opsiyonel — kurulu değilse fallback devreye girer
try:
    import tensorflow as tf
    from tensorflow.keras.models import Sequential
    from tensorflow.keras.layers import LSTM, Dense, Dropout
    TF_AVAILABLE = True
except ImportError:
    TF_AVAILABLE = False
    logger.warning("TensorFlow bulunamadı, fallback modu aktif.")


class LSTMPredictor:
    """
    LSTM tabanlı yoğunluk tahmin modeli.
    TensorFlow yoksa ya da model eğitilmemişse fallback_predict() çalışır.
    """

    def __init__(self):
        self.model = None
        self.is_trained = False
        self._build_model()

    def _build_model(self):
        """
        Sequential LSTM mimarisi: 64 → Dropout 0.2 → 32 → Dense 1
        Çıkış: 0.0-1.0 arası density_pct tahmini
        """
        if not TF_AVAILABLE:
            logger.info("TF yok, model oluşturulmadı. Fallback modu.")
            return
        try:
            self.model = Sequential([
                LSTM(64, input_shape=(SEQUENCE_LENGTH, 2), return_sequences=True),
                Dropout(0.2),
                LSTM(32, return_sequences=False),
                Dense(1, activation='sigmoid')
            ])
            self.model.compile(optimizer='adam', loss='mse', metrics=['mae'])
            logger.info("LSTM modeli oluşturuldu (eğitimsiz). Fallback tahmin aktif.")
        except Exception as e:
            logger.error("Model oluşturma hatası: %s", e)
            self.model = None

    def predict(self, sequence: np.ndarray) -> float:
        """
        Sequence'ten density_pct tahmini üretir.
        Eğer model eğitilmemişse fallback_predict() kullanılır.

        Args:
            sequence: shape (n, 2) — [people_count, density_pct] zaman serisi
        Returns:
            Tahmin edilen density_pct (0.0-1.0)
        """
        if not self.is_trained or self.model is None:
            return self.fallback_predict(sequence)

        try:
            # SEQUENCE_LENGTH uzunluğuna pad veya kırp
            padded = self._prepare_sequence(sequence)
            x = padded.reshape(1, SEQUENCE_LENGTH, 2)
            result = float(self.model.predict(x, verbose=0)[0][0])
            return float(np.clip(result, 0.0, 1.0))
        except Exception as e:
            logger.warning("LSTM tahmin hatası, fallback'e geçiliyor: %s", e)
            return self.fallback_predict(sequence)

    def fallback_predict(self, sequence: np.ndarray) -> float:
        """
        Fallback tahmin: son 5 density değerinin ağırlıklı ortalaması + küçük sapma.
        Ağırlıklar: en yeni okuma en ağır (5, 4, 3, 2, 1).
        Sapma: ±0.05 rastgele eklenir (simülasyon gerçekçiliği için).
        """
        if sequence is None or len(sequence) == 0:
            return 0.5 + random.uniform(-0.05, 0.05)

        # Son 5 density değeri (sütun index 1)
        densities = sequence[-5:, 1] if sequence.shape[1] > 1 else sequence[-5:, 0]
        n = len(densities)
        weights = np.arange(1, n + 1, dtype=float)   # [1, 2, ..., n]
        weighted_avg = float(np.average(densities, weights=weights))

        # Küçük rastgele sapma (±0.05)
        noise = random.uniform(-0.05, 0.05)
        prediction = float(np.clip(weighted_avg + noise, 0.0, 1.0))
        return prediction

    def _prepare_sequence(self, sequence: np.ndarray) -> np.ndarray:
        """Sequence'i SEQUENCE_LENGTH boyutuna getirir."""
        if len(sequence) >= SEQUENCE_LENGTH:
            return sequence[-SEQUENCE_LENGTH:]
        # Pad: başa sıfır satırları ekle
        pad = np.zeros((SEQUENCE_LENGTH - len(sequence), 2), dtype=np.float32)
        return np.vstack([pad, sequence])


# Singleton — uygulama boyunca tek instance
predictor = LSTMPredictor()
