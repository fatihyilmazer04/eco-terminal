package com.ecoterminal.model.dto;

import java.util.List;

/**
 * GET /api/ai/predictions/zone-forecast yanıtı.
 * type=OCCUPANCY → shortTerm/longTerm dolu, dataPoints null
 * type=ENERGY    → dataPoints dolu, shortTerm/longTerm boş
 */
public record ZoneForecastResponse(
        Long zoneId,
        String zoneName,
        String currentRisk,
        double currentLoad,
        List<ForecastDataPoint> shortTerm,
        List<ForecastDataPoint> longTerm,
        String modelConfidence,
        String recommendation,
        List<EnergyForecastPoint> dataPoints   // ENERGY tipinde dolu, OCCUPANCY'de null
) {}
