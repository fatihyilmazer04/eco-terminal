package com.ecoterminal.model.dto;

/**
 * Grafik için tek bir zaman dilimindeki doluluk verisi.
 * ZoneDetailPanel'in 24 saatlik Recharts LineChart'ı için kullanılır.
 */
public record OccupancyTimeSeriesPoint(
        String time,        // "HH:mm" formatında, x ekseni
        double densityPct,  // 0.0–1.0 arası doluluk oranı
        int peopleCount     // anlık kişi sayısı
) {}
