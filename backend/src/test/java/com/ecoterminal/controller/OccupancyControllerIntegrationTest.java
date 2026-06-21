package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-002 — Yolcu Yoğunluğu İzleme
 * Zone listesi, zone bazlı yoğunluk ve heatmap endpoint'lerini test eder.
 * DB bağlantısı application-test.yml'den okunur (localhost:5432).
 */
@DisplayName("OccupancyController Integration Tests")
class OccupancyControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() {
        if (userToken == null) userToken = userToken();
    }

    // ── GET /api/zones  (USER) ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllZones_withValidToken_returns200AndList")
    void getAllZones_withValidToken_returns200AndList() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/zones",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getAllZones_withoutToken_returns401")
    void getAllZones_withoutToken_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/zones",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/occupancy/heatmap  (USER) ──────────────────────────────────

    @Test
    @DisplayName("getHeatmap_withValidToken_returns200AndData")
    void getHeatmap_withValidToken_returns200AndData() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/occupancy/heatmap",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getHeatmap_withoutToken_returns401")
    void getHeatmap_withoutToken_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/occupancy/heatmap",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/zones/{id}/occupancy  (USER) ───────────────────────────────

    @Test
    @DisplayName("getZoneOccupancy_withValidZoneId_returns200AndOccupancyData")
    void getZoneOccupancy_withValidZoneId_returns200AndOccupancyData() {
        // V3 migration seeds zone_id=1
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/zones/1/occupancy",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKey("zoneId");
        assertThat(data).containsKey("densityPct");
    }

    @Test
    @DisplayName("getZoneOccupancy_withInvalidZoneId_returns404")
    void getZoneOccupancy_withInvalidZoneId_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/zones/99999/occupancy",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
