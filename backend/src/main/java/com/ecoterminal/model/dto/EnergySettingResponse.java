package com.ecoterminal.model.dto;

import java.time.Instant;

/**
 * PATCH /api/energy/zones/{id}/settings yanıtı.
 */
public record EnergySettingResponse(
        Long zoneId,
        String zoneName,
        Float previousTemp,
        Float newTemp,
        Integer previousLightingLux,
        Integer newLightingLux,
        String message,
        Instant updatedAt
) {}
