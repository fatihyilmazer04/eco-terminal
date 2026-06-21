package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoungeController Integration Tests")
class LoungeControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() {
        if (userToken == null) userToken = userToken();
    }

    @Test
    @DisplayName("getQuietLounges_withUserToken_returns200")
    void getQuietLounges_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/lounges", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getQuietLounges_withoutToken_returns401")
    void getQuietLounges_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/lounges", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getBestLounge_withUserToken_returns200Or404")
    void getBestLounge_withUserToken_returns200Or404() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/lounges/best", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        // Lounge zone varsa 200, yoksa 404
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getBestLounge_withoutToken_returns401")
    void getBestLounge_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/lounges/best", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
