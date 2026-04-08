package com.ecoterminal.repository;

import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByStatus(ZoneStatus status);

    List<Zone> findByStatusOrderByZoneNameAsc(ZoneStatus status);
}
