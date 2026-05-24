package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.Zone;

/**
 * Zone eşik yönetimi için DTO — Sistem Ayarları / Bölge Eşikleri sekmesi.
 */
public record ZoneSettingsResponse(
        Long    zoneId,
        String  zoneName,
        String  zoneType,
        Integer maxCapacity,
        Float   criticalThreshold,
        String  status
) {
    public static ZoneSettingsResponse from(Zone z) {
        return new ZoneSettingsResponse(
                z.getZoneId(),
                z.getZoneName(),
                z.getType().name(),
                z.getMaxCapacity(),
                z.getCriticalThreshold(),
                z.getStatus().name()
        );
    }
}
