"""
Eco-Terminal AI Service — Risk seviyesi ve trend hesaplama.
"""
from config import LOW_THRESHOLD, HIGH_THRESHOLD


def calculate_risk_level(predicted_density: float) -> str:
    """
    Tahmin edilen yoğunluğa göre risk seviyesi.
    < 0.60  → "LOW"
    0.60-0.84 → "MEDIUM"
    >= 0.85 → "HIGH"
    """
    if predicted_density >= HIGH_THRESHOLD:
        return "HIGH"
    if predicted_density >= LOW_THRESHOLD:
        return "MEDIUM"
    return "LOW"


def calculate_trend(history: list) -> str:
    """
    Son 5 yoğunluk değerinin trendini hesaplar.
    Artış > +0.03 → "INCREASING"
    Azalış < -0.03 → "DECREASING"
    Değişim -0.03 ile +0.03 arası → "STABLE"

    Args:
        history: density_pct değerlerinin listesi (kronolojik sıra)
    """
    if len(history) < 2:
        return "STABLE"

    recent = history[-5:]  # Son 5 değer
    if len(recent) < 2:
        return "STABLE"

    delta = recent[-1] - recent[0]

    if delta > 0.03:
        return "INCREASING"
    if delta < -0.03:
        return "DECREASING"
    return "STABLE"


def calculate_confidence(history_length: int) -> float:
    """
    Geçmişe veri miktarına göre güven skoru.
    60 veya daha fazla okuma → %90 güven
    Az veri → daha düşük güven
    """
    if history_length >= 60:
        return 0.90
    if history_length >= 30:
        return 0.78
    if history_length >= 10:
        return 0.65
    return 0.50
