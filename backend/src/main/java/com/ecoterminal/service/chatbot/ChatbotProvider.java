package com.ecoterminal.service.chatbot;

import com.ecoterminal.model.dto.ChatbotResponse;

/**
 * Chatbot sağlayıcı arayüzü.
 * Yeni bir LLM eklemek için bu interface'i uygula ve @Component ekle —
 * ChatbotService otomatik olarak keşfeder.
 */
public interface ChatbotProvider {

    /**
     * Kullanıcının mesajına ve RAG bağlamına göre yanıt üretir.
     *
     * @param userMessage kullanıcının ham mesajı
     * @param context     veritabanından çekilmiş gerçek zamanlı bağlam
     * @return Türkçe doğal dil yanıtı
     */
    String generateResponse(String userMessage, ChatContext context);

    /**
     * Zengin metadata içeren yanıt üretir.
     * Varsayılan implementasyon generateResponse() çağırır ve sarar.
     * LlmServiceProvider gibi daha zengin yanıtlar üretenler bu metodu override eder.
     */
    default ChatbotResponse generateRichResponse(String userMessage, ChatContext context) {
        return ChatbotResponse.of(generateResponse(userMessage, context), getProviderName());
    }

    /** Dahili anahtar — "cloud", "local" veya "llm-service" */
    String getProviderName();

    /** Kullanıcıya gösterilen isim */
    String getDisplayName();

    /** Sağlayıcı şu an kullanılabilir mi? (API key eksikse, model yüklenmediyse vb.) */
    boolean isAvailable();
}
