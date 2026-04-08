package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.TransactionHistory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record TransactionResponse(
        Long transId,
        int amount,
        String transType,
        String description,
        String rewardTitle,
        Instant createdAt,
        String timeAgo
) {
    public static TransactionResponse from(TransactionHistory t) {
        String rewardTitle = (t.getReward() != null) ? t.getReward().getTitle() : null;
        return new TransactionResponse(
                t.getTransId(),
                t.getAmount(),
                t.getTransType().name(),
                t.getDescription(),
                rewardTitle,
                t.getCreatedAt(),
                timeAgo(t.getCreatedAt())
        );
    }

    private static String timeAgo(Instant ts) {
        if (ts == null) return "";
        long sec = ChronoUnit.SECONDS.between(ts, Instant.now());
        if (sec < 60)        return sec + " saniye önce";
        long min = sec / 60;
        if (min < 60)        return min + " dakika önce";
        long hr = min / 60;
        if (hr < 24)         return hr + " saat önce";
        return (hr / 24) + " gün önce";
    }
}
