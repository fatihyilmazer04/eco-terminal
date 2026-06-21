package com.ecoterminal.service.chatbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmServiceProvider Unit Tests")
class LlmServiceProviderTest {

    private LlmServiceProvider buildProvider(String url, String token) {
        return new LlmServiceProvider(new RestTemplateBuilder(), url, token);
    }

    // ── Metadata Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProviderName_returnsLlmService")
    void getProviderName_returnsLlmService() {
        assertThat(buildProvider("http://llm-service:5002", "").getProviderName())
                .isEqualTo("llm-service");
    }

    @Test
    @DisplayName("getDisplayName_returnsNonBlank")
    void getDisplayName_returnsNonBlank() {
        assertThat(buildProvider("http://llm-service:5002", "").getDisplayName())
                .isNotBlank();
    }

    // ── isAvailable Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isAvailable_alwaysReturnsTrue")
    void isAvailable_alwaysReturnsTrue() {
        // LlmServiceProvider her zaman true döner; hata durumunda fallback mesaj kullanır
        assertThat(buildProvider("http://llm-service:5002", "secret").isAvailable()).isTrue();
        assertThat(buildProvider("http://localhost:5002", "").isAvailable()).isTrue();
    }

    // ── generateResponse Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("generateResponse_whenServiceUnreachable_returnsFallbackMessage")
    void generateResponse_whenServiceUnreachable_returnsFallbackMessage() {
        // given — LLM servisi erişilemez URL
        LlmServiceProvider provider = buildProvider("http://localhost:9999", "");
        ChatContext ctx = new ChatContext(null, null, null, null, null, 0, null, 0L);

        // when — bağlantı başarısız → fallback mesaj döner, exception fırlatılmaz
        String result = provider.generateResponse("Soru", ctx);

        // then
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("generateRichResponse_whenServiceUnreachable_returnsFallbackChatbotResponse")
    void generateRichResponse_whenServiceUnreachable_returnsFallbackChatbotResponse() {
        // given
        LlmServiceProvider provider = buildProvider("http://localhost:9999", "");
        ChatContext ctx = new ChatContext(null, null, null, null, null, 0, null, 0L);

        // when
        var result = provider.generateRichResponse("Soru", ctx);

        // then
        assertThat(result).isNotNull();
        assertThat(result.reply()).isNotBlank();
    }
}
