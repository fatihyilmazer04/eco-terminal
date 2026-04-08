package com.ecoterminal.model.dto;

/**
 * Enerji tasarrufu önerisi.
 * Koşul: doluluk < 0.20 VE energy_kwh > 20.0
 */
public record SavingSuggestion(
    Long zoneId,
    String zoneName,
    Float currentDensity,
    Float currentEnergyKwh,
    String suggestion,
    int potentialSavingPct
) {}
