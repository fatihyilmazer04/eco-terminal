package com.ecoterminal.service.pathfinding;

import com.ecoterminal.model.entity.Zone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Dijkstra shortest-path algoritması.
 *
 * GraphService'in in-memory cache'inden okuyarak tek çağrıda
 * başlangıç → hedef arası optimal yolu hesaplar.
 *
 * Desteklenen stratejiler (GraphService.WeightStrategy):
 *   SHORTEST      — Sadece yürüme süresi
 *   BALANCED      — Süre + yoğunluk dengeli (önerilen default)
 *   LEAST_CROWDED — Kalabalık zone'lardan maksimum kaçınma
 *
 * Thread-safety: Durumsuz (stateless) — her çağrı kendi Map/PQ objelerini oluşturur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DijkstraService {

    private final GraphService graphService;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * fromZoneId → toZoneId arası en kısa yolu hesaplar.
     *
     * @param fromZoneId  başlangıç zone ID
     * @param toZoneId    hedef zone ID
     * @param strategy    ağırlık stratejisi
     * @return PathResult — bulunamazsa {@link PathResult#unreachable()}
     */
    public PathResult findPath(Long fromZoneId, Long toZoneId, GraphService.WeightStrategy strategy) {
        log.debug("Dijkstra başlatıldı: {} → {} [{}]", fromZoneId, toZoneId, strategy);

        // Trivial case: başlangıç = hedef
        if (fromZoneId.equals(toZoneId)) {
            Zone zone = graphService.getZone(fromZoneId);
            return new PathResult(true,
                    List.of(new PathSegment(fromZoneId, nameOf(zone), null, 0)),
                    0, 0);
        }

        Set<Long> allIds = graphService.getAllZoneIds();
        Map<Long, Double> dist    = new HashMap<>(allIds.size() * 2);
        Map<Long, Long>   prev    = new HashMap<>();
        Map<Long, GraphService.EdgeInfo> prevEdge = new HashMap<>();

        for (Long id : allIds) dist.put(id, Double.MAX_VALUE);
        dist.put(fromZoneId, 0.0);

        // PriorityQueue: (zoneId, tentative-weight) — küçük weight önce
        PriorityQueue<PqEntry> pq = new PriorityQueue<>(Comparator.comparingDouble(PqEntry::weight));
        pq.offer(new PqEntry(fromZoneId, 0.0));

        while (!pq.isEmpty()) {
            PqEntry head = pq.poll();
            Long u = head.zoneId();

            // Stale entry: bu node daha iyi bir path ile zaten işlendi
            if (head.weight() > dist.getOrDefault(u, Double.MAX_VALUE)) continue;

            // Hedef bulundu — erken çıkış
            if (u.equals(toZoneId)) break;

            for (GraphService.EdgeInfo edge : graphService.getNeighbors(u)) {
                Long v    = edge.toZoneId();
                double w  = graphService.calculateEdgeWeight(edge, strategy);
                double nd = dist.get(u) + w;

                if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, nd);
                    prev.put(v, u);
                    prevEdge.put(v, edge);
                    pq.offer(new PqEntry(v, nd));
                }
            }
        }

        // Hedefe ulaşılamadıysa
        if (dist.getOrDefault(toZoneId, Double.MAX_VALUE) == Double.MAX_VALUE) {
            log.warn("Dijkstra: {} → {} arasında rota bulunamadı", fromZoneId, toZoneId);
            return PathResult.unreachable();
        }

        return reconstructPath(toZoneId, prev, prevEdge);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /** Prev haritasından yolu geri izler, PathSegment listesi oluşturur. */
    private PathResult reconstructPath(Long toZoneId,
                                       Map<Long, Long> prev,
                                       Map<Long, GraphService.EdgeInfo> prevEdge) {
        // Sondan başa: toZoneId → ... → fromZoneId
        Deque<Long> pathIds = new ArrayDeque<>();
        Long cur = toZoneId;
        while (cur != null) {
            pathIds.addFirst(cur);
            cur = prev.get(cur);
        }

        // Segment listesi ile kümülatif süre/mesafe hesapla
        List<PathSegment> segments = new ArrayList<>(pathIds.size());
        int cumSec   = 0;
        int cumDist  = 0;
        Long prevId  = null;

        for (Long id : pathIds) {
            GraphService.EdgeInfo edge = prevId != null ? prevEdge.get(id) : null;
            if (edge != null) {
                cumSec  += edge.walkTimeSeconds();
                cumDist += edge.distanceMeters();
            }
            Zone zone = graphService.getZone(id);
            segments.add(new PathSegment(id, nameOf(zone), edge, cumSec));
            prevId = id;
        }

        log.debug("Dijkstra tamamlandı: {} adım, {}m, {}sn", segments.size(), cumDist, cumSec);
        return new PathResult(true, segments, cumDist, cumSec);
    }

    private String nameOf(Zone zone) {
        return zone != null ? zone.getZoneName() : "?";
    }

    // ─── Inner Types ──────────────────────────────────────────────────────────

    /** PriorityQueue entry — zone + tentative Dijkstra weight. */
    private record PqEntry(Long zoneId, double weight) {}

    // ─── Public Types ──────────────────────────────────────────────────────────

    /**
     * Dijkstra sonucu.
     *
     * @param reachable           hedefe rota bulunabildi mi?
     * @param segments            başlangıç dahil her zone için segment listesi
     * @param totalDistanceMeters toplam mesafe (metre)
     * @param totalWalkTimeSeconds toplam yürüme süresi (saniye)
     */
    public record PathResult(
            boolean           reachable,
            List<PathSegment> segments,
            int               totalDistanceMeters,
            int               totalWalkTimeSeconds
    ) {
        /** Rota bulunamadı durumu için factory. */
        public static PathResult unreachable() {
            return new PathResult(false, List.of(), 0, 0);
        }
    }

    /**
     * Rota üzerindeki tek bir zone adımı.
     *
     * @param zoneId                bu segment'in zone ID'si
     * @param zoneName              zone görünen adı
     * @param edgeFromPrev          bu zone'a gelmek için kullanılan edge (başlangıç node'unda null)
     * @param cumulativeWalkSeconds başlangıçtan bu zone'a kadar toplam yürüme süresi
     */
    public record PathSegment(
            Long                  zoneId,
            String                zoneName,
            GraphService.EdgeInfo edgeFromPrev,
            int                   cumulativeWalkSeconds
    ) {}
}
