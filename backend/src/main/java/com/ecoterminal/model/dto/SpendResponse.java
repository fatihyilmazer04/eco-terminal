package com.ecoterminal.model.dto;

public record SpendResponse(
        String rewardTitle,
        int remainingBalance,
        String tierLevel
) {}
