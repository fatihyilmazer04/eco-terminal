package com.ecoterminal.model.dto;

/**
 * GET /api/admin/zones/qr-codes yanıt elemanı.
 *
 * qrContent: QR koda kodlanacak JSON string.
 *   Format: {"token":"SEC1-A3F2B1","name":"Security-1","zoneId":2}
 *   Frontend bu string'i qrcode.react'a vererek QR görüntüler.
 */
public record ZoneQrResponse(
        Long   zoneId,
        String zoneName,
        String zoneType,
        String qrToken,
        String qrContent
) {}
