package com.ecoterminal.model.dto;

import java.util.List;

/**
 * Admin ana ekranı özet verisi.
 */
public record AdminDashboardResponse(
    int totalPassengers,         // tüm bölgelerdeki toplam kişi (anlık)
    int criticalZoneCount,       // density >= 0.85 olan bölge sayısı
    Float averageDensityPct,     // tüm bölge yoğunluk ortalaması
    Float totalEnergyKwh,        // tüm bölgeler toplam enerji
    int activeFlightCount,       // SCHEDULED + BOARDING uçuş sayısı
    int savingSuggestionCount,   // WASTEFUL bölge sayısı
    List<ZoneOccupancyResponse> zoneOccupancies
) {}
