package com.ecoterminal.model.dto;

import java.time.Instant;

/**
 * Tek bir zaman noktasındaki enerji değeri — trend grafikleri için.
 */
public record EnergyTrendPoint(
    Instant timestamp,
    Float energyKwh,
    Float temp
) {}
