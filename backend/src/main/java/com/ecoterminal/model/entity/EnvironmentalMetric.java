package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Bölgesel çevresel ve enerji tüketim verisi.
 * IoT sensörlerden gelen anlık enerji, sıcaklık ve aydınlatma okumaları.
 */
@Entity
@Table(
    name = "environmental_metrics",
    indexes = {
        @Index(name = "idx_env_zone_time", columnList = "zone_id, recorded_at DESC"),
        @Index(name = "idx_env_recorded_at", columnList = "recorded_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvironmentalMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** Anlık enerji tüketimi (kWh) */
    @Column(name = "energy_kwh", nullable = false)
    private Float energyKwh;

    /** Ortam sıcaklığı (°C) */
    @Column(name = "temp")
    private Float temp;

    /** Aydınlatma seviyesi (lux) */
    @Column(name = "lighting_lux")
    private Integer lightingLux;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
