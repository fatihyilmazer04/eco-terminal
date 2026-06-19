package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.TransactionHistory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record RedemptionResponse(
        Long transId,
        String rewardName,
        String redemptionCode,
        Integer pointsSpent,
        Instant redeemedAt,
        String timeAgo,
        String rewardType
) {
    public static RedemptionResponse from(TransactionHistory t) {
        String rewardName = (t.getReward() != null) ? t.getReward().getTitle() : t.getDescription();
        String rewardType = (t.getReward() != null && t.getReward().getRewardType() != null)
                ? t.getReward().getRewardType().name() : null;
        return new RedemptionResponse(
                t.getTransId(),
                rewardName,
                t.getRedemptionCode(),
                t.getAmount(),
                t.getCreatedAt(),
                timeAgo(t.getCreatedAt()),
                rewardType
        );
    }

    private static String timeAgo(Instant ts) {
        if (ts == null) return "";
        long sec = ChronoUnit.SECONDS.between(ts, Instant.now());
        if (sec < 60)   return sec + " saniye önce";
        long min = sec / 60;
        if (min < 60)   return min + " dakika önce";
        long hr = min / 60;
        if (hr < 24)    return hr + " saat önce";
        return (hr / 24) + " gün önce";
    }
}
