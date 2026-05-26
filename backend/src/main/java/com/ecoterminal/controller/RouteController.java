package com.ecoterminal.controller;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.RouteCheckin;
import com.ecoterminal.model.entity.RouteCompletion;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.repository.RouteCheckinRepository;
import com.ecoterminal.repository.RouteCompletionRepository;
import com.ecoterminal.repository.UserRepository;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.LoyaltyService;
import com.ecoterminal.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService              routeService;
    private final RouteCheckinRepository    checkinRepo;
    private final RouteCompletionRepository completionRepo;
    private final UserRepository            userRepository;
    private final LoyaltyService            loyaltyService;

    private static final int ROUTE_COMPLETION_POINTS = 50;

    /** GET /api/routes/suggest — kişisel rota önerisi */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<RouteResponse>> suggestRoute(
            @AuthenticationPrincipal UserPrincipal principal) {
        RouteResponse route = routeService.getSuggestedRoute(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(route));
    }

    /** GET /api/routes/alternatives/{zoneId} — sakin bekleme alanları */
    @GetMapping("/alternatives/{zoneId}")
    public ResponseEntity<ApiResponse<List<ZoneOccupancyResponse>>> getAlternatives(
            @PathVariable Long zoneId) {
        List<ZoneOccupancyResponse> alts = routeService.getQuietAlternatives(zoneId);
        return ResponseEntity.ok(ApiResponse.ok(alts));
    }

    // ── POST /api/routes/checkin ───────────────────────────────────────────

    /**
     * Bir rota adımını tamamlandı olarak işaretle.
     * Aynı adım daha önce check-in yapıldıysa 200 döner (idempotent).
     */
    @PostMapping("/checkin")
    public ResponseEntity<ApiResponse<Void>> checkinStep(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RouteCheckinRequest req
    ) {
        Long userId = principal.getUserId();

        // Aynı adım zaten check-in yapılmış mı?
        if (checkinRepo.existsByUser_UserIdAndFlightIdAndStepNumber(
                userId, req.flightId(), req.stepNumber())) {
            return ResponseEntity.ok(ApiResponse.ok(null, "Bu adım zaten tamamlandı"));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        RouteCheckin checkin = RouteCheckin.builder()
                .user(user)
                .flightId(req.flightId())
                .stepNumber(req.stepNumber())
                .zoneName(req.zoneName())
                .totalSteps(req.totalSteps())
                .build();
        checkinRepo.save(checkin);

        log.info("Rota check-in: userId={} flight={} step={}/{} zone={}",
                userId, req.flightId(), req.stepNumber(), req.totalSteps(), req.zoneName());

        return ResponseEntity.ok(ApiResponse.ok(null,
                "Adım " + req.stepNumber() + " tamamlandı: " + req.zoneName()));
    }

    // ── POST /api/routes/complete ──────────────────────────────────────────

    /**
     * Rota tamamlama — puan ANCAK:
     *   1) Tüm adımlar check-in yapıldıysa VE
     *   2) Bu uçuş için daha önce ödül verilmemişse
     * verilir. Aksi hâlde alreadyRewarded=true ile mevcut bakiye döner.
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<RouteCompleteResponse>> completeRoute(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RouteCompleteRequest req
    ) {
        Long userId = principal.getUserId();

        // ── 1. Tekrar kontrolü ──────────────────────────────────────────
        if (completionRepo.existsByUser_UserIdAndFlightId(userId, req.flightId())) {
            WalletResponse wallet = loyaltyService.getWallet(userId);
            log.info("Rota tamamlama tekrarı engellendi: userId={} flight={}",
                    userId, req.flightId());
            return ResponseEntity.ok(ApiResponse.ok(
                    new RouteCompleteResponse(0, wallet.currentBalance(), wallet.tierLevel(), true),
                    "Bu uçuş için eko puanı zaten kazandınız"
            ));
        }

        // ── 2. Adım doğrulaması ─────────────────────────────────────────
        int checkedIn = checkinRepo.countByUser_UserIdAndFlightId(userId, req.flightId());

        List<Integer> totalStepsList = checkinRepo.findTotalStepsByUserAndFlight(userId, req.flightId());
        if (totalStepsList.isEmpty()) {
            throw new BusinessException("Henüz hiç adım tamamlanmadı", HttpStatus.BAD_REQUEST);
        }
        int totalSteps = totalStepsList.get(0);

        log.info("Rota tamamlama isteği: userId={} flight={} checkedIn={}/{}",
                userId, req.flightId(), checkedIn, totalSteps);

        if (checkedIn < totalSteps) {
            throw new BusinessException(
                    "Rota tamamlanmadı. Tamamlanan: " + checkedIn + "/" + totalSteps + " adım",
                    HttpStatus.BAD_REQUEST);
        }

        // ── 3. Puan ver ─────────────────────────────────────────────────
        WalletResponse wallet = loyaltyService.addPoints(
                userId,
                ROUTE_COMPLETION_POINTS,
                "Rota tamamlandı (uçuş #" + req.flightId() + ") — tüm " + totalSteps + " durak check-in yapıldı"
        );

        // ── 4. Completion kaydı — bir daha ödül verilmesini önler ───────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        completionRepo.save(RouteCompletion.builder()
                .user(user)
                .flightId(req.flightId())
                .pointsEarned(ROUTE_COMPLETION_POINTS)
                .build());

        log.info("Rota tamamlama puanı verildi: userId={} +{} puan (toplam={}) flight={}",
                userId, ROUTE_COMPLETION_POINTS, wallet.currentBalance(), req.flightId());

        return ResponseEntity.ok(ApiResponse.ok(
                new RouteCompleteResponse(ROUTE_COMPLETION_POINTS, wallet.currentBalance(),
                        wallet.tierLevel(), false),
                "Tebrikler! Rotayı tamamladınız"
        ));
    }
}
