package com.ecoterminal.service.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lokal fine-tune model sağlayıcısı — PLACEHOLDER.
 *
 * TODO: Bu sınıf ileride gerçek bir lokal LLM ile (örn. Ollama, llama.cpp, GGUF modeli)
 *       değiştirilecektir. Şimdilik yalnızca "henüz hazır değil" mesajı döner.
 *
 * Lokal model entegrasyonu için yapılacaklar:
 *   1. Lokal model servisinin URL'ini application.yml'e ekle
 *      (app.chatbot.local.url=http://localhost:11434)
 *   2. generateResponse() içinde o servise HTTP isteği gönder
 *   3. isAvailable() → lokal servis /health endpoint'ini kontrol etsin
 */
@Slf4j
@Component
public class LocalChatbotProvider implements ChatbotProvider {

    @Override
    public String getProviderName()  { return "local"; }

    @Override
    public String getDisplayName()   { return "Local (Kendi Modelimiz)"; }

    @Override
    public boolean isAvailable()     { return false; } // TODO: lokal servis hazır olduğunda true yap

    @Override
    public String generateResponse(String userMessage, ChatContext context) {
        // TODO: Lokal fine-tune model entegrasyonu buraya gelecek
        log.info("Lokal model isteği alındı ama henüz hazır değil — kullanıcı bilgilendirildi");
        return "Lokal model henüz eğitim aşamasında. Lütfen Cloud (Gemini) moduna geçin. " +
               "Ayarlar → Yapay Zeka Modeli → Cloud seçeneğini kullanabilirsiniz.";
    }
}
