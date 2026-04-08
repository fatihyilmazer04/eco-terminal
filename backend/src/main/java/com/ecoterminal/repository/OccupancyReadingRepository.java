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
     * N+1 sorgu problemini önler — heatmap endpoint için kritik.
     */
    @Query("""
            SELECT r FROM OccupancyReading r
            WHERE r.recordedAt = (
                SELECT MAX(r2.recordedAt)
                FROM OccupancyReading r2
                WHERE r2.zone = r.zone
            )
            ORDER BY r.zone.zoneId ASC
            """)
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
}
