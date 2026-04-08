package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotBlank;

public record EarnRequest(@NotBlank String action) {}
