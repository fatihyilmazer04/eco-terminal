package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI servisinden gelen yoğunluk tahminleri.
 * Scheduled job tarafından her 5 dakikada bir güncellenir.
 */
@Entity
@Table(
    name = "ai_predictions",
    indexes = {
        @Index(name = "idx_ai_predictions_zone", columnList = "zone_id, generated_at DESC"),
        @Index(name = "idx_ai_predictions_risk",  columnList = "risk_level")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pred_id")
    private Long predId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** Tahmin edilen zaman dilimi */
    @Column(name = "forecast_time", nullable = false)
    private Instant forecastTime;

    /** Tahmini doluluk oranı (0.0-1.0) */
    @Column(name = "predicted_load", nullable = false)
    private Float predictedLoad;

    /** density_pct — predicted_load ile aynı değer (API uyum) */
    @Column(name = "density_pct", nullable = false)
    private Float densityPct;

    /** LOW | MEDIUM | HIGH */
    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    /** INCREASING | DECREASING | STABLE */
    @Column(name = "trend", length = 15)
    @Builder.Default
    private String trend = "STABLE";

    /** Model güven skoru (0.0-1.0) */
    @Column(name = "confidence")
    @Builder.Default
    private Float confidence = 0.75f;

    @Column(name = "model_version", length = 50)
    @Builder.Default
    private String modelVersion = "v1.0-fallback";

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
