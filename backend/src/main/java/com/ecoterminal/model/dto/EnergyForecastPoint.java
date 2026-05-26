package com.ecoterminal.model.dto;

/**
 * Enerji tahmin sekmesinde bir zaman dilimine ait nokta.
 * primaryValue   = energy_kwh
 * secondaryValue = lighting_lux
 * status         = YÜKSEK | NORMAL | DÜŞÜK
 */
public record EnergyForecastPoint(
        String label,
        double primaryValue,
        double secondaryValue,
        String status
) {}
