package com.ecoterminal.model.dto;

import java.time.Instant;

/**
 * POST /api/occupancy/redirect yanıtı.
 */
public record RedirectResponse(
        Long fromZoneId,
        String fromZoneName,
        Long toZoneId,
        String toZoneName,
        String message,
        int notificationsSent,
        Instant timestamp
) {}
