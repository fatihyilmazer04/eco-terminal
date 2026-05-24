package com.ecoterminal.model.dto;

/**
 * Tek bir zaman dilimine ait tahmin noktası — ZoneForecastResponse için.
 */
public record ForecastDataPoint(
        String time,            // "HH:mm" veya "gün+saat" formatı
        double predictedLoad,   // 0.0–1.0
        String riskLevel,       // LOW | MEDIUM | HIGH
        double confidence       // 0.0–1.0
) {}
