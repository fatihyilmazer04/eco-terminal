package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.User;

import java.time.Instant;

/**
 * GET /api/admin/users yanıtında dönen kullanıcı özeti.
 */
public record UserListResponse(
        Long userId,
        String email,
        String fullName,        // user_profiles.first_name + last_name (null ise profil yok)
        String role,
        Boolean isActive,
        Instant lastLogin,
        Instant createdAt
) {
    public static UserListResponse from(User u) {
        return new UserListResponse(
                u.getUserId(),
                u.getEmail(),
                null,
                u.getRole().name(),
                u.getIsActive(),
                u.getLastLogin(),
                u.getCreatedAt()
        );
    }

    public static UserListResponse from(User u, String fullName) {
        return new UserListResponse(
                u.getUserId(),
                u.getEmail(),
                fullName,
                u.getRole().name(),
                u.getIsActive(),
                u.getLastLogin(),
                u.getCreatedAt()
        );
    }
}
