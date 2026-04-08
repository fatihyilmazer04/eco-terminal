package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.ZoneType;

import java.time.Instant;

/**
 * Bölge bilgisi + anlık doluluk verisi.
 * HeatmapPage ve PassengerDashboard tarafından kullanılır.
 */
public record ZoneOccupancyResponse(
        Long zoneId,
        String zoneName,
        String type,
        Integer maxCapacity,
        Float criticalThreshold,
        Integer currentCount,
        Float densityPct,
        DensityLevel densityLevel,
        String colorCode,
        Instant lastUpdated
) {
    /** Hiç okuma yoksa sıfır değerlerle döner */
    public static ZoneOccupancyResponse empty(Long zoneId, String zoneName,
                                               ZoneType type, Integer maxCapacity,
                                               Float criticalThreshold) {
        return new ZoneOccupancyResponse(
                zoneId, zoneName, type.name(), maxCapacity, criticalThreshold,
                0, 0.0f, DensityLevel.LOW, DensityLevel.LOW.getColorCode(), null
        );
    }

    public boolean isCritical() {
        return densityLevel == DensityLevel.CRITICAL || densityLevel == DensityLevel.HIGH;
    }
}
