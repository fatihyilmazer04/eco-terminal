package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.EnvironmentalMetric;

import java.time.Instant;

/**
 * Tek bölgenin anlık enerji + verimlilik durumu.
 * efficiencyStatus:
 *   WASTEFUL  → doluluk < 0.20 VE energy_kwh > 20.0 (boş bölge çok enerji harcıyor)
 *   EFFICIENT → yüksek doluluk, enerji kullanımı haklı
 *   NORMAL    → diğer durumlar
 */
public record EnergyResponse(
    Long zoneId,
    String zoneName,
    Float energyKwh,
    Float temp,
    Integer lightingLux,
    Float densityPct,
    String efficiencyStatus,
    Instant recordedAt
) {
    public static String computeStatus(float density, float kwh) {
        if (density < 0.20f && kwh > 20.0f) return "WASTEFUL";
        if (density >= 0.60f)               return "EFFICIENT";
        return "NORMAL";
    }

    public static EnergyResponse from(EnvironmentalMetric m, float densityPct) {
        return new EnergyResponse(
            m.getZone().getZoneId(),
            m.getZone().getZoneName(),
            m.getEnergyKwh(),
            m.getTemp(),
            m.getLightingLux(),
            densityPct,
            computeStatus(densityPct, m.getEnergyKwh()),
            m.getRecordedAt()
        );
    }
}
