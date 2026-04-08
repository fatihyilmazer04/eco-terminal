package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.RouteResponse;
import com.ecoterminal.model.dto.RouteStep;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final TicketRepository ticketRepository;
    private final ZoneRepository zoneRepository;
    private final OccupancyService occupancyService;

    // ── Kullanıcı için kişisel rota önerisi ──────────────────────────────

    @Transactional(readOnly = true)
    public RouteResponse getSuggestedRoute(Long userId) {
        // En yakın aktif uçuşu bul
        Ticket soonestTicket = ticketRepository.findActiveTicketsWithFlight(userId)
                .stream()
                .filter(t -> t.getFlight().getDepartureTime().isAfter(Instant.now()))
                .min(Comparator.comparing(t -> t.getFlight().getDepartureTime()))
                .orElseThrow(() -> new BusinessException(
                        "Yaklaşan aktif uçuşunuz bulunamadı", HttpStatus.NOT_FOUND));

        Flight flight    = soonestTicket.getFlight();
        Zone targetGate  = flight.getGate();
        long minsLeft    = ChronoUnit.MINUTES.between(Instant.now(), flight.getDepartureTime());

        // Tüm bölgelerin anlık doluluk verisi
        List<ZoneOccupancyResponse> allZones = occupancyService.getAllZonesWithOccupancy();

        // Rota adımlarını oluştur
        List<RouteStep> steps = buildRouteSteps(allZones, targetGate);

        // Ortalama rota yoğunluğu (0.0–1.0)
        float avgDensity = (float) steps.stream()
                .mapToDouble(s -> densityToFloat(s.densityLevel()))
                .average()
                .orElse(0.0);

        int totalWalk = steps.stream().mapToInt(RouteStep::estimatedWalkMinutes).sum();

        return new RouteResponse(
                flight.getFlightId(),
                flight.getDestination(),
                targetGate != null ? targetGate.getZoneName() : "Belirsiz",
                minsLeft,
                steps,
                avgDensity,
                totalWalk + " dakika"
        );
    }

    // ── Doluluk < 0.50 olan alternatif sakin bölgeler ─────────────────────

    @Transactional(readOnly = true)
    public List<ZoneOccupancyResponse> getQuietAlternatives(Long targetZoneId) {
        return occupancyService.getAllZonesWithOccupancy()
                .stream()
                .filter(z -> !z.zoneId().equals(targetZoneId))
                .filter(z -> z.densityPct() != null && z.densityPct() < 0.50f)
                .sorted(Comparator.comparing(ZoneOccupancyResponse::densityPct))
                .toList();
    }

    // ── Rota adımları üretimi ─────────────────────────────────────────────

    /**
     * Kural tabanlı rota: CheckIn → Security → Koridor → Lounge → Hedef Kapı.
     * Her ara bölge için anlık yoğunluk bilgisi eklenir.
     * Yüksek yoğunluklu bölgeler atlanıp alternatif güzergah önerilir.
     */
    List<RouteStep> buildRouteSteps(List<ZoneOccupancyResponse> allZones, Zone targetGate) {
        Map<String, ZoneOccupancyResponse> byType = allZones.stream()
                .collect(Collectors.toMap(
                        ZoneOccupancyResponse::type,
                        z -> z,
                        (a, b) -> a.densityPct() <= b.densityPct() ? a : b  // düşük yoğunluğu tercih et
                ));

        List<RouteStep> steps = new ArrayList<>();
        int stepNum = 1;

        // Adım 1: CheckIn bölgesinden çık
        ZoneOccupancyResponse checkin = byType.get("CHECKIN");
        steps.add(new RouteStep(
                stepNum++,
                checkin != null ? checkin.zoneName() : "Check-In",
                "Check-In bölgesinden çıkın ve Ana Koridor'a yönelin",
                3,
                checkin != null ? checkin.densityLevel() : DensityLevel.LOW
        ));

        // Adım 2: Güvenlik
        ZoneOccupancyResponse security = byType.get("SECURITY");
        String securityInstruction = (security != null
                && (security.densityLevel() == DensityLevel.HIGH
                    || security.densityLevel() == DensityLevel.CRITICAL))
                ? "Güvenlik kontrolünden geçin — yoğunluk yüksek, hazırlıklı olun"
                : "Güvenlik kontrolünden geçin";

        steps.add(new RouteStep(
                stepNum++,
                security != null ? security.zoneName() : "Security",
                securityInstruction,
                5,
                security != null ? security.densityLevel() : DensityLevel.LOW
        ));

        // Adım 3: Yolcu alanına geçiş (sabit koridor adımı)
        steps.add(new RouteStep(
                stepNum++,
                "Yolcu Alanı",
                "Yolcu bölümüne giriş yapın, sağa dönün",
                2,
                DensityLevel.LOW
        ));

        // Adım 4: Lounge / bekleme katı
        ZoneOccupancyResponse lounge = byType.get("LOUNGE");
        steps.add(new RouteStep(
                stepNum++,
                lounge != null ? lounge.zoneName() : "Bekleme Salonu",
                "Kapılar koridoruna ilerleyin — Bekleme Salonu solunuzda",
                3,
                lounge != null ? lounge.densityLevel() : DensityLevel.LOW
        ));

        // Adım 5: Hedef kapı
        String gateName   = targetGate != null ? targetGate.getZoneName() : "Kapınız";
        DensityLevel gateDl = allZones.stream()
                .filter(z -> targetGate != null && z.zoneId().equals(targetGate.getZoneId()))
                .findFirst()
                .map(ZoneOccupancyResponse::densityLevel)
                .orElse(DensityLevel.LOW);

        steps.add(new RouteStep(
                stepNum,
                gateName,
                gateName + "'a ulaştınız — biniş başlamak üzere",
                1,
                gateDl
        ));

        return steps;
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private float densityToFloat(DensityLevel level) {
        return switch (level) {
            case LOW      -> 0.30f;
            case MEDIUM   -> 0.65f;
            case HIGH     -> 0.87f;
            case CRITICAL -> 0.97f;
        };
    }
}
