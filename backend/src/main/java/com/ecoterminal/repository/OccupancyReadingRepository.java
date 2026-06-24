package com.ecoterminal.repository;

import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OccupancyReadingRepository extends JpaRepository<OccupancyReading, Long> {

    /** Bir bölgenin en son yoğunluk okuması */
    Optional<OccupancyReading> findTopByZoneOrderByRecordedAtDesc(Zone zone);

    /** Belirli zaman aralığındaki okumalar (grafik/trend için) */
    List<OccupancyReading> findByZoneAndRecordedAtBetweenOrderByRecordedAtAsc(
            Zone zone, Instant start, Instant end);

    /**
     * Tüm aktif bölgelerin en son okumasını tek sorguda getirir.
     * DISTINCT ON (PostgreSQL) garantili olarak zone başına tek satır döner.
     * N+1 sorgu problemini önler — heatmap endpoint için kritik.
     */
    @Query(value = """
            SELECT DISTINCT ON (o.zone_id) o.*
            FROM occupancy_readings o
            JOIN zones z ON z.zone_id = o.zone_id
            WHERE z.status = 'ACTIVE'
            ORDER BY o.zone_id, o.recorded_at DESC
            """, nativeQuery = true)
    List<OccupancyReading> findLatestPerZone();

    /** Son N dakika içindeki okumalar (dashboard trend kartı için) */
    @Query("""
            SELECT r FROM OccupancyReading r
            WHERE r.zone = :zone
              AND r.recordedAt >= :since
            ORDER BY r.recordedAt ASC
            """)
    List<OccupancyReading> findRecentByZone(@Param("zone") Zone zone,
                                             @Param("since") Instant since);

    /** Tüm bölgelerin belirli zaman aralığındaki okumaları — günlük rapor için */
    @Query("""
            SELECT r FROM OccupancyReading r
            WHERE r.recordedAt >= :start AND r.recordedAt < :end
            ORDER BY r.recordedAt ASC
            """)
    List<OccupancyReading> findAllInRange(@Param("start") Instant start,
                                           @Param("end") Instant end);

    /**
     * Zone adına erişim gereken özet sorgular için JOIN FETCH varyantı.
     * Rapor servisi tarafından kullanılır.
     */
    @Query("""
            SELECT r FROM OccupancyReading r
            JOIN FETCH r.zone
            WHERE r.recordedAt >= :start AND r.recordedAt < :end
            ORDER BY r.recordedAt ASC
            """)
    List<OccupancyReading> findAllInRangeWithZone(@Param("start") Instant start,
                                                   @Param("end") Instant end);

    /**
     * Belirli zone'un son X saat içindeki okumaları — ZoneDetailPanel grafiği için.
     */
    @Query("""
            SELECT r FROM OccupancyReading r
            WHERE r.zone.zoneId = :zoneId
              AND r.recordedAt >= :since
            ORDER BY r.recordedAt ASC
            """)
    List<OccupancyReading> findTimeSeriesByZoneId(@Param("zoneId") Long zoneId,
                                                   @Param("since") Instant since);

    /**
     * Saatlik en güncel doluluk — ZoneHistoryPanel grafiği için.
     * DATE_TRUNC ile her saat için EN SON kaydı döner (max ~24 nokta).
     * AVG yerine DISTINCT ON kullanılır: görüntü analizi sonrası son değer
     * önceki simülasyon ortalamasıyla kirletilmez.
     * Dönen Object[]: [0]=saat_etiketi "HH:MM", [1]=density_pct, [2]=people_count
     */
    @Query(value = """
            SELECT DISTINCT ON (DATE_TRUNC('hour', recorded_at))
                TO_CHAR(DATE_TRUNC('hour', recorded_at), 'HH24:MI') AS hour_label,
                density_pct                                          AS avg_density,
                people_count                                         AS avg_people
            FROM occupancy_readings
            WHERE zone_id = :zoneId
              AND recorded_at >= :since
            ORDER BY DATE_TRUNC('hour', recorded_at), recorded_at DESC
            """, nativeQuery = true)
    List<Object[]> findHourlyAveragesByZoneId(@Param("zoneId") Long zoneId,
                                               @Param("since") Instant since);

    /**
     * Zone'un son N okuması (trend hesaplama için).
     */
    @Query("""
            SELECT r FROM OccupancyReading r
            WHERE r.zone.zoneId = :zoneId
            ORDER BY r.recordedAt DESC
            """)
    List<OccupancyReading> findTopNByZoneId(@Param("zoneId") Long zoneId,
                                             org.springframework.data.domain.Pageable pageable);
}
