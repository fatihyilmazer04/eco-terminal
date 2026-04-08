package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
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
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

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
        // JPA dialect override
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ── Login Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginEndpoint_withValidCredentials_returns200AndToken")
    void loginEndpoint_withValidCredentials_returns200AndToken() {
        // given — V2 migration seeds admin@ecoterminal.com / admin123
        Map<String, String> body = Map.of(
                "email", "admin@ecoterminal.com",
                "password", "admin123"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKey("accessToken");
        assertThat(data.get("accessToken").toString()).isNotBlank();
    }

    @Test
    @DisplayName("loginEndpoint_withInvalidCredentials_returns401")
    void loginEndpoint_withInvalidCredentials_returns401() {
        // given
        Map<String, String> body = Map.of(
                "email", "admin@ecoterminal.com",
                "password", "wrongpassword"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("registerEndpoint_withValidData_returns200")
    void registerEndpoint_withValidData_returns200() {
        // given — unique email
        Map<String, String> body = Map.of(
                "email", "newuser_" + System.currentTimeMillis() + "@eco.com",
                "password", "pass123",
                "fullName", "Yeni Kullanıcı"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                new HttpEntity<>(body, headers),
                Map.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKey("accessToken");
    }

    @Test
    @DisplayName("registerEndpoint_withDuplicateEmail_returns409")
    void registerEndpoint_withDuplicateEmail_returns409() {
        // given — V2 seeded email
        Map<String, String> body = Map.of(
                "email", "passenger@ecoterminal.com",
                "password", "pass123",
                "fullName", "Duplicate"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                new HttpEntity<>(body, headers),
                Map.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("protectedEndpoint_withoutToken_returns401")
    void protectedEndpoint_withoutToken_returns401() {
        // when — token yok
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/zones",
                Map.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("adminEndpoint_withUserToken_returns403")
    void adminEndpoint_withUserToken_returns403() {
        // given — önce USER token al
        Map<String, String> loginBody = Map.of(
                "email", "passenger@ecoterminal.com",
                "password", "pass123"
        );
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(loginBody, loginHeaders),
                Map.class
        );
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        String token = ((Map<String, Object>) loginResp.getBody().get("data"))
                .get("accessToken").toString();

        // when — admin endpoint'e USER token ile git
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/admin/dashboard",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
