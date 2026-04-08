package com.ecoterminal.service;

import com.ecoterminal.model.entity.Role;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.security.JwtService;
import com.ecoterminal.security.UserPrincipal;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    private static final String TEST_SECRET =
            "eco-terminal-test-secret-must-be-at-least-256-bits-long-for-hs512-algorithm";
    private static final long ACCESS_EXPIRATION  = 900_000L;   // 15 dk
    private static final long REFRESH_EXPIRATION = 604_800_000L; // 7 gün

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration",  ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", REFRESH_EXPIRATION);

        User user = User.builder()
                .userId(1L)
                .email("test@eco.com")
                .role(Role.USER)
                .isActive(true)
                .build();
        userDetails = new UserPrincipal(user);
    }

    @Test
    @DisplayName("generateAccessToken_returnsNonEmptyString")
    void generateAccessToken_returnsNonEmptyString() {
        String token = jwtService.generateAccessToken(userDetails);
        assertThat(token).isNotBlank();
        // JWT formatı: header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extractUsername_fromValidToken_returnsEmail")
    void extractUsername_fromValidToken_returnsEmail() {
        String token = jwtService.generateAccessToken(userDetails);
        String username = jwtService.extractUsername(token);
        assertThat(username).isEqualTo("test@eco.com");
    }

    @Test
    @DisplayName("validateAccessToken_withValidToken_returnsTrue")
    void validateAccessToken_withValidToken_returnsTrue() {
        String token = jwtService.generateAccessToken(userDetails);
        boolean valid = jwtService.validateAccessToken(token, userDetails);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("validateAccessToken_withRefreshToken_returnsFalse")
    void validateAccessToken_withRefreshToken_returnsFalse() {
        // Refresh token access endpoint'te geçersiz sayılmalı
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        boolean valid = jwtService.validateAccessToken(refreshToken, userDetails);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("validateRefreshToken_withRefreshToken_returnsTrue")
    void validateRefreshToken_withRefreshToken_returnsTrue() {
        String token = jwtService.generateRefreshToken(userDetails);
        assertThat(jwtService.validateRefreshToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken_withExpiredToken_returnsFalse")
    void validateToken_withExpiredToken_returnsFalse() {
        // 1ms expiration → anında süresi dolar
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 1L);
        String token = jwtService.generateAccessToken(userDetails);

        // Sleep to ensure expiration
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(jwtService.validateAccessToken(token, userDetails)).isFalse();
    }

    @Test
    @DisplayName("validateToken_withTamperedToken_returnsFalse")
    void validateToken_withTamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(userDetails);
        // Signature kısmını boz
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "TAMPERED";
        assertThat(jwtService.validateAccessToken(tamperedToken, userDetails)).isFalse();
    }

    @Test
    @DisplayName("extractRole_fromAccessToken_returnsRole")
    void extractRole_fromAccessToken_returnsRole() {
        String token = jwtService.generateAccessToken(userDetails);
        String role = jwtService.extractRole(token);
        assertThat(role).isEqualTo("USER");
    }
}
