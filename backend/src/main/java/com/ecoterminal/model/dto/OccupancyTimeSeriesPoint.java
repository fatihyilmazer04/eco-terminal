package com.ecoterminal.model.dto;

/**
 * Grafik için tek bir zaman dilimindeki doluluk + enerji verisi.
 * ZoneDetailPanel'in 24 saatlik ComposedChart'ı için kullanılır.
 */
public record OccupancyTimeSeriesPoint(
        String time,           // "HH:mm" formatında, x ekseni
        double densityPct,     // 0.0–1.0 arası doluluk oranı
        int peopleCount,       // anlık kişi sayısı
        Double energyKwh,      // enerji tüketimi (kWh) — nullable
        Double temperature,    // sıcaklık (°C) — nullable
        Integer lightingLevel  // aydınlatma (lux) — nullable
) {
    /** Sadece yoğunluk verisi olan nokta (enerji verisi yok) */
    public static OccupancyTimeSeriesPoint occupancyOnly(String time, double densityPct, int peopleCount) {
        return new OccupancyTimeSeriesPoint(time, densityPct, peopleCount, null, null, null);
    }

    /** Tam veri seti */
    public static OccupancyTimeSeriesPoint full(String time, double densityPct, int peopleCount,
                                                Double energyKwh, Double temperature, Integer lightingLevel) {
        return new OccupancyTimeSeriesPoint(time, densityPct, peopleCount, energyKwh, temperature, lightingLevel);
    }
}
