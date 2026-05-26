package com.ecoterminal.controller;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.AuthResponse;
import com.ecoterminal.model.dto.LoginRequest;
import com.ecoterminal.model.dto.RefreshTokenRequest;
import com.ecoterminal.model.dto.RegisterRequest;
import com.ecoterminal.model.dto.ForgotPasswordRequest;
import com.ecoterminal.model.dto.ResetPasswordRequest;
import com.ecoterminal.model.dto.SendCodeRequest;
import com.ecoterminal.model.dto.VerifyRegisterRequest;
import com.ecoterminal.service.AuthService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Kullanıcı giriş, kayıt ve token yenileme işlemleri")
public class AuthController {

    private final AuthService authService;

    /**
     * Redis destekli dağıtık rate limiting (birden fazla instance için).
     * Redis bağlantısı yoksa in-memory fallback devreye girer.
     */
    @Autowired(required = false)
    private ProxyManager<byte[]> proxyManager;

    // In-memory fallback: Redis unavailable durumunda
    private final ConcurrentHashMap<String, Bucket> fallbackBuckets = new ConcurrentHashMap<>();

    private static final Supplier<BucketConfiguration> LOGIN_BUCKET_CONFIG = () ->
            BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(10)
                            .refillGreedy(10, Duration.ofMinutes(1))
                            .build())
                    .build();

    // ── POST /api/auth/login ───────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);

        if (!tryConsumeToken(clientIp)) {
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

    // ── POST /api/auth/register/send-code ─────────────────────────────────────

    @PostMapping("/register/send-code")
    public ResponseEntity<ApiResponse<Void>> sendRegisterCode(
            @Valid @RequestBody SendCodeRequest request
    ) {
        authService.sendRegisterCode(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Doğrulama kodu e-posta adresinize gönderildi"));
    }

    // ── POST /api/auth/register/verify ────────────────────────────────────────

    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyAndRegister(
            @Valid @RequestBody VerifyRegisterRequest request
    ) {
        AuthResponse authResponse = authService.verifyAndRegister(request);
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

    // ── POST /api/auth/forgot-password ────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.sendPasswordResetCode(request);
        return ResponseEntity.ok(ApiResponse.ok(null,
                "Eğer bu e-posta kayıtlıysa, doğrulama kodu gönderildi"));
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────────

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Şifreniz başarıyla güncellendi"));
    }

    // ── Yardımcı Metotlar ─────────────────────────────────────────────────

    private boolean tryConsumeToken(String clientIp) {
        if (proxyManager != null) {
            try {
                byte[] key = ("login_rate:" + clientIp).getBytes(StandardCharsets.UTF_8);
                Bucket bucket = proxyManager.builder()
                        .build(key, LOGIN_BUCKET_CONFIG);
                return bucket.tryConsume(1);
            } catch (Exception e) {
                log.warn("Redis rate limiter hatası, in-memory fallback kullanılıyor: {}", e.getMessage());
            }
        }
        // In-memory fallback
        Bucket bucket = fallbackBuckets.computeIfAbsent(clientIp, ip ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(10)
                                .refillGreedy(10, Duration.ofMinutes(1))
                                .build())
                        .build()
        );
        return bucket.tryConsume(1);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
