package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

/**
 * Tüm controller integration testleri için ortak altyapı.
 * DB bağlantısı application-test.yml'den okunur (localhost:5432).
 * Yerel: dev-infra-up.sh ile başlatılan eco-postgres-dev container.
 * CI:    GitHub Actions PostgreSQL service container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
abstract class BaseIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    // ── Test altyapısı ───────────────────────────────────────────────────────

    /** PATCH desteği için Apache HttpClient'a geç — JDK HttpURLConnection PATCH desteklemez */
    @BeforeEach
    void configureHttpClient() {
        restTemplate.getRestTemplate()
                .setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    // ── Yardımcı metotlar ────────────────────────────────────────────────────

    String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Verilen kullanıcı bilgileriyle login olup accessToken döner. */
    String loginAs(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("email", email, "password", password);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class
        );
        if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            return data.get("accessToken").toString();
        }
        return null;
    }

    String adminToken() {
        return loginAs("admin@ecoterminal.com", "admin123");
    }

    String userToken() {
        return loginAs("passenger@ecoterminal.com", "pass123");
    }

    HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    HttpHeaders bearerJsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
