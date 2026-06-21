package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationController Integration Tests")
class NotificationControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        if (userToken  == null) userToken  = userToken();
        if (adminToken == null) adminToken = adminToken();
    }

    @Test
    @DisplayName("getMyNotifications_withUserToken_returns200")
    void getMyNotifications_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/notifications/my", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getMyNotifications_withoutToken_returns401")
    void getMyNotifications_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/notifications/my", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getUnreadCount_withUserToken_returns200AndCount")
    void getUnreadCount_withUserToken_returns200AndCount() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/notifications/unread-count", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("count");
    }

    @Test
    @DisplayName("getUnreadCount_withoutToken_returns401")
    void getUnreadCount_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/notifications/unread-count", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("markAllAsRead_withUserToken_returns200")
    void markAllAsRead_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/notifications/read-all", HttpMethod.PUT,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("markAllAsRead_withoutToken_returns401")
    void markAllAsRead_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/notifications/read-all", HttpMethod.PUT,
                HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("sendManual_withUserToken_returns403")
    void sendManual_withUserToken_returns403() {
        Map<String, Object> body = Map.of(
                "userId", 2, "type", "CROWD_ALERT",
                "title", "Test", "body", "Test mesajı");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/notifications/send", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sendManual_withoutToken_returns401")
    void sendManual_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/notifications/send", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("clearAllNotifications_withUserToken_returns200")
    void clearAllNotifications_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/notifications/clear-all", HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
