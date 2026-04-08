package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Kamera/sensör kaynaklı anlık yolcu yoğunluğu okuması.
 * recorded_at: @CreationTimestamp ile otomatik set edilir.
 *
 * density_pct = people_count / zone.maxCapacity
 * Bu hesaplama IoT servisi tarafından yapılır ve kayıt edilir.
 */
@Entity
@Table(
    name = "occupancy_readings",
    indexes = {
        @Index(name = "idx_occupancy_zone_time", columnList = "zone_id, recorded_at DESC"),
        @Index(name = "idx_occupancy_recorded_at", columnList = "recorded_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupancyReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reading_id")
    private Long readingId;

    /**
     * EAGER fetch: OccupancyReading her okunduğunda zone bilgisi de gerekli.
     * Heatmap sorgusunda her reading için ayrı zone sorgusu yapılmasını önler.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** IoT cihaz referansı — soft FK, cihaz silinse bile reading korunur */
    @Column(name = "source_device_id")
    private Long sourceDeviceId;

    @Column(name = "people_count", nullable = false)
    private Integer peopleCount;

    /** 0.0 ile 1.0 arasında — people_count / zone.maxCapacity */
    @Column(name = "density_pct", nullable = false)
    private Float densityPct;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
