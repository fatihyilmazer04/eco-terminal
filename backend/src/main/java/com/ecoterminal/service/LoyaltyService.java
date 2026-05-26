package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final EcoWalletRepository       walletRepo;
    private final TransactionHistoryRepository txRepo;
    private final RewardCatalogRepository   rewardRepo;
    private final UserRepository            userRepository;
    private final NotificationService       notifService;

    // ── Cüzdan ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        EcoWallet wallet = getOrCreateWallet(userId);
        return WalletResponse.from(wallet);
    }

    // ── Puan Kazan ────────────────────────────────────────────────────────

    @Transactional
    public WalletResponse addPoints(Long userId, int points, String description) {
        EcoWallet wallet = getOrCreateWallet(userId);
        TierLevel prevTier = wallet.getTierLevel();

        wallet.setCurrentBalance(wallet.getCurrentBalance() + points);
        updateTierLevel(wallet);
        walletRepo.save(wallet);

        // İşlem kaydı
        TransactionHistory tx = TransactionHistory.builder()
                .wallet(wallet)
                .amount(points)
                .transType(TransType.EARN)
                .description(description)
                .build();
        txRepo.save(tx);

        // Bildirim
        notifService.sendManual(new ManualNotificationRequest(
                userId,
                "🌿 Eko-Puan Kazandınız!",
                points + " puan kazandınız. Toplam: " + wallet.getCurrentBalance(),
                NotificationType.REWARD
        ));

        // Tier yükseldiyse bildirim
        if (wallet.getTierLevel() != prevTier) {
            String tierName = switch (wallet.getTierLevel()) {
                case GOLD     -> "Gold Member";
                case PLATINUM -> "Platinum Member";
                default       -> "Green Member";
            };
            notifService.sendManual(new ManualNotificationRequest(
                    userId,
                    "🏆 Tebrikler! " + tierName + " seviyesine ulaştınız!",
                    "Yeni ayrıcalıklarınızı keşfetmek için ödül kataloğunu inceleyin.",
                    NotificationType.REWARD
            ));
            log.info("Tier yükseltme: userId={} → {}", userId, wallet.getTierLevel());
        }

        log.info("Puan eklendi: userId={} +{} puan (toplam={})", userId, points, wallet.getCurrentBalance());
        return WalletResponse.from(wallet);
    }

    // ── Puan Harca ────────────────────────────────────────────────────────

    @Transactional
    public SpendResponse spendPoints(Long userId, Long rewardId) {
        EcoWallet wallet = getOrCreateWallet(userId);
        RewardCatalog reward = rewardRepo.findById(rewardId)
                .orElseThrow(() -> BusinessException.notFound("Ödül"));

        if (!Boolean.TRUE.equals(reward.getIsActive())) {
            throw new BusinessException("Bu ödül artık mevcut değil", HttpStatus.BAD_REQUEST);
        }
        if (wallet.getCurrentBalance() < reward.getCostPoints()) {
            throw new BusinessException(
                    "Yetersiz bakiye. Gereken: " + reward.getCostPoints()
                    + ", Mevcut: " + wallet.getCurrentBalance(),
                    HttpStatus.BAD_REQUEST);
        }

        wallet.setCurrentBalance(wallet.getCurrentBalance() - reward.getCostPoints());
        updateTierLevel(wallet);
        walletRepo.save(wallet);

        TransactionHistory tx = TransactionHistory.builder()
                .wallet(wallet)
                .reward(reward)
                .amount(reward.getCostPoints())
                .transType(TransType.SPEND)
                .description(reward.getTitle() + " ödülü kullanıldı")
                .build();
        txRepo.save(tx);

        log.info("Puan harcandı: userId={} -{} ({})", userId, reward.getCostPoints(), reward.getTitle());
        return new SpendResponse(reward.getTitle(), wallet.getCurrentBalance(), wallet.getTierLevel().name());
    }

    // ── İşlem Geçmişi ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(Long userId) {
        EcoWallet wallet = getOrCreateWallet(userId);
        return txRepo.findByWallet_WalletIdOrderByCreatedAtDesc(wallet.getWalletId())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    // ── Ödül Kataloğu ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RewardResponse> getRewardCatalog(Long userId) {
        int balance = getOrCreateWallet(userId).getCurrentBalance();
        return rewardRepo.findByIsActiveTrue()
                .stream()
                .map(r -> RewardResponse.from(r, balance))
                .toList();
    }

    // ── Aksiyon Puanı ────────────────────────────────────────────────────

    public int calculatePointsForAction(String action) {
        return switch (action.toUpperCase()) {
            case "QUIET_ZONE_WAIT"   -> 10;
            case "FLIGHT_CHECKIN"    -> 25;
            case "ECO_ROUTE_USED"    -> 15;
            case "ROUTE_SELECTION"   -> 50;   // geriye uyumluluk
            case "ROUTE_COMPLETION"  -> 50;   // yeni: adım bazlı doğrulamalı rota tamamlama
            case "LOUNGE_CHECKIN"    -> 20;
            default                  -> 5;
        };
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────

    private EcoWallet getOrCreateWallet(Long userId) {
        return walletRepo.findByUser_UserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));
            EcoWallet w = EcoWallet.builder().user(user).build();
            return walletRepo.save(w);
        });
    }

    private void updateTierLevel(EcoWallet wallet) {
        int b = wallet.getCurrentBalance();
        TierLevel tier = b >= 1500 ? TierLevel.PLATINUM
                       : b >= 500  ? TierLevel.GOLD
                       :             TierLevel.GREEN;
        wallet.setTierLevel(tier);
    }
}
