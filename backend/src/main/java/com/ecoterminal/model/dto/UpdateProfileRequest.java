package com.ecoterminal.model.dto;

public record UpdateProfileRequest(
        String fullName,
        String phone,
        String avatarUrl
) {}
