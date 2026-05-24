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
     * DISTINCT ON garantili olarak zone başına tek satır döner.
     */
    @Query(value = """
            SELECT DISTINCT ON (p.zone_id) p.*
            FROM ai_predictions p
            JOIN zones z ON z.zone_id = p.zone_id
            WHERE z.status = 'ACTIVE'
            ORDER BY p.zone_id, p.generated_at DESC
            """, nativeQuery = true)
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
