package com.ecoterminal.model.dto;

public record ProfileResponse(
        Long userId,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        String role,
        WalletResponse wallet,
        PreferencesResponse preferences
) {}
