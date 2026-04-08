package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ZoneType type;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    /**
     * Kritik doluluk eşiği — bu değerin üzerine çıkıldığında alarm tetiklenir.
     * Bölgeye göre farklılaşır: Lounge daha düşük, Gate daha yüksek toleranslı.
     */
    @Column(name = "critical_threshold", nullable = false)
    @Builder.Default
    private Float criticalThreshold = 0.85f;

    @Column(name = "geo_coords", length = 100)
    private String geoCoords;

    @Column(name = "floor_level")
    @Builder.Default
    private Integer floorLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ZoneStatus status = ZoneStatus.ACTIVE;
}
