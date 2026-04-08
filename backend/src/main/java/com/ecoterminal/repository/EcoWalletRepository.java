package com.ecoterminal.repository;

import com.ecoterminal.model.entity.EcoWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EcoWalletRepository extends JpaRepository<EcoWallet, Long> {
    Optional<EcoWallet> findByUser_UserId(Long userId);
}
