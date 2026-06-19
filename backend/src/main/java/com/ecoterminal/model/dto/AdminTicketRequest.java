package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.SeatClass;
import jakarta.validation.constraints.NotNull;

public record AdminTicketRequest(
        @NotNull Long flightId,
        @NotNull String seatNumber,
        @NotNull SeatClass seatClass,
        String passengerName
) {}
