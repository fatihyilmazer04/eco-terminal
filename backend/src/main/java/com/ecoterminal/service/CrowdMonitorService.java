package com.ecoterminal.service;

import com.ecoterminal.model.dto.HeatmapSummaryResponse;
import com.ecoterminal.model.dto.OccupancyTimeSeriesPoint;
import com.ecoterminal.model.dto.ZoneCrowdStatusResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneMapPositionRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Heatmap servisi — Zone durumu + harita koordinatları + AI tahmini birleştirir.
 * /api/heatmap/* endpoint'leri için kullanılır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrowdMonitorService {

    private static final int TREND_LOOKBACK = 4;

    private final ZoneRepository               zoneRepo;
    private final OccupancyReadingRepository   occupancyRepo;
    private final AIPredictionRepository       predictionRepo;
    private final ZoneMapPositionRepository    positionRepo;
    private final DemoOccupancyProvider        demoProvider;

    @Value("${app.demo.fixed-heatmap:true}")
    private boolean demoMode;

    /**
     * Tüm terminal heatmap verisi — zone durumu + koordinatlar + AI + özet.
     * Demo modunda DemoOccupancyProvider'dan sabit değerler döner.
     */
    @Transactional(readOnly = true)
    public HeatmapSummaryResponse getHeatmapData() {
        if (demoMode) {
            return demoProvider.buildHeatmapSummaryResponse();
        }

        // 1. Her zone'un en son okuması (tek sorgu)
        Map<Long, OccupancyReading> latestByZoneId = occupancyRepo.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getZone().getZoneId(), r -> r,
                        (existing, duplicate) -> existing));

        // 2. Her zone'un en son AI tahmini (tek sorgu)
        Map<Long, AIPrediction> predByZoneId = predictionRepo.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getZone().getZoneId(), p -> p,
                        (existing, duplicate) -> existing));

        // 3. Harita pozisyonları (tek sorgu)
        Map<Long, ZoneMapPosition> posByZoneId = positionRepo.findAllActiveZonePositions()
                .stream()
                .collect(Collectors.toMap(pos -> pos.getZone().getZoneId(), pos -> pos));

        // 4. Tüm aktif zone'lar
        List<Zone> activeZones = zoneRepo.findByStatus(ZoneStatus.ACTIVE);

        List<ZoneCrowdStatusResponse> zones = new ArrayList<>();
        for (Zone zone : activeZones) {
            Long zoneId = zone.getZoneId();
            OccupancyReading reading = latestByZoneId.get(zoneId);
            AIPrediction pred = predByZoneId.get(zoneId);
            ZoneMapPosition pos = posByZoneId.get(zoneId);

            ZoneCrowdStatusResponse dto;
            if (reading != null) {
                dto = ZoneCrowdStatusResponse.from(reading, pred, pos);
                // Trend yoksa hesapla
                if (dto.getTrend() == null || "STABLE".equals(dto.getTrend())) {
                    dto.setTrend(calculateTrend(zoneId));
                }
            } else {
                dto = buildEmptyZone(zone, pos);
            }
            zones.add(dto);
        }

        // Pozisyon sıralamasına göre sırala (display_order)
        zones.sort(Comparator.comparingInt(z -> {
            ZoneMapPosition p = posByZoneId.get(z.getZoneId());
            return p != null ? p.getDisplayOrder() : 999;
        }));

        // 5. Özet hesapla
        List<String> alertZones = zones.stream()
                .filter(z -> "FULL".equals(z.getStatus()))
                .map(ZoneCrowdStatusResponse::getZoneName)
                .collect(Collectors.toList());

        List<String> suggestedZones = zones.stream()
                .filter(z -> "EMPTY".equals(z.getStatus()) || "MODERATE".equals(z.getStatus()))
                .map(ZoneCrowdStatusResponse::getZoneName)
                .collect(Collectors.toList());

        long fullCount     = zones.stream().filter(z -> "FULL".equals(z.getStatus())).count();
        long busyCount     = zones.stream().filter(z -> "BUSY".equals(z.getStatus())).count();
        long moderateCount = zones.stream().filter(z -> "MODERATE".equals(z.getStatus())).count();
        long emptyCount    = zones.stream().filter(z -> "EMPTY".equals(z.getStatus())).count();

        String aiSummary = buildAiSummary(alertZones, suggestedZones, (int) fullCount, (int) busyCount);

        log.debug("Heatmap: {} zone, {} dolu, {} yoğun", zones.size(), fullCount, busyCount);

        return new HeatmapSummaryResponse(
                activeZones.size(),
                (int) fullCount, (int) busyCount, (int) moderateCount, (int) emptyCount,
                zones,
                alertZones, suggestedZones,
                aiSummary,
                LocalDateTime.now()
        );
    }

    /**
     * Zone'un son X saatlik doluluk geçmişi — ZoneHistoryPanel grafiği için.
     * Saatlik ortalama döner (max ~24 nokta), X ekseninde gerçek "HH:MM" etiketleri kullanılır.
     */
    @Transactional(readOnly = true)
    public List<OccupancyTimeSeriesPoint> getHistory(Long zoneId, int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<Object[]> rows = occupancyRepo.findHourlyAveragesByZoneId(zoneId, since);

        return rows.stream()
                .map(row -> {
                    String label      = (String) row[0];                      // "HH:MM"
                    double avgDensity = ((Number) row[1]).doubleValue();
                    int    avgPeople  = ((Number) row[2]).intValue();
                    return OccupancyTimeSeriesPoint.occupancyOnly(label, avgDensity, avgPeople);
                })
                .collect(Collectors.toList());
    }

    // ── Yardımcı metodlar ────────────────────────────────────────────────────────

    /** Son 4 okumaya bakarak trend hesaplar */
    private String calculateTrend(Long zoneId) {
        List<OccupancyReading> recent = occupancyRepo.findTopNByZoneId(
                zoneId, PageRequest.of(0, TREND_LOOKBACK));
        if (recent.size() < 2) return "STABLE";

        // En eskiden en yeniye çevir
        List<Float> densities = recent.stream()
                .sorted(Comparator.comparing(OccupancyReading::getRecordedAt))
                .map(OccupancyReading::getDensityPct)
                .collect(Collectors.toList());

        float first = densities.get(0);
        float last  = densities.get(densities.size() - 1);
        float diff  = last - first;

        if (diff >  0.05f) return "INCREASING";
        if (diff < -0.05f) return "DECREASING";
        return "STABLE";
    }

    /** Veri olmayan zone için varsayılan boş durum */
    private ZoneCrowdStatusResponse buildEmptyZone(Zone zone, ZoneMapPosition pos) {
        ZoneCrowdStatusResponse dto = ZoneCrowdStatusResponse.builder()
                .zoneId(zone.getZoneId())
                .zoneName(zone.getZoneName())
                .zoneType(zone.getType().name())
                .currentDensity(0.0f)
                .peopleCount(0)
                .capacity(zone.getMaxCapacity())
                .status("EMPTY")
                .trend("STABLE")
                .predictedLoad(0.0f)
                .riskLevel("LOW")
                .lastUpdated(null)
                .build();

        if (pos != null) {
            dto.setSection(pos.getSection());
            dto.setPosX(pos.getPosX());
            dto.setPosY(pos.getPosY());
            dto.setWidth(pos.getWidth());
            dto.setHeight(pos.getHeight());
        }
        return dto;
    }

    /** Türkçe AI özet cümlesi oluşturur */
    private String buildAiSummary(List<String> alertZones, List<String> suggested,
                                   int fullCount, int busyCount) {
        if (alertZones.isEmpty() && busyCount == 0) {
            return "Tüm terminal bölgeleri normal yoğunlukta. Herhangi bir müdahale gerekmemektedir.";
        }

        StringBuilder sb = new StringBuilder();
        if (!alertZones.isEmpty()) {
            sb.append(String.join(", ", alertZones));
            if (alertZones.size() == 1) sb.append(" dolu durumda.");
            else sb.append(" dolu durumda.");
            sb.append(" ");
        }
        if (busyCount > 0 && alertZones.isEmpty()) {
            sb.append(busyCount).append(" bölge yoğun. ");
        }
        if (!suggested.isEmpty()) {
            List<String> alt = suggested.stream().limit(3).collect(Collectors.toList());
            sb.append("Alternatif: ").append(String.join(", ", alt)).append(" müsait.");
        }
        return sb.toString().trim();
    }
}
