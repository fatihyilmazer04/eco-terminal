package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AIPredictionController Integration Tests")
class AIPredictionControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        if (userToken  == null) userToken  = userToken();
        if (adminToken == null) adminToken = adminToken();
    }

    @Test
    @DisplayName("getAllPredictions_withAdminToken_returns200")
    void getAllPredictions_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getAllPredictions_withUserToken_returns403")
    void getAllPredictions_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getAllPredictions_withoutToken_returns401")
    void getAllPredictions_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/ai/predictions", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getHighRisk_withAdminToken_returns200")
    void getHighRisk_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions/high-risk", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getHighRisk_withUserToken_returns403")
    void getHighRisk_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions/high-risk", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getPredictionForZone_withAdminToken_returns200Or404")
    void getPredictionForZone_withAdminToken_returns200Or404() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions/1", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getZoneForecast_withAdminToken_returns200Or404")
    void getZoneForecast_withAdminToken_returns200Or404() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions/zone-forecast?zoneId=1", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("refresh_withAdminToken_returns200")
    void refresh_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/ai/predictions/refresh", HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        // AI servisi test ortamında çalışmıyor → 200 veya 503 kabul edilir
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("refresh_withoutToken_returns401")
    void refresh_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/ai/predictions/refresh", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
