package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.EcoWallet;
import com.ecoterminal.model.entity.TierLevel;

public record WalletResponse(
        Long walletId,
        int currentBalance,
        String tierLevel,
        String tierName,
        int pointsToNextTier,
        String nextTierName,
        float progressPct
) {
    private static final int GOLD_THRESHOLD     = 500;
    private static final int PLATINUM_THRESHOLD = 1500;

    public static WalletResponse from(EcoWallet w) {
        int balance   = w.getCurrentBalance();
        TierLevel tier = w.getTierLevel();

        String tierName;
        String nextTierName;
        int    pointsToNext;
        float  progress;

        switch (tier) {
            case GREEN -> {
                tierName      = "Green Member";
                nextTierName  = "Gold Member";
                pointsToNext  = Math.max(0, GOLD_THRESHOLD - balance);
                progress      = Math.min(100f, balance / (float) GOLD_THRESHOLD * 100f);
            }
            case GOLD -> {
                tierName      = "Gold Member";
                nextTierName  = "Platinum Member";
                pointsToNext  = Math.max(0, PLATINUM_THRESHOLD - balance);
                progress      = Math.min(100f, (balance - GOLD_THRESHOLD)
                                / (float)(PLATINUM_THRESHOLD - GOLD_THRESHOLD) * 100f);
            }
            default -> {      // PLATINUM
                tierName      = "Platinum Member";
                nextTierName  = null;
                pointsToNext  = 0;
                progress      = 100f;
            }
        }

        return new WalletResponse(
                w.getWalletId(),
                balance,
                tier.name(),
                tierName,
                pointsToNext,
                nextTierName,
                progress
        );
    }
}
