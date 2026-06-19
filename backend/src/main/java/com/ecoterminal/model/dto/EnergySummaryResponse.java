package com.ecoterminal.model.dto;

import java.util.List;

/**
 * GET /api/admin/reports/energy/summary — dönem enerji özeti.
 */
public record EnergySummaryResponse(
        double  totalKwh,               // seçili dönem toplam kWh
        double  prevTotalKwh,           // karşılaştırma dönemi toplam kWh
        String  topZoneName,            // en çok tüketen zone adı
        double  topZoneKwh,             // o zone'un tüketimi
        double  avgTemp,                // dönem ort. sıcaklık (°C)
        double  avgLux,                 // dönem ort. aydınlatma (lux)
        long    settingChanges,         // audit_logs'taki ENERGY_SETTING sayısı
        boolean hasEnergyData,          // environmental_metrics'te veri var mı?
        String  insightText,
        // ── Ek istatistikler ──────────────────────────────────────────────────
        double  last24hKwh,             // son 24 saat toplam kWh (anlık)
        List<ZoneEnergyDetail>      zoneBreakdown,          // zone bazlı detaylı liste (min/max/kritik)
        List<PeakHourEntry>         topPeakHours,           // top 5 pik saat
        List<SavingsOpportunity>    savingsOpportunities,   // yüksek enerji+düşük doluluk eşleşmesi
        int     dataZoneCount,                              // kaç zone'da enerji verisi var
        List<EnergyDailyTrendPoint> dailyTrend              // gün bazlı ortalama kWh trendi
) {
    public record PeakHourEntry(int hour, double avgKwh) {}
    public record SavingsOpportunity(String zoneName, double avgKwh, double avgDensityPct) {}
    public record ZoneEnergyDetail(
            String zoneName,
            double avgKwh,
            double maxKwh,
            double minKwh,
            long   criticalCount,
            long   readings
    ) {}
    public record EnergyDailyTrendPoint(String date, double avgKwh) {}
}
