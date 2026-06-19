package com.ecoterminal.model.dto;

import java.util.List;

/**
 * GET /api/admin/reports/users/summary — kullanıcı raporu özeti.
 */
public record UserReportResponse(
        long   totalUsers,
        long   adminCount,
        long   passengerCount,
        long   newUsersInPeriod,
        long   prevNewUsersInPeriod,
        double emailVerifiedRate,
        List<MonthlyCount> newUsersByMonth,
        LoyaltyStats loyaltyStats,
        List<TopEarner> topPointEarners,
        String comparisonText,
        // ── Ek istatistikler ──────────────────────────────────────────────────
        long   activeEarnerCount,       // en az 1 EARN yapan yolcu sayısı
        double activeEarnerRate,        // aktiflik oranı 0-100
        long   repeatEarnerCount,       // birden fazla EARN yapan cüzdan sayısı
        double repeatEarnerRate,        // devam oranı (cüzdan bazlı) 0-100
        long   routeCompletions         // route_completions tablosundaki toplam
) {
    public record MonthlyCount(String month, long count) {}

    public record LoyaltyStats(
            long totalEarned,
            long totalSpent,
            long earnCount,
            long spendCount
    ) {}

    public record TopEarner(String displayName, long totalPoints) {}
}
