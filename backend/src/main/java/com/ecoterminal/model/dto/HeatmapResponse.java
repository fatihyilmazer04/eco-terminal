package com.ecoterminal.model.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/occupancy/heatmap — tüm bölgeler + meta */
public record HeatmapResponse(
        List<ZoneOccupancyResponse> zones,
        int totalZones,
        int criticalZoneCount,
        int totalPeople,
        Instant generatedAt
) {
    public static HeatmapResponse of(List<ZoneOccupancyResponse> zones) {
        int critical = (int) zones.stream().filter(ZoneOccupancyResponse::isCritical).count();
        int total    = zones.stream().mapToInt(ZoneOccupancyResponse::currentCount).sum();
        return new HeatmapResponse(zones, zones.size(), critical, total, Instant.now());
    }
}
