package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.RewardCatalog;

public record RewardResponse(
        Long rewardId,
        String title,
        String description,
        int costPoints,
        String rewardType,
        boolean canAfford
) {
    public static RewardResponse from(RewardCatalog r, int userBalance) {
        return new RewardResponse(
                r.getRewardId(),
                r.getTitle(),
                r.getDescription(),
                r.getCostPoints(),
                r.getRewardType() != null ? r.getRewardType().name() : null,
                userBalance >= r.getCostPoints()
        );
    }
}
