package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ChangePasswordRequest;
import com.ecoterminal.model.dto.UpdateUserRequest;
import com.ecoterminal.model.dto.UserListResponse;
import com.ecoterminal.model.entity.Role;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository    userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final AuditLogService   auditLogService;

    // ── Admin işlemleri ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserListResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(User::getCreatedAt).reversed())
                .map(UserListResponse::from)
                .toList();
    }

    @Transactional
    public UserListResponse updateUser(Long targetId, UpdateUserRequest request, Long actorId) {
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        String oldVal = String.format("{\"role\":\"%s\",\"is_active\":%b}", user.getRole(), user.getIsActive());

        if (request.role() != null) {
            try {
                user.setRole(Role.valueOf(request.role().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("Geçersiz rol: " + request.role());
            }
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        userRepository.save(user);

        String newVal = String.format("{\"role\":\"%s\",\"is_active\":%b}", user.getRole(), user.getIsActive());
        auditLogService.log(actorId, "USER_UPDATE", "users", targetId, oldVal, newVal);

        log.info("Admin {} kullanıcı {} güncelledi: {}", actorId, targetId, newVal);
        return UserListResponse.from(user);
    }

    // ── Şifre değiştirme (oturum açmış kullanıcı) ────────────────────────────

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("Mevcut şifre hatalı");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Kullanıcı {} şifresini değiştirdi", userId);
    }
}
