package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Her zone'un SVG harita üzerindeki konumunu tutar.
 * Koordinatlar 0–100 arası normalize değerler — frontend SVG viewBox'a göre ölçekler.
 */
@Entity
@Table(name = "zone_map_positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneMapPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false, unique = true)
    private Zone zone;

    /** SVG x koordinatı — %0–100 normalize */
    @Column(name = "pos_x", nullable = false)
    private Double posX;

    /** SVG y koordinatı — %0–100 normalize */
    @Column(name = "pos_y", nullable = false)
    private Double posY;

    /** Genişlik — %0–100 normalize */
    @Column(name = "width", nullable = false)
    private Double width;

    /** Yükseklik — %0–100 normalize */
    @Column(name = "height", nullable = false)
    private Double height;

    /** Terminaldeki bölüm: CHECKIN, SECURITY, CONCOURSE_A/B/C, LOUNGE */
    @Column(name = "section", length = 50)
    private String section;

    /** Sidebar/grid sıralama sırası */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}
