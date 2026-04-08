package com.ecoterminal.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT üretme ve doğrulama servisi — jjwt 0.12.x API.
 *
 * Token türleri:
 *  - access : kısa ömürlü (15 dk), API isteklerinde kullanılır
 *  - refresh: uzun ömürlü (7 gün), sadece /api/auth/refresh endpoint'inde kabul edilir
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private static final String CLAIM_ROLE         = "role";
    private static final String CLAIM_USER_ID      = "userId";
    private static final String CLAIM_TOKEN_TYPE   = "tokenType";
    private static final String TYPE_ACCESS        = "access";
    private static final String TYPE_REFRESH       = "refresh";

    // ── Token Üretimi ──────────────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails) {
        UserPrincipal principal = (UserPrincipal) userDetails;
        return buildToken(
                Map.of(
                        CLAIM_ROLE,       principal.getRole(),
                        CLAIM_USER_ID,    principal.getUserId(),
                        CLAIM_TOKEN_TYPE, TYPE_ACCESS
                ),
                userDetails.getUsername(),
                accessTokenExpiration
        );
    }

    public String generateRefreshToken(UserDetails userDetails) {
        UserPrincipal principal = (UserPrincipal) userDetails;
        return buildToken(
                Map.of(
                        CLAIM_USER_ID,    principal.getUserId(),
                        CLAIM_TOKEN_TYPE, TYPE_REFRESH
                ),
                userDetails.getUsername(),
                refreshTokenExpiration
        );
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token Doğrulama ────────────────────────────────────────────────────

    /**
     * Access token doğrulama — JwtAuthFilter tarafından her istekte çağrılır.
     * Token türünün "access" olduğunu da kontrol eder.
     */
    public boolean validateAccessToken(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            String tokenType = extractClaim(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
            return username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && TYPE_ACCESS.equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Refresh token doğrulama — sadece /api/auth/refresh endpoint'inde kullanılır.
     */
    public boolean validateRefreshToken(String token) {
        try {
            String tokenType = extractClaim(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
            return !isTokenExpired(token) && TYPE_REFRESH.equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    // ── Claim Çıkarma ──────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_USER_ID, Long.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
