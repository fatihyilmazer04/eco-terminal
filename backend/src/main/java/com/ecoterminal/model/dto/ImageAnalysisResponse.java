package com.ecoterminal.model.dto;

import java.util.List;

/**
 * POST /api/zones/{zoneId}/analyze-image — yanıt gövdesi.
 * YOLOv8 servisinden gelen sonucu zone meta verisiyle zenginleştirir.
 */
public record ImageAnalysisResponse(
        Long    zoneId,
        String  zoneName,
        Integer capacity,
        Integer peopleCount,
        Double  densityPct,
        String  riskLevel,
        String  source,
        String  timestamp,
        List<Detection> detections,
        Double  predictedLoad   // AI tahmini; AI servis unavailable ise null
) {

    /**
     * Tek bir kişi tespiti: bounding box [x1, y1, x2, y2] + güven skoru.
     */
    public record Detection(
            List<Double> bbox,
            Double confidence
    ) {}

    /** Yoğunluk → risk seviyesi dönüşümü */
    public static String toRiskLevel(double densityPct) {
        if (densityPct >= 0.85) return "CRITICAL";
        if (densityPct >= 0.60) return "HIGH";
        if (densityPct >= 0.30) return "MEDIUM";
        return "LOW";
    }
}
