package com.ecoterminal.model.dto;

import java.util.List;

public record RouteResponse(
        Long    flightId,
        String  destination,
        String  targetGate,
        Long    minutesToDeparture,
        List<RouteStep> steps,
        Float   averageRouteDensity,
        String  estimatedTotalWalkMinutes,
        boolean alreadyRewarded
) {}
