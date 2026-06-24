package com.ecoterminal.service.pathfinding;

import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneConnection;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneConnectionRepository;
import com.ecoterminal.repository.ZoneRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dijkstra algoritmasının üzerinde çalışacağı in-memory graph cache.
 *
 * Graph yapısı:
 *   - Nodes: 15 zone (CheckIn, Security, Lounge, Gate)
 *   - Edges: 52 bağlantı (V25 migration'ından)
 *
 * Cache stratejisi:
 *   - Edges DB'den 1 saatte bir yenilenir (zone_connections nadiren değişir)
 *   - Density 5 dakikada bir OccupancyReading'den güncellenir
 *   - Dijkstra çalışırken in-memory veriden okur, DB'ye gitmez
 *
 * Thread-safety:
 *   - ConcurrentHashMap kullanılır (refresh ve Dijkstra paralel çalışabilir)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final ZoneConnectionRepository connectionRepository;
    private final ZoneRepository           zoneRepository;
    private final OccupancyReadingRepository occupancyRepository;

    /**
     * Adjacency map: zoneId → bu zone'dan çıkan edge'lerin listesi.
     * EdgeInfo, ZoneConnection'ın Dijkstra için hafifletilmiş halidir.
     */
    private final Map<Long, List<EdgeInfo>> adjacencyMap = new ConcurrentHashMap<>();

    /**
     * Zone metadata cache: zoneId → Zone entity.
     */
    private final Map<Long, Zone> zoneCache = new ConcurrentHashMap<>();

    /**
     * Her zone'un mevcut yoğunluk değeri (0.0 – 1.0).
     * 5 dakikada bir OccupancyReading'den güncellenir.
     */
    private final Map<Long, Double> densityCache = new ConcurrentHashMap<>();

    /** Density verisi henüz yüklenmemiş zone'lar için fallback. */
    private static final double DEFAULT_DENSITY = 0.30;

    /**
     * Demo modu baseline yoğunlukları — DemoOccupancyProvider ile tutarlı.
     * refreshDensities() DB'de gerçek analiz kaydı olmayan zone'lar için bunları kullanır.
     * Simülatör verisi (source='yolov8_simulated') ignore edilir; sadece YOLOv8 gerçek
     * analizleri (source='yolov8_live') bu değerlerin üzerine yazılır.
     */
    private static final Map<String, Double> DEMO_BASELINE = Map.ofEntries(
        Map.entry("Lounge-1",   0.93),
        Map.entry("Gate A1",    0.87),
        Map.entry("Security-1", 0.78),
        Map.entry("Gate B2",    0.72),
        Map.entry("CheckIn-1",  0.65),
        Map.entry("Gate C3",    0.58),
        Map.entry("Gate A2",    0.52),
        Map.entry("Gate B1",    0.45),
        Map.entry("CheckIn-2",  0.38),
        Map.entry("Gate B3",    0.33),
        Map.entry("Gate C1",    0.28),
        Map.entry("Security-2", 0.25),
        Map.entry("Gate A3",    0.22),
        Map.entry("Gate C2",    0.18),
        Map.entry("Lounge-2",   0.12)
    );

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        log.info("GraphService initializing...");
        long start = System.currentTimeMillis();
        loadGraph();
        refreshDensities();
        log.info("GraphService initialized: {} nodes, {} edges, {} densities in {}ms",
                zoneCache.size(), countEdges(), densityCache.size(),
                System.currentTimeMillis() - start);
    }

    // ─── Scheduled Refresh ────────────────────────────────────────────────────

    /**
     * Graph'ı DB'den yükler.
     * Edge yapısı nadiren değiştiği için 1 saatte bir yenilenir.
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void loadGraph() {
        List<ZoneConnection> connections = connectionRepository.findAllActiveWithZones();
        List<Zone> zones = zoneRepository.findAll();

        Map<Long, List<EdgeInfo>> newAdjacency = new HashMap<>();
        Map<Long, Zone> newZoneCache = new HashMap<>();

        for (Zone z : zones) {
            newZoneCache.put(z.getZoneId(), z);
        }

        for (ZoneConnection conn : connections) {
            Long fromId = conn.getFromZone().getZoneId();
            EdgeInfo edge = new EdgeInfo(
                    conn.getId(),
                    fromId,
                    conn.getToZone().getZoneId(),
                    conn.getDistanceMeters(),
                    conn.getWalkTimeSeconds(),
                    Boolean.TRUE.equals(conn.getHasEscalator()),
                    Boolean.TRUE.equals(conn.getHasElevator()),
                    Boolean.TRUE.equals(conn.getHasMovingWalkway()),
                    Boolean.TRUE.equals(conn.getIsAccessible()),
                    conn.getNotes()
            );
            newAdjacency.computeIfAbsent(fromId, k -> new ArrayList<>()).add(edge);
        }

        // Atomik swap — Dijkstra çalışırken yarı-yenilenmiş state görmesin
        adjacencyMap.clear();
        adjacencyMap.putAll(newAdjacency);
        zoneCache.clear();
        zoneCache.putAll(newZoneCache);

        log.debug("Graph loaded: {} zones, {} edges", zoneCache.size(), countEdges());
    }

    /**
     * Her zone için güncel yoğunluk değerini yükler.
     *
     * Strateji:
     *   1) DEMO_BASELINE değerleri densityCache'e yüklenir (heatmap görünümüyle tutarlı).
     *   2) findLatestPerZone() çağrılır; source='yolov8_simulated' olan kayıtlar skip edilir.
     *   3) Gerçek YOLOv8 analiz sonuçları (source='yolov8_live') baseline'ın üzerine yazar.
     *
     * Bu sayede Dijkstra, heatmap'in gösterdiği değerleri kullanır ve demo modunda
     * simülatör artık verisi tarafından bozulmaz.
     *
     * 5 dakikada bir çalışır.
     */
    @Scheduled(fixedDelay = 300_000)
    public void refreshDensities() {
        if (zoneCache.isEmpty()) return;   // loadGraph henüz çalışmadıysa bekle

        try {
            // ── 1. Demo baseline yükle (heatmap ile tutarlı başlangıç noktası) ─────
            for (Long zoneId : zoneCache.keySet()) {
                Zone zone = zoneCache.get(zoneId);
                Double baseline = zone != null
                        ? DEMO_BASELINE.getOrDefault(zone.getZoneName(), DEFAULT_DENSITY)
                        : DEFAULT_DENSITY;
                densityCache.put(zoneId, baseline);
            }

            // ── 2. Gerçek YOLOv8 analizleriyle üzerine yaz ───────────────────────
            // Simülatör verisi (source='yolov8_simulated') kasıtlı olarak ignore edilir;
            // bu sayede demo baseline korunur ve sadece gerçek kamera analizleri
            // güncelleme yapar.
            List<OccupancyReading> latestReadings = occupancyRepository.findLatestPerZone();
            int realCount = 0;
            for (OccupancyReading reading : latestReadings) {
                if ("yolov8_simulated".equals(reading.getSource())) continue;
                Long zoneId = reading.getZone().getZoneId();
                densityCache.put(zoneId, reading.getDensityPct().doubleValue());
                realCount++;
            }

            log.debug("Densities refreshed: {} zones ({} real analysis, {} demo baseline)",
                    densityCache.size(), realCount, densityCache.size() - realCount);

        } catch (Exception e) {
            log.warn("Density refresh failed, keeping previous values: {}", e.getMessage());
        }
    }

    // ─── Public API (Dijkstra tarafından çağrılır) ────────────────────────────

    /**
     * Bir zone'dan çıkan tüm aktif edge'leri döner.
     * Dijkstra "neighbor expansion" adımında kullanılır.
     */
    public List<EdgeInfo> getNeighbors(Long zoneId) {
        return adjacencyMap.getOrDefault(zoneId, Collections.emptyList());
    }

    /**
     * Tüm zone ID'leri.
     * Dijkstra başlangıç state'i (tüm node'lar ∞ mesafede) için kullanılır.
     */
    public Set<Long> getAllZoneIds() {
        return Collections.unmodifiableSet(zoneCache.keySet());
    }

    /**
     * Zone entity'sini döner (zone_name, type, capacity için).
     */
    public Zone getZone(Long zoneId) {
        return zoneCache.get(zoneId);
    }

    /**
     * Zone'un mevcut yoğunluk değeri (0.0 – 1.0).
     */
    public double getDensity(Long zoneId) {
        return densityCache.getOrDefault(zoneId, DEFAULT_DENSITY);
    }

    /**
     * Edge weight hesaplama — 3 farklı strateji destekler.
     *
     * SHORTEST:     Sadece yürüme süresi (en hızlı fiziksel yol)
     * LEAST_CROWDED: Yoğunluğa ağır ceza — kalabalıktan kaçınan rota
     * BALANCED:     Süre + yoğunluk dengeli — önerilen default
     *
     * @param edge     değerlendirilecek edge
     * @param strategy ağırlık hesaplama stratejisi
     * @return double weight (düşük = tercih edilir)
     */
    public double calculateEdgeWeight(EdgeInfo edge, WeightStrategy strategy) {
        double walkTime  = edge.walkTimeSeconds();
        double toDensity = getDensity(edge.toZoneId());

        return switch (strategy) {
            case SHORTEST      -> walkTime;
            case LEAST_CROWDED -> walkTime + (toDensity * 300.0);  // %100 doluluk → +300sn ceza
            case BALANCED      -> walkTime + (toDensity * 400.0);  // yoğunluğa duyarlı denge
        };
    }

    /**
     * Toplam edge sayısı (log ve health için).
     */
    public int countEdges() {
        return adjacencyMap.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Manuel cache yenileme (admin endpoint'ten tetiklenebilir).
     */
    public void forceRefresh() {
        log.info("Manual graph refresh requested");
        loadGraph();
        refreshDensities();
    }

    // ─── Inner Types ──────────────────────────────────────────────────────────

    /**
     * Hafif edge representation — Dijkstra'nın ihtiyacı olan verileri tutar.
     * ZoneConnection entity'nin JPA proxy yükü olmadan.
     * Record (immutable) → thread-safe.
     */
    public record EdgeInfo(
            Long    edgeId,
            Long    fromZoneId,
            Long    toZoneId,
            int     distanceMeters,
            int     walkTimeSeconds,
            boolean hasEscalator,
            boolean hasElevator,
            boolean hasMovingWalkway,
            boolean isAccessible,
            String  notes
    ) {}

    /**
     * Dijkstra'nın optimizasyon kriteri.
     */
    public enum WeightStrategy {
        /** En kısa yürüme süresi */
        SHORTEST,
        /** En az kalabalık geçiş (yoğun node'lara ağır ceza) */
        LEAST_CROWDED,
        /** Süre + yoğunluk dengeli — önerilen */
        BALANCED
    }
}
