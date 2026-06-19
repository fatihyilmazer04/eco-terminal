package com.ecoterminal.model.dto;

/**
 * POST /api/routes/verify-qr yanıtı.
 *
 * verified:  true → QR geçerli ve doğru zone'a ait
 * zoneName:  QR'ın ait olduğu zone adı (doğru veya yanlış zone olsun)
 * message:   kullanıcıya gösterilecek Türkçe mesaj
 */
public record QrVerifyResponse(
        boolean verified,
        String  zoneName,
        String  message
) {}
