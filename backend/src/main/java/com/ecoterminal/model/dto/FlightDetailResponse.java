package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.FlightStatus;

import java.time.Instant;

public record FlightDetailResponse(
        Long flightId,
        String flightCode,
        String airline,
        String iataCode,
        String destination,
        String origin,
        Instant departureTime,
        String gateZoneName,
        Long minutesToDeparture,
        Float gateDensityPct,
        DensityLevel gateDensityLevel,
        String colorCode,
        String seatNumber,
        String flightClass,
        FlightStatus status
) {}
