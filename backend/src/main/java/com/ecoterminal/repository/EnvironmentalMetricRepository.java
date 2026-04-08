package com.ecoterminal.repository;

import com.ecoterminal.model.entity.EnvironmentalMetric;
import com.ecoterminal.model.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentalMetricRepository extends JpaRepository<EnvironmentalMetric, Long> {

    /** Bir bölgenin en son enerji okuması */
    Optional<EnvironmentalMetric> findTopByZoneOrderByRecordedAtDesc(Zone zone);

    /** Belirli zaman aralığındaki okumalar (trend grafik için) */
    List<EnvironmentalMetric> findByZoneAndRecordedAtBetweenOrderByRecordedAtAsc(
            Zone zone, Instant start, Instant end);

    /** Bir bölgenin tüm enerji geçmişi (rapor için) */
    List<EnvironmentalMetric> findByZone_ZoneId(Long zoneId);

    /**
     * Tüm bölgelerin en son enerji okumasını tek sorguda getirir (N+1 yok).
     */
    @Query("""
            SELECT em FROM EnvironmentalMetric em
            JOIN FETCH em.zone z
            WHERE em.recordedAt = (
                SELECT MAX(em2.recordedAt) FROM EnvironmentalMetric em2
                WHERE em2.zone.zoneId = em.zone.zoneId
            )
            ORDER BY z.zoneId ASC
            """)
    List<EnvironmentalMetric> findLatestPerZone();

    /**
     * Tüm bölgelerin belirli zaman aralığındaki enerji verileri — günlük rapor için.
     */
    @Query("""
            SELECT em FROM EnvironmentalMetric em
            JOIN FETCH em.zone
            WHERE em.recordedAt >= :start AND em.recordedAt < :end
            ORDER BY em.recordedAt ASC
            """)
    List<EnvironmentalMetric> findAllInRange(@Param("start") Instant start,
                                              @Param("end") Instant end);
}
