package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.DensityLevel;

public record LoungeResponse(
        Long zoneId,
        String zoneName,
        Float densityPct,
        DensityLevel densityLevel,
        int comfortScore,
        String colorCode,
        boolean isRecommended,
        String suggestion
) {
    public static LoungeResponse from(ZoneOccupancyResponse z) {
        Float density  = z.densityPct() != null ? z.densityPct() : 0f;
        int comfort    = comfortScore(density);
        boolean recommended = density < 0.40f;
        String suggestion   = buildSuggestion(density);

        return new LoungeResponse(
                z.zoneId(),
                z.zoneName(),
                density,
                z.densityLevel(),
                comfort,
                z.colorCode(),
                recommended,
                suggestion
        );
    }

    public static int comfortScore(float density) {
        if (density < 0.20f) return 5;
        if (density < 0.40f) return 4;
        if (density < 0.60f) return 3;
        if (density < 0.75f) return 2;
        return 1;
    }

    private static String buildSuggestion(float density) {
        if (density < 0.30f) return "Sakin ve konforlu";
        if (density < 0.50f) return "Uygun kapasite";
        return "Dolmaya başlıyor";
    }
}
