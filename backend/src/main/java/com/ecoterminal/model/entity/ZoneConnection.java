package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Zone'lar arası bağlantı (graph edge).
 * V25 migration'ında oluşturulan zone_connections tablosunun JPA karşılığı.
 * Dijkstra algoritması bu entity üzerinden çalışacak.
 *
 * Her bağlantı tek yönlüdür (from → to). Çift yönlü trafik için her
 * iki yön de ayrı satır olarak DB'de tutulur (V25 seed verisinde
 * NOT EXISTS ile otomatik ters yön ekleniyor).
 */
@Entity
@Table(name = "zone_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Edge'in başlangıç zone'u. LAZY fetch — graph build sırasında
     * eager yüklemek için JOIN FETCH kullanılacak.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_zone_id", nullable = false)
    private Zone fromZone;

    /**
     * Edge'in bitiş zone'u.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_zone_id", nullable = false)
    private Zone toZone;

    /**
     * Bu edge üzerindeki yürüme mesafesi (metre).
     * Dijkstra'da "shortest distance" stratejisinde kullanılır.
     */
    @Column(name = "distance_meters", nullable = false)
    private Integer distanceMeters;

    /**
     * Bu edge'i yürüyerek geçme süresi (saniye).
     * Dijkstra'da "shortest time" stratejisinde primary weight olarak
     * kullanılır. Walking speed ~80 m/dk varsayımıyla hesaplandı.
     */
    @Column(name = "walk_time_seconds", nullable = false)
    private Integer walkTimeSeconds;

    /**
     * Edge'de yürüyen merdiven var mı?
     * LLM açıklamada "asansör veya merdiven kullanabilirsiniz" diyebilir.
     */
    @Builder.Default
    @Column(name = "has_escalator")
    private Boolean hasEscalator = false;

    /**
     * Edge'de asansör var mı? (Accessibility için kritik.)
     */
    @Builder.Default
    @Column(name = "has_elevator")
    private Boolean hasElevator = false;

    /**
     * Edge'de yürüyen bant var mı?
     * Varsa LLM "Yürüyen bandı kullanarak daha az enerji harcayabilirsiniz" der.
     */
    @Builder.Default
    @Column(name = "has_moving_walkway")
    private Boolean hasMovingWalkway = false;

    /**
     * Engelli erişimi var mı? Tekerlekli sandalye/bebek arabası kullanan
     * yolcular için Dijkstra is_accessible=false edge'leri filtreleyebilir.
     */
    @Builder.Default
    @Column(name = "is_accessible")
    private Boolean isAccessible = true;

    /**
     * Edge aktif mi? Bakım, kapatma, vs durumlarda false yapılır,
     * Dijkstra bu edge'i atlar.
     */
    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * İnsan okunabilir açıklama (örn. "Üst koridor - yürüyen bant").
     * LLM açıklama üretirken kullanabilir.
     */
    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @Override
    public String toString() {
        return "ZoneConnection{" +
                "id=" + id +
                ", from=" + (fromZone != null ? fromZone.getZoneName() : "?") +
                ", to="   + (toZone   != null ? toZone.getZoneName()   : "?") +
                ", dist=" + distanceMeters + "m" +
                ", time=" + walkTimeSeconds + "s" +
                '}';
    }
}
