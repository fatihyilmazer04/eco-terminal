package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/occupancy/redirect isteği.
 * Admin tarafından yoğun zone'dan alternatif zone'a yolcu yönlendirme mesajı.
 */
public record RedirectRequest(
        @NotNull Long fromZoneId,
        @NotNull Long toZoneId,
        @NotNull @Size(min = 5, max = 500) String message
) {}
