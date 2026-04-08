package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoyaltyService Unit Tests")
class LoyaltyServiceTest {

    @Mock private EcoWalletRepository            walletRepo;
    @Mock private TransactionHistoryRepository   txRepo;
    @Mock private RewardCatalogRepository        rewardRepo;
    @Mock private UserRepository                 userRepository;
    @Mock private NotificationService            notifService;

    @InjectMocks
    private LoyaltyService loyaltyService;

    private User testUser;
    private EcoWallet testWallet;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("test@eco.com")
                .role(Role.USER)
                .isActive(true)
                .build();

        testWallet = EcoWallet.builder()
                .walletId(1L)
                .user(testUser)
                .currentBalance(200)
                .tierLevel(TierLevel.GREEN)
                .build();
    }

    // ── addPoints Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("addPoints_updatesBalanceAndSavesTx")
    void addPoints_updatesBalanceAndSavesTx() {
        // given
        when(walletRepo.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifService.sendManual(any())).thenReturn(null);

        // when
        WalletResponse response = loyaltyService.addPoints(1L, 25, "FLIGHT_CHECKIN");

        // then
        assertThat(response.currentBalance()).isEqualTo(225);
        verify(walletRepo, times(1)).save(any(EcoWallet.class));
        verify(txRepo, times(1)).save(any(TransactionHistory.class));
    }

    @Test
    @DisplayName("addPoints_crossingGoldThreshold_updatesTierToGold")
    void addPoints_crossingGoldThreshold_updatesTierToGold() {
        // given — balance=490, +20 = 510 → GOLD
        testWallet.setCurrentBalance(490);
        when(walletRepo.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifService.sendManual(any())).thenReturn(null);

        // when
        WalletResponse response = loyaltyService.addPoints(1L, 20, "Test");

        // then
        assertThat(response.currentBalance()).isEqualTo(510);
        assertThat(response.tierLevel()).isEqualTo("GOLD");
    }

    // ── spendPoints Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("spendPoints_withInsufficientBalance_throwsBadRequest")
    void spendPoints_withInsufficientBalance_throwsBadRequest() {
        // given — balance=50, cost=100
        testWallet.setCurrentBalance(50);
        RewardCatalog reward = RewardCatalog.builder()
                .rewardId(1L)
                .title("Coffee")
                .costPoints(100)
                .isActive(true)
                .build();

        when(walletRepo.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));
        when(rewardRepo.findById(1L)).thenReturn(Optional.of(reward));

        // when/then
        assertThatThrownBy(() -> loyaltyService.spendPoints(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getMessage()).contains("Yetersiz bakiye");
                });
    }

    @Test
    @DisplayName("spendPoints_withSufficientBalance_deductsAndSavesTx")
    void spendPoints_withSufficientBalance_deductsAndSavesTx() {
        // given — balance=200, cost=100
        RewardCatalog reward = RewardCatalog.builder()
                .rewardId(1L)
                .title("Coffee")
                .costPoints(100)
                .isActive(true)
                .build();

        when(walletRepo.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));
        when(rewardRepo.findById(1L)).thenReturn(Optional.of(reward));
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        SpendResponse response = loyaltyService.spendPoints(1L, 1L);

        // then
        assertThat(response.remainingBalance()).isEqualTo(100);
        assertThat(response.rewardTitle()).isEqualTo("Coffee");
        verify(walletRepo, times(1)).save(any(EcoWallet.class));
        verify(txRepo, times(1)).save(any(TransactionHistory.class));
    }

    // ── updateTierLevel Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("addPoints_withPlatinumPoints_setsPlatinum")
    void addPoints_withPlatinumPoints_setsPlatinum() {
        // given — balance=1490, +20 = 1510 → PLATINUM
        testWallet.setCurrentBalance(1490);
        testWallet.setTierLevel(TierLevel.GOLD);
        when(walletRepo.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifService.sendManual(any())).thenReturn(null);

        // when
        WalletResponse response = loyaltyService.addPoints(1L, 20, "Test");

        // then
        assertThat(response.currentBalance()).isEqualTo(1510);
        assertThat(response.tierLevel()).isEqualTo("PLATINUM");
    }
}
