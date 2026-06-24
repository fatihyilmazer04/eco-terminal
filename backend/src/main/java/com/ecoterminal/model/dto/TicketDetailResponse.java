package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.SeatClass;

import java.time.Instant;

public record TicketDetailResponse(
        Long ticketId,
        String pnrCode,
        String passengerName,
        String ticketStatus,
        String seatNumber,
        SeatClass seatClass,
        Long userId,
        String userEmail,
        Long flightId,
        String flightCode,
        String airline,
        String iataCode,
        String destination,
        String origin,
        Instant departureTime,
        String gateName,
        Instant bookedAt
) {}
