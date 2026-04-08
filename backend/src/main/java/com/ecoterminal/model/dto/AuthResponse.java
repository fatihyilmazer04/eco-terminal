package com.ecoterminal.model.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String role,
        Long userId,
        String email,
        String fullName
) {}
