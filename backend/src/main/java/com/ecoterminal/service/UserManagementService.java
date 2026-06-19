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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository    userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final AuditLogService   auditLogService;
    private final JdbcTemplate      jdbc;

    // ── Admin işlemleri ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserListResponse> getAllUsers() {
        // user_profiles JOIN ile ad/soyad haritası
        Map<Long, String> nameMap;
        try {
            nameMap = jdbc.queryForList(
                    "SELECT u.user_id, NULLIF(TRIM(COALESCE(up.full_name,'')), '') AS full_name " +
                    "FROM users u LEFT JOIN user_profiles up ON u.user_id = up.user_id"
            ).stream().collect(Collectors.toMap(
                    r -> ((Number) r.get("user_id")).longValue(),
                    r -> r.get("full_name") != null ? String.valueOf(r.get("full_name")) : "",
                    (a, b) -> a
            ));
        } catch (Exception e) {
            log.warn("fullName join başarısız, isim boş döner: {}", e.getMessage());
            nameMap = Map.of();
        }
        final Map<Long, String> names = nameMap;

        return userRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(User::getCreatedAt).reversed())
                .map(u -> {
                    String fn = names.getOrDefault(u.getUserId(), "");
                    return UserListResponse.from(u, fn.isBlank() ? null : fn);
                })
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
