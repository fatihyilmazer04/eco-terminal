package com.ecoterminal.repository;

import com.ecoterminal.model.entity.ZoneConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ZoneConnection (graph edges).
 *
 * Bu interface, Dijkstra algoritmasının veri ihtiyaçlarını karşılar:
 *   1. Tüm aktif edge'leri yüklemek (graph build için)
 *   2. Bir zone'dan çıkan edge'leri bulmak (neighbor lookup)
 *   3. Specific edge'i bulmak (from→to)
 *
 * Performans Notu: findAllActiveWithZones() metodu JOIN FETCH kullanır,
 * böylece Dijkstra çalışırken Zone entity'lerinin lazy loading sorunu
 * (N+1 query problem) yaşanmaz. Graph cache 5 dakikada bir bu sorguyu
 * çalıştırır.
 */
@Repository
public interface ZoneConnectionRepository extends JpaRepository<ZoneConnection, Long> {

    /**
     * Tüm aktif edge'leri ZONE entity'leriyle birlikte tek sorguda yükler.
     * JOIN FETCH sayesinde lazy loading proxy'leri yerine gerçek Zone
     * nesneleri döner — Dijkstra graph build için ideal.
     *
     * @return aktif (is_active=true) tüm edge'ler, from/to Zone'lar dahil
     */
    @Query("""
            SELECT zc FROM ZoneConnection zc
            JOIN FETCH zc.fromZone
            JOIN FETCH zc.toZone
            WHERE zc.isActive = true
            """)
    List<ZoneConnection> findAllActiveWithZones();

    /**
     * Belirli bir zone'dan ÇIKAN tüm aktif edge'leri bulur.
     * Dijkstra "neighbor expansion" adımında kullanılır.
     *
     * @param fromZoneId başlangıç zone'unun zoneId değeri
     * @return o zone'dan çıkan aktif edge'ler
     */
    @Query("""
            SELECT zc FROM ZoneConnection zc
            JOIN FETCH zc.toZone
            WHERE zc.fromZone.zoneId = :fromZoneId
              AND zc.isActive = true
            """)
    List<ZoneConnection> findActiveEdgesFromZone(@Param("fromZoneId") Long fromZoneId);

    /**
     * Specific bir edge'i bulur (from → to).
     * Edge metadata (mesafe, süre, escalator vb.) sorgulamak için.
     *
     * @param fromZoneId başlangıç zone ID
     * @param toZoneId   hedef zone ID
     * @return edge varsa tek elemanlı liste, yoksa boş liste
     */
    @Query("""
            SELECT zc FROM ZoneConnection zc
            WHERE zc.fromZone.zoneId = :fromZoneId
              AND zc.toZone.zoneId = :toZoneId
              AND zc.isActive = true
            """)
    List<ZoneConnection> findEdge(@Param("fromZoneId") Long fromZoneId,
                                   @Param("toZoneId") Long toZoneId);

    /**
     * Aktif edge sayısı (health check ve graph stats için).
     */
    long countByIsActiveTrue();
}
