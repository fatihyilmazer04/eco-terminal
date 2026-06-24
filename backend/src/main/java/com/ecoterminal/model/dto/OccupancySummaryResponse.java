package com.ecoterminal.model.dto;

import java.util.List;

/**
 * GET /api/admin/reports/occupancy/summary — dönem özeti.
 * avgDensity / prevAvgDensity: 0-100 arası yüzde değerleri.
 */
public record OccupancySummaryResponse(
        double avgDensity,              // seçili dönem ort. doluluk (%)
        double prevAvgDensity,          // karşılaştırma dönemi ort. doluluk (%)
        int    peakHour,                // en yoğun saat (0-23)
        double peakHourDensity,         // o saatin ort. doluluk (%)
        List<ZoneStatEntry> topZones,
        long   criticalReadings,        // density_pct >= 0.85 olan okuma sayısı
        String insightText,
        // ── Ek istatistikler ──────────────────────────────────────────────────
        List<ZoneOccupancyDetail> zoneBreakdown,  // tüm zone'ların detaylı tablosu
        List<DailyTrend> dailyTrend,              // gün bazlı doluluk trendi
        List<PeakHourStat> peakHours              // saatlik pik analizi (top 5)
) {
    public record ZoneOccupancyDetail(
            String zoneName,
            double avgPct,
            double maxPct,
            double minPct,
            long   criticalCount,
            long   readings
    ) {}

    public record DailyTrend(String date, double avgPct, long criticalCount) {}

    public record PeakHourStat(int hour, double avgPct, long criticalCount) {}
}
