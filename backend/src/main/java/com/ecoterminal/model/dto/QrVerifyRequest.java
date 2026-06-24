package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/routes/verify-qr için istek gövdesi.
 *
 * scannedToken: QR koddan okunan token (örn. "SEC1-A3F2B1")
 * expectedZoneName: rota adımının beklediği zone adı (örn. "Security-1")
 */
public record QrVerifyRequest(
        @NotBlank(message = "QR token zorunludur")
        String scannedToken,

        @NotBlank(message = "Beklenen zone adı zorunludur")
        String expectedZoneName
) {}
