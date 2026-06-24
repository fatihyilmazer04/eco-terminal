package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.FlightStatus;

import java.time.Instant;

public record AdminFlightResponse(
        Long flightId,
        String flightCode,
        Long airlineId,
        String airlineName,
        String iataCode,
        String destination,
        String origin,
        Instant departureTime,
        Instant arrivalTime,
        Long gateId,
        String gateName,
        FlightStatus status
) {}
