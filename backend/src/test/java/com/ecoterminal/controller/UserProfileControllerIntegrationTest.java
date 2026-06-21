package com.ecoterminal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileController Integration Tests")
class UserProfileControllerIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() {
        if (userToken == null) userToken = userToken();
    }

    @Test
    @DisplayName("getProfile_withUserToken_returns200AndProfileFields")
    void getProfile_withUserToken_returns200AndProfileFields() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/users/profile", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getBody().get("data");
        assertThat(data).containsKey("email");
    }

    @Test
    @DisplayName("getProfile_withoutToken_returns401")
    void getProfile_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.getForEntity(
                baseUrl() + "/api/users/profile", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("updateProfile_withUserToken_returns200")
    void updateProfile_withUserToken_returns200() {
        Map<String, String> body = Map.of("firstName", "TestAd", "lastName", "TestSoyad");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/users/profile", HttpMethod.PUT,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updateProfile_withoutToken_returns401")
    void updateProfile_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/users/profile", HttpMethod.PUT,
                HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("updatePreferences_withUserToken_returns200")
    void updatePreferences_withUserToken_returns200() {
        Map<String, Object> body = Map.of("notifications", true, "language", "TR");
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/users/preferences", HttpMethod.PUT,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updatePreferences_withoutToken_returns401")
    void updatePreferences_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/users/preferences", HttpMethod.PUT,
                HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("changePassword_withWrongCurrentPassword_returns400")
    void changePassword_withWrongCurrentPassword_returns400() {
        Map<String, String> body = Map.of(
                "currentPassword", "yanlis_sifre_123",
                "newPassword", "yeniSifre123",
                "confirmPassword", "yeniSifre123"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                baseUrl() + "/api/users/change-password", HttpMethod.POST,
                new HttpEntity<>(body, bearerJsonHeaders(userToken)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("changePassword_withoutToken_returns401")
    void changePassword_withoutToken_returns401() {
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl() + "/api/users/change-password", HttpEntity.EMPTY, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
