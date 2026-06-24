package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.FlightStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AdminFlightRequest(
        @NotBlank String flightCode,
        @NotNull Long airlineId,
        @NotBlank String destination,
        @NotBlank String origin,
        @NotNull Instant departureTime,
        Instant arrivalTime,
        Long gateId,
        FlightStatus status
) {}
