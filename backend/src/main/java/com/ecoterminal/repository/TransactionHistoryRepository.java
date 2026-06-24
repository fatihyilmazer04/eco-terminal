package com.ecoterminal.repository;

import com.ecoterminal.model.entity.TransType;
import com.ecoterminal.model.entity.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {

    List<TransactionHistory> findByWallet_WalletIdOrderByCreatedAtDesc(Long walletId);

    List<TransactionHistory> findByWallet_WalletIdAndTransType(Long walletId, TransType transType);

    /** Kullanıcının tüm redemption kodları (redemption_code NULL olmayanlar) */
    List<TransactionHistory> findByWallet_WalletIdAndRedemptionCodeIsNotNullOrderByCreatedAtDesc(Long walletId);
}
