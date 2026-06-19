package com.ecoterminal.service.chatbot;

import com.ecoterminal.model.dto.ChatbotResponse;
import com.ecoterminal.model.dto.ChatbotResponse.RouteStepInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM mikroservisine (FastAPI/Python) proxy yapan provider.
 *
 * - Bağlantı: app.chatbot.llm-service.url (varsayılan: http://llm-service:5002)
 * - Kimlik doğrulama: X-Internal-Token header'ı (app.internal.token)
 * - intent, confidence, routeSteps, sourcesUsed alanlarını ChatbotResponse'a ekler.
 */
@Slf4j
@Component
public class LlmServiceProvider implements ChatbotProvider {

    private static final String FALLBACK_REPLY =
            "Yapay zeka servisine ulaşılamıyor. Lütfen birazdan tekrar deneyin.";

    private final RestTemplate restTemplate;
    private final String       llmServiceUrl;
    private final String       internalToken;

    public LlmServiceProvider(
            RestTemplateBuilder builder,
            @Value("${app.chatbot.llm-service.url:http://llm-service:5002}") String llmServiceUrl,
            @Value("${app.internal.token:}") String internalToken) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(180).toMillis()); // yerel HF model inference için
        this.restTemplate  = builder.requestFactory(() -> factory).build();
        this.llmServiceUrl = llmServiceUrl;
        this.internalToken = internalToken;
    }

    @Override
    public String getProviderName() { return "llm-service"; }

    @Override
    public String getDisplayName()  { return "LLM Servisi (RAG + DistilBERT)"; }

    @Override
    public boolean isAvailable()    { return true; } // başarısız çağrı fallback döner, servis hiç çökmez

    // ── Ana metod ─────────────────────────────────────────────────────────────

    @Override
    public String generateResponse(String userMessage, ChatContext context) {
        return generateRichResponse(userMessage, context).reply();
    }

    @Override
    public ChatbotResponse generateRichResponse(String userMessage, ChatContext context) {
        String url = llmServiceUrl + "/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalToken != null && !internalToken.isBlank()) {
            headers.set("X-Internal-Token", internalToken);
        }

        // ChatContext verilerini Python template engine için ekle
        Map<String, Object> body = new HashMap<>();
        body.put("message",    userMessage);
        body.put("session_id", UUID.randomUUID().toString());
        body.put("locale",     "tr-TR");

        if (context.ecoPoints() != null) {
            body.put("eco_points", context.ecoPoints());
        }
        if (context.tierLevel() != null) {
            body.put("tier_level", context.tierLevel());
        }
        if (context.avgDensityPct() >= 0) {
            body.put("avg_density_pct", context.avgDensityPct());
        }
        body.put("unread_notification_count", context.unreadNotificationCount());
        if (context.userFlights() != null && !context.userFlights().isEmpty()) {
            List<Map<String, Object>> flights = new ArrayList<>();
            for (ChatContext.FlightInfo f : context.userFlights()) {
                Map<String, Object> fm = new HashMap<>();
                fm.put("code",           f.code());
                fm.put("destination",    f.destination());
                fm.put("departure_time", f.departureTime());
                fm.put("status",         f.status());
                if (f.gate() != null) fm.put("gate", f.gate());
                flights.add(fm);
            }
            body.put("user_flights", flights);
        }
        if (context.hotZones() != null && !context.hotZones().isEmpty()) {
            List<Map<String, Object>> zones = new ArrayList<>();
            for (ChatContext.ZoneInfo z : context.hotZones()) {
                zones.add(Map.of("name", z.name(), "type", z.type(), "density_pct", z.densityPct()));
            }
            body.put("hot_zones", zones);
        }
        if (context.quietZones() != null && !context.quietZones().isEmpty()) {
            List<Map<String, Object>> zones = new ArrayList<>();
            for (ChatContext.ZoneInfo z : context.quietZones()) {
                zones.add(Map.of("name", z.name(), "type", z.type(), "density_pct", z.densityPct()));
            }
            body.put("quiet_zones", zones);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, entity, Map.class);
            return parseResponse(resp);
        } catch (RestClientException e) {
            log.error("llm-service bağlantı hatası [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            return ChatbotResponse.of(FALLBACK_REPLY, getProviderName());
        } catch (Exception e) {
            log.error("llm-service beklenmedik hata [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return ChatbotResponse.of(FALLBACK_REPLY, getProviderName());
        }
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ChatbotResponse parseResponse(Map<String, Object> resp) {
        if (resp == null) return ChatbotResponse.of(FALLBACK_REPLY, getProviderName());

        String  reply      = (String)  resp.getOrDefault("reply",      FALLBACK_REPLY);
        String  intent     = (String)  resp.get("intent");
        Double  confidence = resp.get("confidence") instanceof Number n ? n.doubleValue() : null;

        // sources_used
        List<String> sourcesUsed = null;
        Object src = resp.get("sources_used");
        if (src instanceof List<?> list) {
            sourcesUsed = list.stream().map(Object::toString).toList();
        }

        // route_steps
        List<RouteStepInfo> routeSteps = null;
        Object steps = resp.get("route_steps");
        if (steps instanceof List<?> list && !list.isEmpty()) {
            routeSteps = list.stream()
                    .filter(s -> s instanceof Map<?, ?>)
                    .map(s -> {
                        Map<String, Object> m = (Map<String, Object>) s;
                        int stepNum  = m.get("step_number") instanceof Number n ? n.intValue() : 0;
                        String zone  = (String) m.getOrDefault("zone_name", "");
                        String instr = (String) m.getOrDefault("instruction", "");
                        int walk     = m.get("estimated_walk_minutes") instanceof Number n ? n.intValue() : 0;
                        return new RouteStepInfo(stepNum, zone, instr, walk);
                    })
                    .toList();
        }

        log.debug("llm-service yanıt: intent={}, conf={}, steps={}",
                intent, confidence, routeSteps != null ? routeSteps.size() : 0);

        return ChatbotResponse.rich(reply, getProviderName(), intent, confidence, routeSteps, sourcesUsed);
    }
}
