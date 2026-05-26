package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AuthResponse;
import com.ecoterminal.model.dto.LoginRequest;
import com.ecoterminal.model.dto.RegisterRequest;
import com.ecoterminal.model.dto.SendCodeRequest;
import com.ecoterminal.model.dto.VerifyRegisterRequest;
import com.ecoterminal.model.entity.Role;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.model.entity.UserProfile;
import com.ecoterminal.repository.UserProfileRepository;
import com.ecoterminal.repository.UserRepository;
import com.ecoterminal.security.CustomUserDetailsService;
import com.ecoterminal.security.JwtService;
import com.ecoterminal.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final VerificationService verificationService;

    /**
     * Kullanıcı girişi.
     * AuthenticationManager → BCrypt doğrulama → JWT çifti üret.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // Spring Security authentication manager BCrypt kontrolünü yapar.
            // Başarısız olursa AuthenticationException fırlatır.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            log.warn("Login failed for email: {}", request.email());
            throw new BusinessException("Geçersiz e-posta veya şifre", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        // last_login güncelle
        userRepository.updateLastLogin(user.getUserId(), Instant.now());

        UserDetails userDetails = new UserPrincipal(user);
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        String fullName = userProfileRepository.findByUserUserId(user.getUserId())
                .map(UserProfile::getFullName)
                .orElse(null);

        log.info("User logged in: {} | role: {}", user.getEmail(), user.getRole());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getRole().name(),
                user.getUserId(),
                user.getEmail(),
                fullName
        );
    }

    /**
     * Yolcu kaydı — sadece USER rolü atanır.
     * Admin hesapları seed ile oluşturulur, register endpoint'ten açık değildir.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw BusinessException.conflict("Bu e-posta adresi zaten kayıtlı");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        // Profil oluştur
        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(request.fullName())
                .build();
        userProfileRepository.save(profile);

        UserDetails userDetails = new UserPrincipal(user);
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        log.info("New user registered: {} | id: {}", user.getEmail(), user.getUserId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getRole().name(),
                user.getUserId(),
                user.getEmail(),
                request.fullName()
        );
    }

    // ── Email Doğrulamalı Kayıt (2 adım) ──────────────────────────────────────

    /**
     * Adım 1: Email'e doğrulama kodu gönder.
     * Hesap henüz oluşturulmaz — sadece kod gönderilir.
     */
    @Transactional
    public void sendRegisterCode(SendCodeRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw BusinessException.conflict("Bu e-posta adresi zaten kayıtlı");
        }
        verificationService.generateAndSend(req.email(), "REGISTER");
        log.info("Kayıt doğrulama kodu gönderildi: {}", req.email());
    }

    /**
     * Adım 2: Kodu doğrula → başarılıysa User + UserProfile oluştur, JWT dön.
     */
    @Transactional
    public AuthResponse verifyAndRegister(VerifyRegisterRequest req) {
        // Kod doğrula
        boolean valid = verificationService.verify(req.email(), req.code(), "REGISTER");
        if (!valid) {
            throw new BusinessException("Kod hatalı veya süresi dolmuş", HttpStatus.BAD_REQUEST);
        }

        // Kayıt sırasında email tekrar kayıtlı oldu mu?
        if (userRepository.existsByEmail(req.email())) {
            throw BusinessException.conflict("Bu e-posta adresi zaten kayıtlı");
        }

        User user = User.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.USER)
                .isActive(true)
                .emailVerified(true)
                .build();
        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(req.fullName())
                .build();
        userProfileRepository.save(profile);

        UserDetails userDetails = new UserPrincipal(user);
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        log.info("Email doğrulamalı kayıt tamamlandı: {} | id: {}", user.getEmail(), user.getUserId());

        return new AuthResponse(
                accessToken, refreshToken,
                user.getRole().name(),
                user.getUserId(),
                user.getEmail(),
                req.fullName()
        );
    }

    /**
     * Refresh token ile yeni access token üretir.
     * Refresh token türü ve süresi kontrol edilir.
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw new BusinessException("Geçersiz veya süresi dolmuş refresh token",
                    HttpStatus.UNAUTHORIZED);
        }

        String email = jwtService.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        String newAccessToken = jwtService.generateAccessToken(userDetails);

        UserPrincipal principal = (UserPrincipal) userDetails;
        String fullName = userProfileRepository.findByUserUserId(principal.getUserId())
                .map(UserProfile::getFullName)
                .orElse(null);

        return new AuthResponse(
                newAccessToken,
                refreshToken,          // Refresh token değişmiyor
                principal.getRole(),
                principal.getUserId(),
                principal.getEmail(),
                fullName
        );
    }
}
