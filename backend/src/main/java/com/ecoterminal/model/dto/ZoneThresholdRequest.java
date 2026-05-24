package com.ecoterminal.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Zone kritik doluluk eşiği güncelleme isteği.
 */
public record ZoneThresholdRequest(
        @NotNull
        @DecimalMin("0.1") @DecimalMax("1.0")
        Float criticalThreshold
) {}
