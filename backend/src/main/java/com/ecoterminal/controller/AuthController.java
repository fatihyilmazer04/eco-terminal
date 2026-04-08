package com.ecoterminal.controller;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.AuthResponse;
import com.ecoterminal.model.dto.LoginRequest;
import com.ecoterminal.model.dto.RefreshTokenRequest;
import com.ecoterminal.model.dto.RegisterRequest;
import com.ecoterminal.service.AuthService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Kullanıcı giriş, kayıt ve token yenileme işlemleri")
public class AuthController {

    private final AuthService authService;

    /**
     * IP başına rate limiting bucket'ları.
     * Kural: 10 istek / dakika — brute-force koruması.
     * Production'da Redis backed bucket4j kullanılır (Faz 8).
     */
    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    // ── POST /api/auth/login ───────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        // Rate limit kontrolü
        String clientIp = getClientIp(httpRequest);
        Bucket bucket = loginBuckets.computeIfAbsent(clientIp, this::newLoginBucket);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            throw new BusinessException("Çok fazla giriş denemesi. 1 dakika bekleyin.", HttpStatus.TOO_MANY_REQUESTS);
        }

        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(authResponse, "Giriş başarılı"));
    }

    // ── POST /api/auth/register ────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authResponse, "Kayıt başarılı"));
    }

    // ── POST /api/auth/refresh ─────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse authResponse = authService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(authResponse, "Token yenilendi"));
    }

    // ── Yardımcı Metotlar ─────────────────────────────────────────────────

    private Bucket newLoginBucket(String ip) {
        // 10 token / dakika — her saniye 1/6 token dolar
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
