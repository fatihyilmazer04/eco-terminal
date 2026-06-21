package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoyaltyController Integration Tests")
class LoyaltyControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() {
        if (userToken == null) userToken = userToken();
    }

    @Test
    @DisplayName("getWallet_withUserToken_returns200AndBalance")
    void getWallet_withUserToken_returns200AndBalance() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/loyalty/wallet", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("currentBalance");
        assertThat(data).containsKey("tierLevel");
    }

    @Test
    @DisplayName("getWallet_withoutToken_returns401")
    void getWallet_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/loyalty/wallet", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getTransactions_withUserToken_returns200")
    void getTransactions_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/loyalty/transactions", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getTransactions_withoutToken_returns401")
    void getTransactions_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/loyalty/transactions", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getRewards_withUserToken_returns200")
    void getRewards_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/loyalty/rewards", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("getRewards_withoutToken_returns401")
    void getRewards_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/loyalty/rewards", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("earnPoints_withUserToken_returns200AndUpdatedBalance")
    void earnPoints_withUserToken_returns200AndUpdatedBalance() {
        Map<String, Object> body = Map.of("action", "CHECK_IN");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/loyalty/earn", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("currentBalance");
    }

    @Test
    @DisplayName("earnPoints_withoutToken_returns401")
    void earnPoints_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/loyalty/earn", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getMyRedemptions_withUserToken_returns200")
    void getMyRedemptions_withUserToken_returns200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/loyalty/my-redemptions", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("data");
    }
}
