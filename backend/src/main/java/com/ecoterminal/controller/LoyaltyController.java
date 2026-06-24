package com.ecoterminal.controller;

import com.ecoterminal.model.dto.*;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.LoyaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    /** GET /api/loyalty/wallet — bakiye + tier bilgisi */
    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.getWallet(principal.getUserId())));
    }

    /** GET /api/loyalty/transactions — işlem geçmişi */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.getTransactionHistory(principal.getUserId())));
    }

    /** GET /api/loyalty/rewards — ödül kataloğu */
    @GetMapping("/rewards")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> getRewards(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.getRewardCatalog(principal.getUserId())));
    }

    /** POST /api/loyalty/spend — puan harca */
    @PostMapping("/spend")
    public ResponseEntity<ApiResponse<SpendResponse>> spend(
            @RequestBody @Valid SpendRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                loyaltyService.spendPoints(principal.getUserId(), request.rewardId())));
    }

    /** GET /api/loyalty/my-redemptions — sahip olunan ödül kodları */
    @GetMapping("/my-redemptions")
    public ResponseEntity<ApiResponse<List<RedemptionResponse>>> getMyRedemptions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.getMyRedemptions(principal.getUserId())));
    }

    /** POST /api/loyalty/earn — aksiyon ile puan kazan (test endpoint) */
    @PostMapping("/earn")
    public ResponseEntity<ApiResponse<WalletResponse>> earn(
            @RequestBody @Valid EarnRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        int points = loyaltyService.calculatePointsForAction(request.action());
        WalletResponse wallet = loyaltyService.addPoints(
                principal.getUserId(), points, request.action() + " aksiyonu");
        return ResponseEntity.ok(ApiResponse.ok(wallet));
    }
}
