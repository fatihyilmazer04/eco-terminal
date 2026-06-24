package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.RouteResponse;
import com.ecoterminal.model.dto.RouteStep;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.RouteCompletionRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneMapPositionRepository;
import com.ecoterminal.repository.ZoneRepository;
import com.ecoterminal.service.pathfinding.DijkstraService;
import com.ecoterminal.service.pathfinding.DijkstraService.PathResult;
import com.ecoterminal.service.pathfinding.DijkstraService.PathSegment;
import com.ecoterminal.service.pathfinding.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final TicketRepository ticketRepository;
    private final ZoneRepository zoneRepository;
    private final OccupancyService occupancyService;
    private final ZoneMapPositionRepository zoneMapPositionRepository;
    private final RouteCompletionRepository completionRepository;
    private final DijkstraService dijkstraService;
    private final GraphService graphService;

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

        Flight flight   = soonestTicket.getFlight();
        Zone targetGate = flight.getGate();
        long minsLeft   = ChronoUnit.MINUTES.between(Instant.now(), flight.getDepartureTime());

        // Harita koordinatları
        Map<String, ZoneMapPosition> posMap = zoneMapPositionRepository.findAllActiveZonePositions()
                .stream()
                .collect(Collectors.toMap(p -> p.getZone().getZoneName(), p -> p));

        // Dijkstra ile rota bul
        List<RouteStep> steps = buildDijkstraSteps(targetGate, posMap);

        // Ortalama rota yoğunluğu
        float avgDensity = (float) steps.stream()
                .mapToDouble(s -> densityLevelToFloat(s.densityLevel()))
                .average()
                .orElse(0.0);

        int totalWalkMin = steps.isEmpty() ? 0
                : steps.get(steps.size() - 1).estimatedWalkMinutes();

        // Bu kullanıcı bu uçuş için daha önce rota tamamlama puanı almış mı?
        boolean alreadyRewarded = completionRepository
                .existsByUser_UserIdAndFlightId(userId, flight.getFlightId());

        return new RouteResponse(
                flight.getFlightId(),
                flight.getDestination(),
                targetGate != null ? targetGate.getZoneName() : "Belirsiz",
                minsLeft,
                steps,
                avgDensity,
                totalWalkMin + " dakika",
                alreadyRewarded
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

    // ── Dijkstra tabanlı rota adımları üretimi ────────────────────────────

    /**
     * En az yoğun CheckIn zone'unu başlangıç olarak seçer, Dijkstra ile
     * hedef kapıya optimal yolu bulur ve RouteStep listesine dönüştürür.
     *
     * targetGate null ise kural-tabanlı fallback devreye girer.
     */
    private List<RouteStep> buildDijkstraSteps(Zone targetGate, Map<String, ZoneMapPosition> posMap) {
        // Hedef kapı atanmamış — fallback
        if (targetGate == null) {
            log.warn("Hedef kapı atanamadı, kural-tabanlı rota döndürülüyor");
            return fallbackSteps(posMap);
        }

        // En az yoğun CheckIn zone'u
        Long startZoneId = pickBestCheckIn();
        if (startZoneId == null) {
            log.warn("CheckIn zone bulunamadı, kural-tabanlı rota döndürülüyor");
            return fallbackSteps(posMap);
        }

        Long goalZoneId = targetGate.getZoneId();

        PathResult result = dijkstraService.findPath(startZoneId, goalZoneId, GraphService.WeightStrategy.BALANCED);

        if (!result.reachable() || result.segments().isEmpty()) {
            log.warn("Dijkstra rota bulamadı ({} → {}), fallback aktif", startZoneId, goalZoneId);
            return fallbackSteps(posMap);
        }

        return convertToRouteSteps(result.segments(), posMap);
    }

    /**
     * Graph cache üzerinden en az yoğun CheckIn zone ID'sini döner.
     * GraphService.getAllZoneIds() + getZone() + getDensity() kullanır.
     */
    private Long pickBestCheckIn() {
        return graphService.getAllZoneIds().stream()
                .filter(id -> {
                    Zone z = graphService.getZone(id);
                    return z != null && z.getType() == ZoneType.CHECKIN;
                })
                .min(Comparator.comparingDouble(graphService::getDensity))
                .orElse(null);
    }

    /**
     * PathSegment listesini RouteStep listesine dönüştürür.
     * Her segment için:
     *   - densityLevel: GraphService'den anlık yoğunluk
     *   - instruction: zone tipine + edge özelliklerine göre üretilir
     *   - estimatedWalkMinutes: kümülatif saniyeden hesaplanır
     *   - posX/posY: ZoneMapPosition'dan merkez koordinat
     */
    private List<RouteStep> convertToRouteSteps(List<PathSegment> segments,
                                                 Map<String, ZoneMapPosition> posMap) {
        List<RouteStep> steps = new ArrayList<>(segments.size());
        int size = segments.size();

        for (int i = 0; i < size; i++) {
            PathSegment seg = segments.get(i);
            boolean isFirst = (i == 0);
            boolean isLast  = (i == size - 1);

            Zone zone = graphService.getZone(seg.zoneId());
            double density = graphService.getDensity(seg.zoneId());
            DensityLevel densityLevel = DensityLevel.of((float) density);

            String instruction = buildInstruction(seg, zone, isFirst, isLast, densityLevel);
            int walkMinutes = (int) Math.ceil(seg.cumulativeWalkSeconds() / 60.0);

            // Harita koordinatı (merkez)
            ZoneMapPosition pos = posMap.get(seg.zoneName());
            Double cx = null, cy = null;
            if (pos != null) {
                cx = pos.getPosX() + pos.getWidth()  / 2.0;
                cy = pos.getPosY() + pos.getHeight() / 2.0;
            }

            steps.add(new RouteStep(i + 1, seg.zoneName(), instruction, walkMinutes, densityLevel, cx, cy));
        }

        return steps;
    }

    /**
     * Segment için doğal dil yönlendirmesi üretir.
     * Zone tipi + edge özellikleri + yoğunluk durumu dikkate alınır.
     */
    private String buildInstruction(PathSegment seg, Zone zone,
                                    boolean isFirst, boolean isLast,
                                    DensityLevel densityLevel) {
        ZoneType type = zone != null ? zone.getType() : ZoneType.OTHER;
        GraphService.EdgeInfo edge = seg.edgeFromPrev();

        String base;
        if (isFirst) {
            base = "Check-In bölgesinden çıkın ve güvenlik koridoruna yönelin";
        } else if (isLast) {
            base = seg.zoneName() + " kapısına ulaştınız — biniş için hazır olun";
        } else {
            base = switch (type) {
                case SECURITY -> buildSecurityInstruction(densityLevel);
                case LOUNGE   -> "Bekleme salonundan geçerek kapılar koridoruna ilerleyin";
                case GATE     -> seg.zoneName() + " kapısından geçin";
                default       -> seg.zoneName() + " bölgesinden ilerleyin";
            };
        }

        // Edge özelliği notu (sadece başlangıç adımı değilse)
        if (!isFirst && edge != null) {
            if (edge.hasMovingWalkway()) {
                base += " — yürüyen bandı kullanabilirsiniz";
            } else if (edge.hasEscalator()) {
                base += " — yürüyen merdivenden yararlanın";
            } else if (edge.hasElevator()) {
                base += " — asansörü kullanabilirsiniz";
            }
        }

        return base;
    }

    private String buildSecurityInstruction(DensityLevel level) {
        return switch (level) {
            case HIGH, CRITICAL -> "Güvenlik kontrolünden geçin — yoğunluk yüksek, hazırlıklı olun";
            default             -> "Güvenlik kontrolünden geçin";
        };
    }

    // ── Kural-tabanlı fallback (Dijkstra başarısız olursa) ───────────────

    /**
     * Dijkstra çalışamazsa (hedef kapı yok / graph boş) devreye giren
     * önceki kural-tabanlı 5 adımlı rota.
     */
    private List<RouteStep> fallbackSteps(Map<String, ZoneMapPosition> posMap) {
        List<ZoneOccupancyResponse> allZones = occupancyService.getAllZonesWithOccupancy();
        Map<String, ZoneOccupancyResponse> byType = allZones.stream()
                .collect(Collectors.toMap(
                        ZoneOccupancyResponse::type,
                        z -> z,
                        (a, b) -> a.densityPct() <= b.densityPct() ? a : b
                ));

        List<RouteStep> steps = new ArrayList<>();
        int n = 1;

        ZoneOccupancyResponse checkin = byType.get("CHECKIN");
        steps.add(enrichWithPos(new RouteStep(n++,
                checkin != null ? checkin.zoneName() : "Check-In",
                "Check-In bölgesinden çıkın ve Ana Koridor'a yönelin",
                3, checkin != null ? checkin.densityLevel() : DensityLevel.LOW,
                null, null), posMap));

        ZoneOccupancyResponse security = byType.get("SECURITY");
        String secInstr = (security != null
                && (security.densityLevel() == DensityLevel.HIGH
                    || security.densityLevel() == DensityLevel.CRITICAL))
                ? "Güvenlik kontrolünden geçin — yoğunluk yüksek, hazırlıklı olun"
                : "Güvenlik kontrolünden geçin";
        steps.add(enrichWithPos(new RouteStep(n++,
                security != null ? security.zoneName() : "Security",
                secInstr, 5,
                security != null ? security.densityLevel() : DensityLevel.LOW,
                null, null), posMap));

        steps.add(new RouteStep(n++, "Yolcu Alanı",
                "Yolcu bölümüne giriş yapın, sağa dönün", 2, DensityLevel.LOW, null, null));

        ZoneOccupancyResponse lounge = byType.get("LOUNGE");
        steps.add(enrichWithPos(new RouteStep(n++,
                lounge != null ? lounge.zoneName() : "Bekleme Salonu",
                "Kapılar koridoruna ilerleyin — Bekleme Salonu solunuzda",
                3, lounge != null ? lounge.densityLevel() : DensityLevel.LOW,
                null, null), posMap));

        steps.add(new RouteStep(n, "Kapınız",
                "Kapınıza ulaştınız — biniş başlamak üzere", 1, DensityLevel.LOW, null, null));

        return steps;
    }

    private RouteStep enrichWithPos(RouteStep step, Map<String, ZoneMapPosition> posMap) {
        ZoneMapPosition pos = posMap.get(step.zoneName());
        if (pos == null) return step;
        double cx = pos.getPosX() + pos.getWidth()  / 2.0;
        double cy = pos.getPosY() + pos.getHeight() / 2.0;
        return new RouteStep(step.stepNumber(), step.zoneName(), step.instruction(),
                step.estimatedWalkMinutes(), step.densityLevel(), cx, cy);
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private float densityLevelToFloat(DensityLevel level) {
        return switch (level) {
            case LOW      -> 0.30f;
            case MEDIUM   -> 0.65f;
            case HIGH     -> 0.87f;
            case CRITICAL -> 0.97f;
        };
    }
}
