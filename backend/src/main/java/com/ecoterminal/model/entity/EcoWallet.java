package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "eco_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcoWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Long walletId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "current_balance", nullable = false)
    @Builder.Default
    private Integer currentBalance = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier_level", nullable = false, length = 20)
    @Builder.Default
    private TierLevel tierLevel = TierLevel.GREEN;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private Instant lastUpdated;
}
