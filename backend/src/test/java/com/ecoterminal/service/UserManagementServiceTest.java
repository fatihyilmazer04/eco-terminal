package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ChangePasswordRequest;
import com.ecoterminal.model.dto.UpdateUserRequest;
import com.ecoterminal.model.dto.UserListResponse;
import com.ecoterminal.model.entity.Role;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementService Unit Tests")
class UserManagementServiceTest {

    @Mock private UserRepository    userRepository;
    @Mock private PasswordEncoder   passwordEncoder;
    @Mock private AuditLogService   auditLogService;
    @Mock private JdbcTemplate      jdbc;

    @InjectMocks
    private UserManagementService userManagementService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("$2a$hashed")
                .role(Role.USER)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    // ── getAllUsers Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsers_returnsAllUsers")
    void getAllUsers_returnsAllUsers() {
        // given — JDBC join başarılı, 1 kullanıcı var
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("user_id", 1L, "full_name", "Fatih Yılmaz")
        ));
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        // when
        List<UserListResponse> result = userManagementService.getAllUsers();

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAllUsers_whenJdbcFails_stillReturnsUsers")
    void getAllUsers_whenJdbcFails_stillReturnsUsers() {
        // given — JDBC join başarısız
        when(jdbc.queryForList(anyString())).thenThrow(new RuntimeException("DB bağlantı hatası"));
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        // when — hata yutulmalı
        List<UserListResponse> result = userManagementService.getAllUsers();

        // then — yine de kullanıcı dönmeli
        assertThat(result).hasSize(1);
    }

    // ── updateUser Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateUser_withValidRole_updatesRoleAndAudits")
    void updateUser_withValidRole_updatesRoleAndAudits() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        UpdateUserRequest request = new UpdateUserRequest("ADMIN", null);

        // when
        userManagementService.updateUser(1L, request, 99L);

        // then
        assertThat(testUser.getRole()).isEqualTo(Role.ADMIN);
        verify(auditLogService).log(eq(99L), eq("USER_UPDATE"), eq("users"), eq(1L), any(), any());
    }

    @Test
    @DisplayName("updateUser_withIsActive_updatesActiveStatus")
    void updateUser_withIsActive_updatesActiveStatus() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        UpdateUserRequest request = new UpdateUserRequest(null, false);

        // when
        userManagementService.updateUser(1L, request, 99L);

        // then
        assertThat(testUser.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("updateUser_withInvalidRole_throwsBadRequest")
    void updateUser_withInvalidRole_throwsBadRequest() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        UpdateUserRequest request = new UpdateUserRequest("INVALID_ROLE", null);

        // when / then
        assertThatThrownBy(() -> userManagementService.updateUser(1L, request, 99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("updateUser_withNonExistentUser_throwsNotFound")
    void updateUser_withNonExistentUser_throwsNotFound() {
        // given
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() ->
                userManagementService.updateUser(99L, new UpdateUserRequest("USER", null), 1L))
                .isInstanceOf(BusinessException.class);
    }

    // ── changePassword Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword_withCorrectCurrentPassword_encodesAndSaves")
    void changePassword_withCorrectCurrentPassword_encodesAndSaves() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("eski123", "$2a$hashed")).thenReturn(true);
        when(passwordEncoder.encode("yeni123")).thenReturn("$2a$yeni_hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // when
        userManagementService.changePassword(1L, new ChangePasswordRequest("eski123", "yeni123"));

        // then
        verify(passwordEncoder).encode("yeni123");
        verify(userRepository).save(testUser);
        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$yeni_hashed");
    }

    @Test
    @DisplayName("changePassword_withWrongCurrentPassword_throwsBadRequest")
    void changePassword_withWrongCurrentPassword_throwsBadRequest() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("yanlis", "$2a$hashed")).thenReturn(false);

        // when / then
        assertThatThrownBy(() ->
                userManagementService.changePassword(1L, new ChangePasswordRequest("yanlis", "yeni123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mevcut şifre hatalı");
    }

    @Test
    @DisplayName("changePassword_withNonExistentUser_throwsNotFound")
    void changePassword_withNonExistentUser_throwsNotFound() {
        // given
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() ->
                userManagementService.changePassword(99L, new ChangePasswordRequest("pwd", "newpwd")))
                .isInstanceOf(BusinessException.class);
    }
}
