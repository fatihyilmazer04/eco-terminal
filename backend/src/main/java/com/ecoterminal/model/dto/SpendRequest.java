package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotNull;

public record SpendRequest(@NotNull Long rewardId) {}
