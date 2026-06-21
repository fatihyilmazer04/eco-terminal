package com.ecoterminal.service.chatbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalChatbotProvider Unit Tests")
class LocalChatbotProviderTest {

    private final LocalChatbotProvider provider = new LocalChatbotProvider();

    // ── Metadata Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProviderName_returnsLocal")
    void getProviderName_returnsLocal() {
        assertThat(provider.getProviderName()).isEqualTo("local");
    }

    @Test
    @DisplayName("getDisplayName_returnsNonBlank")
    void getDisplayName_returnsNonBlank() {
        assertThat(provider.getDisplayName()).isNotBlank();
    }

    // ── isAvailable Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isAvailable_returnsFalse_whenLocalModelNotReady")
    void isAvailable_returnsFalse_whenLocalModelNotReady() {
        // Local model henüz hazır değil — placeholder
        assertThat(provider.isAvailable()).isFalse();
    }

    // ── generateResponse Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("generateResponse_returnsNonBlankMessage")
    void generateResponse_returnsNonBlankMessage() {
        // given
        ChatContext ctx = new ChatContext(null, null, null, null, null, 0, null, 0L);

        // when
        String result = provider.generateResponse("Terminal kalabalık mı?", ctx);

        // then
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("generateResponse_mentionsCloudAlternative")
    void generateResponse_mentionsCloudAlternative() {
        // given
        ChatContext ctx = new ChatContext(null, null, null, null, null, 0, null, 0L);

        // when
        String result = provider.generateResponse("Herhangi bir soru", ctx);

        // then — kullanıcıya Cloud moduna geçmesi söylenmeli
        assertThat(result.toLowerCase()).containsAnyOf("cloud", "gemini", "model");
    }
}
