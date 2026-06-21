package com.ecoterminal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmService Unit Tests")
class FcmServiceTest {

    // Firebase null → simülasyon modu
    private final FcmService fcmService = new FcmService(null);

    // ── sendToToken Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("sendToToken_withSimulationMode_returnsTrue")
    void sendToToken_withSimulationMode_returnsTrue() {
        // when
        boolean result = fcmService.sendToToken("valid-fcm-token-12345", "Test Başlık", "Test mesajı");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("sendToToken_withNullToken_returnsFalse")
    void sendToToken_withNullToken_returnsFalse() {
        // when
        boolean result = fcmService.sendToToken(null, "Başlık", "Mesaj");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("sendToToken_withBlankToken_returnsFalse")
    void sendToToken_withBlankToken_returnsFalse() {
        // when
        boolean result = fcmService.sendToToken("   ", "Başlık", "Mesaj");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("sendToToken_withEmptyToken_returnsFalse")
    void sendToToken_withEmptyToken_returnsFalse() {
        // when
        boolean result = fcmService.sendToToken("", "Başlık", "Mesaj");

        // then
        assertThat(result).isFalse();
    }

    // ── sendToTopic Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("sendToTopic_withSimulationMode_returnsTrue")
    void sendToTopic_withSimulationMode_returnsTrue() {
        // when
        boolean result = fcmService.sendToTopic("zone_10_alerts", "Yoğunluk Uyarısı", "Gate A1 dolu");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("sendToTopic_withAnyTopicName_returnsTrue")
    void sendToTopic_withAnyTopicName_returnsTrue() {
        // when
        boolean result = fcmService.sendToTopic("general-announcements", "Duyuru", "Terminal kapanıyor");

        // then
        assertThat(result).isTrue();
    }
}
