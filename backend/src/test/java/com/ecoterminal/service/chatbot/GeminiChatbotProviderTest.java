package com.ecoterminal.service.chatbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GeminiChatbotProvider Unit Tests")
class GeminiChatbotProviderTest {

    // ── isAvailable Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isAvailable_withEmptyApiKey_returnsFalse")
    void isAvailable_withEmptyApiKey_returnsFalse() {
        // given — API key yok
        GeminiChatbotProvider provider = new GeminiChatbotProvider(
                new RestTemplateBuilder(), "", "gemini-pro");

        // when / then
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("isAvailable_withValidApiKey_returnsTrue")
    void isAvailable_withValidApiKey_returnsTrue() {
        // given — API key mevcut
        GeminiChatbotProvider provider = new GeminiChatbotProvider(
                new RestTemplateBuilder(), "AIza-test-key-12345", "gemini-pro");

        // when / then
        assertThat(provider.isAvailable()).isTrue();
    }

    // ── Metadata Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProviderName_returnsCloud")
    void getProviderName_returnsCloud() {
        GeminiChatbotProvider provider = new GeminiChatbotProvider(
                new RestTemplateBuilder(), "", "gemini-pro");
        assertThat(provider.getProviderName()).isEqualTo("cloud");
    }

    @Test
    @DisplayName("getDisplayName_returnsNonBlank")
    void getDisplayName_returnsNonBlank() {
        GeminiChatbotProvider provider = new GeminiChatbotProvider(
                new RestTemplateBuilder(), "", "gemini-pro");
        assertThat(provider.getDisplayName()).isNotBlank();
    }

    // ── generateResponse Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("generateResponse_withNoApiKey_returnsFallbackMessage")
    void generateResponse_withNoApiKey_returnsFallbackMessage() {
        // given — API key yok → isAvailable() == false → fallback mesaj döner
        GeminiChatbotProvider provider = new GeminiChatbotProvider(
                new RestTemplateBuilder(), "", "gemini-pro");
        ChatContext ctx = new ChatContext(null, null, null, null, null, 0, null, 0L);

        // when
        String result = provider.generateResponse("Terminal soru", ctx);

        // then — fallback mesaj dönmeli, exception atılmamalı
        assertThat(result).isNotBlank();
    }
}
