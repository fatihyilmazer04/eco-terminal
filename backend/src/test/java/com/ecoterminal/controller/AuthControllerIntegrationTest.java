package com.ecoterminal.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-001 — JWT Kimlik Doğrulama
 * Login, register ve token tabanlı yetkilendirme akışlarını test eder.
 * DB bağlantısı application-test.yml'den okunur (localhost:5432).
 */
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    // ── Login Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginEndpoint_withValidCredentials_returns200AndToken")
    void loginEndpoint_withValidCredentials_returns200AndToken() {
        Map<String, String> body = Map.of(
                "email", "admin@ecoterminal.com",
                "password", "admin123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKey("accessToken");
        assertThat(data.get("accessToken").toString()).isNotBlank();
    }

    @Test
    @DisplayName("loginEndpoint_withInvalidCredentials_returns401")
    void loginEndpoint_withInvalidCredentials_returns401() {
        Map<String, String> body = Map.of(
                "email", "admin@ecoterminal.com",
                "password", "wrongpassword"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("registerEndpoint_withValidData_returns200")
    void registerEndpoint_withValidData_returns200() {
        Map<String, String> body = Map.of(
                "email", "newuser_" + System.currentTimeMillis() + "@eco.com",
                "password", "pass123",
                "fullName", "Yeni Kullanici"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                new HttpEntity<>(body, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKey("accessToken");
    }

    @Test
    @DisplayName("registerEndpoint_withDuplicateEmail_returns409")
    void registerEndpoint_withDuplicateEmail_returns409() {
        // V2 migration seeds passenger@ecoterminal.com
        Map<String, String> body = Map.of(
                "email", "passenger@ecoterminal.com",
                "password", "pass123",
                "fullName", "Duplicate"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                new HttpEntity<>(body, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("protectedEndpoint_withoutToken_returns401")
    void protectedEndpoint_withoutToken_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/zones",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("adminEndpoint_withUserToken_returns403")
    void adminEndpoint_withUserToken_returns403() {
        String token = userToken();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/admin/dashboard",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
