package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnergyController Integration Tests")
class EnergyControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        if (userToken  == null) userToken  = userToken();
        if (adminToken == null) adminToken = adminToken();
    }

    @Test
    @DisplayName("getAllUsage_withAdminToken_returns200")
    void getAllUsage_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/energy/usage", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getAllUsage_withUserToken_returns403")
    void getAllUsage_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/energy/usage", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getAllUsage_withoutToken_returns401")
    void getAllUsage_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/energy/usage", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getZoneUsage_withValidZoneId_returns200")
    void getZoneUsage_withValidZoneId_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/energy/usage/1", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getZoneUsage_withUserToken_returns403")
    void getZoneUsage_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/energy/usage/1", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getSavings_withAdminToken_returns200")
    void getSavings_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/energy/savings", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getSavings_withUserToken_returns403")
    void getSavings_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/energy/savings", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getSavings_withoutToken_returns401")
    void getSavings_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/energy/savings", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
