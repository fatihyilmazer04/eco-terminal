package com.ecoterminal.repository;

import com.ecoterminal.model.entity.ZoneMapPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneMapPositionRepository extends JpaRepository<ZoneMapPosition, Long> {

    Optional<ZoneMapPosition> findByZone_ZoneId(Long zoneId);

    Optional<ZoneMapPosition> findByZone_ZoneName(String zoneName);

    /**
     * Aktif zone'ların tüm harita pozisyonlarını tek sorguda döndürür.
     * Zone JOIN ile aktif olmayanları filtreler.
     */
    @Query("""
            SELECT p FROM ZoneMapPosition p
            JOIN FETCH p.zone z
            WHERE z.status = com.ecoterminal.model.entity.ZoneStatus.ACTIVE
            ORDER BY p.displayOrder ASC
            """)
    List<ZoneMapPosition> findAllActiveZonePositions();
}
