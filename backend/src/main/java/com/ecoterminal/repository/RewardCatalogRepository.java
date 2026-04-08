package com.ecoterminal.repository;

import com.ecoterminal.model.entity.RewardCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RewardCatalogRepository extends JpaRepository<RewardCatalog, Long> {
    List<RewardCatalog> findByIsActiveTrue();
}
