package com.ecoterminal.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tüm terminal heatmap verisi — /api/heatmap/live endpoint'inden döner.
 * Zone detayları, özet sayılar, AI önerisi içerir.
 */
public record HeatmapSummaryResponse(
        int totalZones,
        int fullCount,
        int busyCount,
        int moderateCount,
        int emptyCount,
        List<ZoneCrowdStatusResponse> zones,
        List<String> alertZones,        // FULL olan zone isimleri (kırmızı uyarı)
        List<String> suggestedZones,    // EMPTY/MODERATE alternatifler
        String aiSummary,               // Türkçe özet cümle
        LocalDateTime timestamp
) {}
