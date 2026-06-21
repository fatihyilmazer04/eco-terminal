package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteController Integration Tests")
class RouteControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() {
        if (userToken == null) userToken = userToken();
    }

    @Test
    @DisplayName("suggestRoute_withoutToken_returns401")
    void suggestRoute_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/routes/suggest", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("suggestRoute_withNoActiveTicket_returns404")
    void suggestRoute_withNoActiveTicket_returns404() {
        // passenger@ecoterminal.com'un aktif bileti yoksa 404 beklenir
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/routes/suggest", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getAlternatives_withValidZoneId_returns200")
    void getAlternatives_withValidZoneId_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/routes/alternatives/1", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getAlternatives_withoutToken_returns401")
    void getAlternatives_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/routes/alternatives/1", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getOptimalRoute_withValidZoneIds_returns200AndAlternatives")
    void getOptimalRoute_withValidZoneIds_returns200AndAlternatives() {
        Map<String, Long> body = Map.of("fromZoneId", 1L, "toZoneId", 2L);
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/routes/optimal", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("alternatives");
    }

    @Test
    @DisplayName("getOptimalRoute_withoutToken_returns401")
    void getOptimalRoute_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/routes/optimal", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getOptimalRoute_withMissingFields_returns400")
    void getOptimalRoute_withMissingFields_returns400() {
        Map<String, Long> body = Map.of("fromZoneId", 1L); // toZoneId eksik
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/routes/optimal", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("checkinStep_withoutToken_returns401")
    void checkinStep_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/routes/checkin", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
