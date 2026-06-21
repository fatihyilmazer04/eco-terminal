package com.ecoterminal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * EmailService unit testleri.
 * Demo modunda (mailEnabled=false veya apiKey boş) gerçek API çağrısı yapılmaz;
 * sadece log yazılır — dolayısıyla exception fırlatılmamalı.
 */
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    // ── sendVerificationCode Tests ────────────────────────────────────────────

    @Test
    @DisplayName("sendVerificationCode_withDemoMode_doesNotThrow")
    void sendVerificationCode_withDemoMode_doesNotThrow() {
        // given — mailEnabled=false → demo modu
        EmailService service = buildDemoService();

        // when / then
        assertDoesNotThrow(() ->
                service.sendVerificationCode("test@example.com", "123456"));
    }

    @Test
    @DisplayName("sendVerificationCode_withEmptyApiKey_doesNotThrow")
    void sendVerificationCode_withEmptyApiKey_doesNotThrow() {
        // given — mailEnabled=true ama apiKey boş → demo modu
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@ecoterminal.com");
        ReflectionTestUtils.setField(service, "mailEnabled", true);

        // when / then — hata fırlatılmamalı (demo moduna düşer)
        assertDoesNotThrow(() ->
                service.sendVerificationCode("test@example.com", "654321"));
    }

    // ── sendPasswordResetCode Tests ───────────────────────────────────────────

    @Test
    @DisplayName("sendPasswordResetCode_withDemoMode_doesNotThrow")
    void sendPasswordResetCode_withDemoMode_doesNotThrow() {
        // given
        EmailService service = buildDemoService();

        // when / then
        assertDoesNotThrow(() ->
                service.sendPasswordResetCode("reset@example.com", "789012"));
    }

    @Test
    @DisplayName("sendPasswordResetCode_withNullEmail_doesNotThrow")
    void sendPasswordResetCode_withNullEmail_doesNotThrow() {
        // given — demo modunda null email de loglansın, exception atılmasın
        EmailService service = buildDemoService();

        // when / then
        assertDoesNotThrow(() ->
                service.sendPasswordResetCode(null, "000000"));
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private EmailService buildDemoService() {
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@ecoterminal.com");
        ReflectionTestUtils.setField(service, "mailEnabled", false);
        return service;
    }
}
