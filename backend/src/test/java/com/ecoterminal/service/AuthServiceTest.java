package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AuthResponse;
import com.ecoterminal.model.dto.LoginRequest;
import com.ecoterminal.model.dto.RegisterRequest;
import com.ecoterminal.model.entity.Role;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.model.entity.UserProfile;
import com.ecoterminal.repository.UserProfileRepository;
import com.ecoterminal.repository.UserRepository;
import com.ecoterminal.security.CustomUserDetailsService;
import com.ecoterminal.security.JwtService;
import com.ecoterminal.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository           userRepository;
    @Mock private UserProfileRepository    userProfileRepository;
    @Mock private PasswordEncoder          passwordEncoder;
    @Mock private JwtService               jwtService;
    @Mock private AuthenticationManager    authenticationManager;
    @Mock private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("test@eco.com")
                .passwordHash("$2b$12$hashedpassword")
                .role(Role.USER)
                .isActive(true)
                .build();

        testProfile = UserProfile.builder()
                .user(testUser)
                .fullName("Test Kullanıcı")
                .build();
    }

    // ── Login Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("login_withValidCredentials_returnsAuthResponse")
    void login_withValidCredentials_returnsAuthResponse() {
        // given
        LoginRequest req = new LoginRequest("test@eco.com", "pass123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("test@eco.com")).thenReturn(Optional.of(testUser));
        doNothing().when(userRepository).updateLastLogin(any(), any());
        when(jwtService.generateAccessToken(any())).thenReturn("access.token.value");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh.token.value");
        when(userProfileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));

        // when
        AuthResponse response = authService.login(req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.accessToken()).isEqualTo("access.token.value");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.email()).isEqualTo("test@eco.com");
    }

    @Test
    @DisplayName("login_withInvalidPassword_throwsException")
    void login_withInvalidPassword_throwsException() {
        // given
        LoginRequest req = new LoginRequest("test@eco.com", "wrongpassword");
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        // when/then
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @DisplayName("login_withNonExistentUser_throwsException")
    void login_withNonExistentUser_throwsException() {
        // given
        LoginRequest req = new LoginRequest("unknown@eco.com", "pass123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("unknown@eco.com")).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    // ── Register Tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("register_withExistingEmail_throwsException")
    void register_withExistingEmail_throwsException() {
        // given
        RegisterRequest req = new RegisterRequest("test@eco.com", "pass123", "Ad Soyad");
        when(userRepository.existsByEmail("test@eco.com")).thenReturn(true);

        // when/then
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("register_withValidData_createsUserAndProfile")
    void register_withValidData_createsUserAndProfile() {
        // given
        RegisterRequest req = new RegisterRequest("new@eco.com", "pass123", "Yeni Kullanıcı");
        when(userRepository.existsByEmail("new@eco.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("$2b$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(testProfile);
        when(jwtService.generateAccessToken(any())).thenReturn("access.token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh.token");

        // when
        AuthResponse response = authService.register(req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access.token");
        verify(userRepository, times(1)).save(any(User.class));
        verify(userProfileRepository, times(1)).save(any(UserProfile.class));
    }

    // ── Refresh Token Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken_withExpiredToken_throwsException")
    void refreshToken_withExpiredToken_throwsException() {
        // given
        String expiredToken = "expired.refresh.token";
        when(jwtService.validateRefreshToken(expiredToken)).thenReturn(false);

        // when/then
        assertThatThrownBy(() -> authService.refreshToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
