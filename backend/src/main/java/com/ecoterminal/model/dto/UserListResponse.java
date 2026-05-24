package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.User;

import java.time.Instant;

/**
 * GET /api/admin/users yanıtında dönen kullanıcı özeti.
 */
public record UserListResponse(
        Long userId,
        String email,
        String role,
        Boolean isActive,
        Instant lastLogin,
        Instant createdAt
) {
    public static UserListResponse from(User u) {
        return new UserListResponse(
                u.getUserId(),
                u.getEmail(),
                u.getRole().name(),
                u.getIsActive(),
                u.getLastLogin(),
                u.getCreatedAt()
        );
    }
}
