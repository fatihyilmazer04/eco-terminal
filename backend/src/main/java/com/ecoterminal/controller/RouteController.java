package com.ecoterminal.controller;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.RouteCheckin;
import com.ecoterminal.model.entity.RouteCompletion;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.repository.RouteCheckinRepository;
import com.ecoterminal.repository.RouteCompletionRepository;
import com.ecoterminal.repository.UserRepository;
import com.ecoterminal.repository.ZoneRepository;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.LoyaltyService;
import com.ecoterminal.service.RouteService;
import com.ecoterminal.service.pathfinding.DijkstraService;
import com.ecoterminal.service.pathfinding.GraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService              routeService;
    private final DijkstraService           dijkstraService;
    private final GraphService              graphService;
    private final RouteCheckinRepository    checkinRepo;
    private final RouteCompletionRepository completionRepo;
    private final UserRepository            userRepository;
    private final LoyaltyService            loyaltyService;
    private final ZoneRepository            zoneRepository;

    private static final int ROUTE_COMPLETION_POINTS = 50;

    /**
     * POST /api/routes/optimal — llm-service için internal Dijkstra endpoint'i.
     *
     * İstek: { "fromZoneId": 4, "toZoneId": 1 }
     * Yanıt: { "alternatives": [ { "strategy": "BALANCED", "steps": [...], ... } ] }
     *
     * 3 strateji için paralel hesaplama yapılır; llm-service bu veriden
     * doğal dil rota önerisi üretir. X-Internal-Token ile erişilebilir.
     */
    @PostMapping("/optimal")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOptimalRoute(
            @RequestBody Map<String, Long> req) {

        Long fromId = req.get("fromZoneId");
        Long toId   = req.get("toZoneId");

        if (fromId == null || toId == null) {
            throw new BusinessException("fromZoneId ve toZoneId zorunludur", HttpStatus.BAD_REQUEST);
        }

        GraphService.WeightStrategy[] strategies = {
            GraphService.WeightStrategy.BALANCED,
            GraphService.WeightStrategy.SHORTEST,
            GraphService.WeightStrategy.LEAST_CROWDED,
        };

        var alternatives = new ArrayList<Map<String, Object>>();

        for (GraphService.WeightStrategy strategy : strategies) {
            DijkstraService.PathResult result = dijkstraService.findPath(fromId, toId, strategy);
            if (!result.reachable()) continue;

            var steps = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < result.segments().size(); i++) {
                DijkstraService.PathSegment seg = result.segments().get(i);
                int walkMin = seg.edgeFromPrev() != null
                        ? (int) Math.round(seg.edgeFromPrev().walkTimeSeconds() / 60.0)
                        : 0;
                steps.add(Map.of(
                        "stepNumber",           i + 1,
                        "zoneName",             seg.zoneName(),
                        "estimatedWalkMinutes", walkMin
                ));
            }

            double avgDensity = result.segments().stream()
                    .mapToDouble(s -> graphService.getDensity(s.zoneId()))
                    .average()
                    .orElse(0.0);

            alternatives.add(Map.of(
                    "strategy",             strategy.name(),
                    "steps",                steps,
                    "totalWalkSeconds",     result.totalWalkTimeSeconds(),
                    "totalDistanceMeters",  result.totalDistanceMeters(),
                    "avgDensity",           avgDensity
            ));
        }

        log.info("optimal_route from={} to={} alternatives={}", fromId, toId, alternatives.size());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("alternatives", alternatives)));
    }

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

    // ── POST /api/routes/verify-qr ────────────────────────────────────────

    /**
     * QR kod doğrulaması — rota adımında kullanıcının fiziksel varlığını kanıtlar.
     *
     * Akış:
     *   1) scannedToken → DB'den zone bul (findByQrToken)
     *   2) Zone bulunamadıysa → verified=false ("Geçersiz QR kodu")
     *   3) Zone bulunduysa ama isim uyuşmuyorsa → verified=false ("Yanlış QR kodu! X bekleniyor")
     *   4) İsim uyuşuyorsa → verified=true
     *
     * Not: Bu endpoint yalnızca QR'ı doğrular. Adım kaydı (checkin) hâlâ
     * mevcut /api/routes/checkin endpoint'i üzerinden yapılır.
     */
    @PostMapping("/verify-qr")
    public ResponseEntity<ApiResponse<QrVerifyResponse>> verifyQr(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody QrVerifyRequest req
    ) {
        // 1. Token ile zone bul
        Zone zone = zoneRepository.findByQrToken(req.scannedToken()).orElse(null);

        if (zone == null) {
            log.warn("QR doğrulama başarısız — bilinmeyen token: '{}' (userId={})",
                    req.scannedToken(), principal.getUserId());
            return ResponseEntity.ok(ApiResponse.ok(
                    new QrVerifyResponse(false, null,
                            "Geçersiz QR kodu! Bu QR kodu sisteme kayıtlı değil.")));
        }

        // 2. Zone ismi beklenenle uyuşuyor mu?
        if (!zone.getZoneName().equalsIgnoreCase(req.expectedZoneName())) {
            log.warn("QR doğrulama başarısız — yanlış zone: beklenen='{}' okunan='{}' (userId={})",
                    req.expectedZoneName(), zone.getZoneName(), principal.getUserId());
            return ResponseEntity.ok(ApiResponse.ok(
                    new QrVerifyResponse(false, zone.getZoneName(),
                            "Yanlış QR kodu! Bu kod " + zone.getZoneName() +
                            "'e ait, " + req.expectedZoneName() + " bekleniyor.")));
        }

        // 3. Başarılı
        log.info("QR doğrulandı: zone='{}' userId={}", zone.getZoneName(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(
                new QrVerifyResponse(true, zone.getZoneName(),
                        "QR doğrulandı! " + zone.getZoneName() + " adımı tamamlandı.")));
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
