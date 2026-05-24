package com.ecoterminal.model.dto;

import java.util.List;

/**
 * GET /api/ai/zone-forecast yanıtı — seçilen bölge için çok noktalı tahmin.
 */
public record ZoneForecastResponse(
        Long zoneId,
        String zoneName,
        String currentRisk,
        double currentLoad,
        List<ForecastDataPoint> shortTerm,   // sonraki 30, 60, 120 dk
        List<ForecastDataPoint> longTerm,    // sonraki 6, 12, 24 saat
        String modelConfidence,
        String recommendation
) {}
