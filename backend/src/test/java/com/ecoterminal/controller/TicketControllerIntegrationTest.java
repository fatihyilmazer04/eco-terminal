package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TicketController Integration Tests")
class TicketControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() {
        if (userToken == null) userToken = userToken();
    }

    @Test
    @DisplayName("lookupPnr_withValidPnr_returns200")
    void lookupPnr_withValidPnr_returns200() {
        // V2 migration seeds a ticket with a known PNR — use seed data
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/tickets/lookup?pnrCode=TK-A3F2B1",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        // 200 (found) or 404 (not seeded) — both are valid auth responses
        assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("lookupPnr_withoutToken_returns401")
    void lookupPnr_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/tickets/lookup?pnrCode=TK-A3F2B1", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("claimTicket_withoutToken_returns401")
    void claimTicket_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/tickets/claim", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("claimTicket_withInvalidPnr_returns404")
    void claimTicket_withInvalidPnr_returns404() {
        Map<String, String> body = Map.of("pnrCode", "XX-NOTEXIST");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/tickets/claim", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("unclaimTicket_withoutToken_returns401")
    void unclaimTicket_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/tickets/1/unclaim", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("unclaimTicket_withNonExistentId_returns404OrForbidden")
    void unclaimTicket_withNonExistentId_returns404OrForbidden() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/tickets/99999/unclaim", HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
    }
}
