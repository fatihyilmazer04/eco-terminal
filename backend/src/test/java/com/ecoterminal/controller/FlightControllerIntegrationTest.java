package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FlightController Integration Tests")
class FlightControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        if (userToken  == null) userToken  = userToken();
        if (adminToken == null) adminToken = adminToken();
    }

    @Test
    @DisplayName("getAllFlights_withAdminToken_returns200")
    void getAllFlights_withAdminToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/flights", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getAllFlights_withUserToken_returns403")
    void getAllFlights_withUserToken_returns403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/flights", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getAllFlights_withoutToken_returns401")
    void getAllFlights_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(baseUrl() + "/api/flights", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getMyFlights_withUserToken_returns200")
    void getMyFlights_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/flights/my", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getMyFlights_withoutToken_returns401")
    void getMyFlights_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(baseUrl() + "/api/flights/my", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getFlightDetails_withValidId_returns200AndFlightCode")
    void getFlightDetails_withValidId_returns200AndFlightCode() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/flights/1", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("flightCode");
    }

    @Test
    @DisplayName("getFlightDetails_withInvalidId_returns404")
    void getFlightDetails_withInvalidId_returns404() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/flights/99999", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getFlightDetails_withoutToken_returns401")
    void getFlightDetails_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(baseUrl() + "/api/flights/1", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
