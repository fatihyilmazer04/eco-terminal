package com.ecoterminal.model.dto;

/**
 * GET /api/stats/cameras yanıtında dönen kamera/IoT durum bilgisi.
 */
public record CameraStatusResponse(
        Long deviceId,
        String serialNumber,
        String zoneName,
        String deviceType,
        String status,
        String firmwareVer
) {}
