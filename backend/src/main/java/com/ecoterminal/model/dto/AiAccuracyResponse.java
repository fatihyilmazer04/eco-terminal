package com.ecoterminal.model.dto;

/**
 * GET /api/admin/reports/ai-accuracy
 * ai_predictions.predicted_load vs occupancy_readings.density_pct karşılaştırması.
 * Aynı zone + ±5 dakika toleransla eşleştirme yapılır.
 */
public record AiAccuracyResponse(
        double mae,            // ortalama mutlak hata (0.0–1.0 ölçeği)
        double maePct,         // yüzde olarak MAE (0–100)
        double correlation,    // Pearson korelasyon katsayısı (-1 ile 1 arası)
        long   matchedPairs,   // eşleşen tahmin–ölçüm çifti sayısı
        String accuracyText    // Türkçe açıklama cümlesi
) {}
