package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.model.entity.ZoneType;

/** GET /api/zones — saf bölge meta verisi, anlık doluluk yok */
public record ZoneResponse(
        Long zoneId,
        String zoneName,
        ZoneType type,
        Integer maxCapacity,
        Float criticalThreshold,
        String geoCoords,
        Integer floorLevel,
        ZoneStatus status
) {
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(
                zone.getZoneId(),
                zone.getZoneName(),
                zone.getType(),
                zone.getMaxCapacity(),
                zone.getCriticalThreshold(),
                zone.getGeoCoords(),
                zone.getFloorLevel(),
                zone.getStatus()
        );
    }
}
