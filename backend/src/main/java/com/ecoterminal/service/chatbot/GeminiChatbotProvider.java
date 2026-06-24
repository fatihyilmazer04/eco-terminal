package com.ecoterminal.service.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini REST API aracılığıyla yanıt üreten sağlayıcı.
 *
 * Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
 * Hata olursa ya da API key yoksa nazik bir Türkçe mesaj döner — chatbot çökmez.
 */
@Slf4j
@Component
public class GeminiChatbotProvider implements ChatbotProvider {

    private static final String GEMINI_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String FALLBACK_MESSAGE =
            "Şu an yapay zeka servisine ulaşamıyorum. Lütfen birazdan tekrar deneyin.";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;

    public GeminiChatbotProvider(
            RestTemplateBuilder builder,
            @Value("${app.chatbot.gemini.api-key:}") String apiKey,
            @Value("${app.chatbot.gemini.model:gemini-pro}") String model) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.restTemplate = builder.requestFactory(() -> factory).build();
        this.apiKey = apiKey;
        this.model  = model;
    }

    @Override
    public String getProviderName()  { return "cloud"; }

    @Override
    public String getDisplayName()   { return "Cloud (Gemini)"; }

    @Override
    public boolean isAvailable()     { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public String generateResponse(String userMessage, ChatContext ctx) {
        if (!isAvailable()) {
            log.warn("Gemini API key yapılandırılmamış — GEMINI_API_KEY ortam değişkenini ayarlayın");
            return "Gemini API anahtarı yapılandırılmamış. Lütfen yöneticinize başvurun.";
        }

        String prompt = buildPrompt(userMessage, ctx);
        String url    = String.format(GEMINI_BASE, model, apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature",    0.7,
                        "maxOutputTokens", 600
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            return extractText(response);
        } catch (RestClientException e) {
            log.error("Gemini API hatası: {}", e.getMessage());
            return FALLBACK_MESSAGE;
        } catch (Exception e) {
            log.error("Gemini beklenmedik hata: {}", e.getMessage());
            return FALLBACK_MESSAGE;
        }
    }

    // ── Yardımcılar ─────────────────────────────────────────────────────────

    /** RAG verilerini sistem talimatıyla birleştirerek Gemini'ye gönderilecek tek prompt'u oluşturur. */
    private String buildPrompt(String userMessage, ChatContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Sen Eco-Terminal akıllı havalimanı asistanısın. ")
          .append("Türkçe, samimi ve kısa cevap ver (en fazla 3-4 cümle). ")
          .append("Aşağıdaki gerçek zamanlı verileri kullanarak soruyu yanıtla.\n\n");

        sb.append("=== ANLIŞIK TERMİNAL VERİLERİ ===\n");
        sb.append("Saat: ").append(ctx.currentTime()).append("\n");
        sb.append("Ortalama terminal doluluk: %").append(ctx.avgDensityPct()).append("\n\n");

        if (!ctx.hotZones().isEmpty()) {
            sb.append("YOĞUN BÖLGELER:\n");
            ctx.hotZones().forEach(z ->
                    sb.append("  - ").append(z.name()).append(" (").append(z.type()).append(")")
                      .append(" — %").append(z.densityPct()).append("\n"));
            sb.append("\n");
        }

        if (!ctx.quietZones().isEmpty()) {
            sb.append("SAKİN BÖLGELER (önerilen):\n");
            ctx.quietZones().forEach(z ->
                    sb.append("  - ").append(z.name()).append(" (").append(z.type()).append(")")
                      .append(" — %").append(z.densityPct()).append("\n"));
            sb.append("\n");
        }

        sb.append("KULLANICI BİLGİLERİ:\n");
        if (ctx.ecoPoints() != null) {
            sb.append("  Eco Puan: ").append(ctx.ecoPoints())
              .append(" (").append(ctx.tierLevel()).append(" üye)\n");
        }

        if (!ctx.userFlights().isEmpty()) {
            sb.append("  Aktif uçuşlar:\n");
            ctx.userFlights().forEach(f ->
                    sb.append("    - ").append(f.code()).append(" → ").append(f.destination())
                      .append(" | Kalkış: ").append(f.departureTime())
                      .append(" | Kapı: ").append(f.gate() != null ? f.gate() : "Henüz atanmadı")
                      .append(" | Durum: ").append(f.status()).append("\n"));
        } else {
            sb.append("  Aktif uçuş bulunamadı.\n");
        }

        sb.append("===\n\n");
        sb.append("Kullanıcının sorusu: ").append(userMessage);

        return sb.toString();
    }

    /** Gemini JSON yanıtından metin çıkarır. */
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        if (response == null) return FALLBACK_MESSAGE;
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.warn("Gemini yanıt ayrıştırma hatası: {}", e.getMessage());
            return FALLBACK_MESSAGE;
        }
    }
}
