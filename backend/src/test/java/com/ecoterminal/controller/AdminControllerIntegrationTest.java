package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminController Integration Tests")
class AdminControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        if (userToken  == null) userToken  = userToken();
        if (adminToken == null) adminToken = adminToken();
    }

    @Test
    @DisplayName("getDashboard_withAdminToken_returns200AndSummary")
    void getDashboard_withAdminToken_returns200AndSummary() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getDashboard_withUserToken_returns403")
    void getDashboard_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getDashboard_withoutToken_returns401")
    void getDashboard_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/admin/dashboard", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("listUsers_withAdminToken_returns200AndUserList")
    void listUsers_withAdminToken_returns200AndUserList() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("listUsers_withUserToken_returns403")
    void listUsers_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getSystemHealth_withAdminToken_returns200")
    void getSystemHealth_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/system/health", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("backend");
        assertThat(data).containsKey("database");
    }

    @Test
    @DisplayName("getSystemHealth_withUserToken_returns403")
    void getSystemHealth_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/system/health", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getOccupancyReport_withAdminToken_returns200")
    void getOccupancyReport_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/reports/occupancy", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getEnergyReport_withAdminToken_returns200")
    void getEnergyReport_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/reports/energy", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("generateReport_withAdminToken_returns200")
    void generateReport_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/admin/reports/generate?type=OCCUPANCY_GENERAL&startDate=2025-01-01&endDate=2025-01-31",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("generateReport_withoutToken_returns401")
    void generateReport_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/admin/reports/generate?type=OCCUPANCY_GENERAL&startDate=2025-01-01&endDate=2025-01-31",
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
