package com.ecoterminal.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * PATCH /api/energy/zones/{id}/settings isteği.
 * Hedef sıcaklık ve aydınlatma seviyesini günceller.
 */
public record EnergySettingRequest(
        @DecimalMin("10.0") @DecimalMax("35.0") Float targetTemp,    // °C
        @Min(0) @Max(1000)  Integer targetLightingLux               // lux
) {}
