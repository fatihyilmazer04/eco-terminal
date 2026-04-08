package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManualNotificationRequest(
        @NotNull Long userId,
        @NotBlank String title,
        @NotBlank String body,
        @NotNull NotificationType type
) {}
