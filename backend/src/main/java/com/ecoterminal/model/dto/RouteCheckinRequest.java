package com.ecoterminal.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RouteCheckinRequest(
        @NotNull(message = "Uçuş ID boş olamaz")
        Long flightId,

        @NotNull(message = "Adım numarası boş olamaz")
        @Min(value = 1, message = "Adım numarası en az 1 olmalıdır")
        Integer stepNumber,

        @NotBlank(message = "Bölge adı boş olamaz")
        String zoneName,

        @NotNull(message = "Toplam adım sayısı boş olamaz")
        @Min(value = 1)
        Integer totalSteps
) {}
