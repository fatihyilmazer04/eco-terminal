package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotNull;

public record RouteCompleteRequest(
        @NotNull(message = "Uçuş ID boş olamaz")
        Long flightId
) {}
