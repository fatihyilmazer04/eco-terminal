package com.ecoterminal.repository;

import com.ecoterminal.model.entity.AIPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AIPredictionRepository extends JpaRepository<AIPrediction, Long> {

    /** Belirli bölgenin tahminleri, en yeni önce */
    List<AIPrediction> findByZone_ZoneIdOrderByGeneratedAtDesc(Long zoneId);

    /** Risk seviyesine göre tahminler, en yeni önce */
    List<AIPrediction> findByRiskLevelOrderByGeneratedAtDesc(String riskLevel);

    /** Son 10 tahmin */
    List<AIPrediction> findTop10ByOrderByGeneratedAtDesc();

    /**
     * Her bölgenin en son tahmini — tek sorguda (N+1 yok).
     * Admin dashboard ve scheduled refresh için.
     */
    @Query("""
            SELECT p FROM AIPrediction p
            JOIN FETCH p.zone z
            WHERE p.generatedAt = (
                SELECT MAX(p2.generatedAt) FROM AIPrediction p2
                WHERE p2.zone.zoneId = p.zone.zoneId
            )
            ORDER BY z.zoneId ASC
            """)
    List<AIPrediction> findLatestPerZone();

    /**
     * Belirli bir bölgenin son N dakika içindeki en son tahmini.
     * Cache kontrolü için kullanılır.
     */
    @Query("""
            SELECT p FROM AIPrediction p
            WHERE p.zone.zoneId = :zoneId
              AND p.generatedAt >= :since
            ORDER BY p.generatedAt DESC
            """)
    List<AIPrediction> findRecentByZoneId(@Param("zoneId") Long zoneId,
                                           @Param("since") Instant since);
}
