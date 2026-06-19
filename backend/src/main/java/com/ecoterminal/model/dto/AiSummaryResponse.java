package com.ecoterminal.model.dto;

import java.util.List;

/**
 * GET /api/admin/reports/ai-summary — AI tahmin özet raporu.
 */
public record AiSummaryResponse(
        long   totalPredictions,
        RiskDistribution riskDistribution,
        double avgConfidence,           // 0-100 (yüzde)
        boolean hasConfidenceData,
        List<ZoneRiskEntry> topRiskyZones,    // HIGH sayısına göre top 5
        List<DayCount>      predictionsByDay, // günlük HIGH alarm sayısı
        String comparisonText
) {
    public record RiskDistribution(
            long   high,
            long   medium,
            long   low,
            double highPct,
            double mediumPct,
            double lowPct
    ) {}

    public record ZoneRiskEntry(String zoneName, long highCount) {}

    public record DayCount(String date, long highCount) {}
}
