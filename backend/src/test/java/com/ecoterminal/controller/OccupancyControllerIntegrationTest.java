package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("OccupancyController Integration Tests")
class OccupancyControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ecoterminal_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private String bearerToken;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void obtainToken() {
        if (bearerToken != null) return;  // once per class

        Map<String, String> loginBody = Map.of(
                "email", "passenger@ecoterminal.com",
                "password", "pass123"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(loginBody, headers),
                Map.class
        );

        if (loginResp.getStatusCode() == HttpStatus.OK) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) loginResp.getBody().get("data");
            bearerToken = data.get("accessToken").toString();
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) headers.setBearerAuth(bearerToken);
        return headers;
    }

    // ── Heatmap Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getHeatmap_withValidToken_returns200AndZones")
    void getHeatmap_withValidToken_returns200AndZones() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/zones/heatmap",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKey("zones");
    }

    @Test
    @DisplayName("getHeatmap_withoutToken_returns401")
    void getHeatmap_withoutToken_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/zones/heatmap",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getZoneOccupancy_withValidZoneId_returnsOccupancy")
    void getZoneOccupancy_withValidZoneId_returnsOccupancy() {
        // V3 migration seeds zones with zone_id starting from 1
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/zones/1/occupancy",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
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
                new HttpEntity<>(authHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
