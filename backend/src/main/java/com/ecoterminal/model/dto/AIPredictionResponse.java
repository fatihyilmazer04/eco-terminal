package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.AIPrediction;

import java.time.Instant;

/**
 * AI tahmin verisi — hem AI servisten gelen JSON'u hem DB entity'sini temsil eder.
 */
public record AIPredictionResponse(
    Long zoneId,
    String zoneName,
    Instant forecastTime,
    Float predictedLoad,
    Float densityPct,
    String riskLevel,
    String trend,
    Float confidence,
    Instant generatedAt
) {
    public static AIPredictionResponse from(AIPrediction p) {
        return new AIPredictionResponse(
            p.getZone().getZoneId(),
            p.getZone().getZoneName(),
            p.getForecastTime(),
            p.getPredictedLoad(),
            p.getDensityPct(),
            p.getRiskLevel(),
            p.getTrend(),
            p.getConfidence(),
            p.getGeneratedAt()
        );
    }
}
