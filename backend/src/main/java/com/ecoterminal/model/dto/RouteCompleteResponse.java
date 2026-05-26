package com.ecoterminal.model.dto;

public record RouteCompleteResponse(
        int pointsEarned,
        int newBalance,
        String tierLevel
) {}
