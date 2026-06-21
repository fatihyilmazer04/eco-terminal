package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatbotController Integration Tests")
class ChatbotControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        if (userToken  == null) userToken  = userToken();
        if (adminToken == null) adminToken = adminToken();
    }

    @Test
    @DisplayName("getProviders_withUserToken_returns200AndProviderList")
    void getProviders_withUserToken_returns200AndProviderList() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/chatbot/providers", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getProviders_withAdminToken_returns200")
    void getProviders_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/chatbot/providers", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getProviders_withoutToken_returns401")
    void getProviders_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/chatbot/providers", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ask_withUserToken_returns200AndReply")
    void ask_withUserToken_returns200AndReply() {
        Map<String, String> body = Map.of("message", "Terminal kalabalık mı?");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/chatbot/ask", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("reply");
    }

    @Test
    @DisplayName("ask_withoutToken_returns401")
    void ask_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/chatbot/ask", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ask_withBlankMessage_returns200WithDefaultReply")
    void ask_withBlankMessage_returns200WithDefaultReply() {
        Map<String, String> body = Map.of("message", "   ");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/chatbot/ask", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data.get("reply").toString()).isEqualTo("Lütfen bir soru yazın.");
    }
}
