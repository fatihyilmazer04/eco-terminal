package com.ecoterminal.model.dto;

import java.time.Instant;

/**
 * Sistem geneli istatistik özeti — Sistem Ayarları sayfası için.
 */
public record SystemStatsResponse(
        int    totalZones,
        int    activeZones,
        long   totalReadings,
        long   totalUsers,
        int    totalDevices,
        int    onlineDevices,
        String backendVersion,
        String javaVersion,
        Instant timestamp
) {}
