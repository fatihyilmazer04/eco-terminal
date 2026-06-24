package com.ecoterminal.service;

import com.ecoterminal.model.dto.HeatmapResponse;
import com.ecoterminal.model.dto.HeatmapSummaryResponse;
import com.ecoterminal.model.dto.ZoneCrowdStatusResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneMapPosition;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.ZoneMapPositionRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo modunda sabit doluluk değerleri döndürür.
 * app.demo.fixed-heatmap=true olduğunda CrowdMonitorService ve OccupancyService
 * bu sınıfın metotlarını çağırır; veritabanı okumaları yapılmaz.
 *
 * Değerler bilinçli olarak tüm statü kümesini (FULL, BUSY, MODERATE, EMPTY)
 * gösterecek şekilde seçilmiştir.
 */
@Service
@RequiredArgsConstructor
public class DemoOccupancyProvider {

    private final ZoneRepository            zoneRepo;
    private final ZoneMapPositionRepository positionRepo;

    /** Sabit demo doluluk oranları (0.0–1.0). Anahtar = zone adı. */
    private static final Map<String, Float> DEMO_DENSITY = Map.ofEntries(
        Map.entry("Lounge-1",       0.93f),   // FULL
        Map.entry("Gate A1",        0.87f),   // FULL
        Map.entry("Security-1",     0.78f),   // BUSY
        Map.entry("Gate B2",        0.72f),   // BUSY
        Map.entry("CheckIn-1",      0.65f),   // BUSY
        Map.entry("Gate C3",        0.58f),   // MODERATE
        Map.entry("Gate A2",        0.52f),   // MODERATE
        Map.entry("Gate B1",        0.45f),   // MODERATE
        Map.entry("CheckIn-2",      0.38f),   // EMPTY
        Map.entry("Gate B3",        0.33f),   // EMPTY
        Map.entry("Gate C1",        0.28f),   // EMPTY
        Map.entry("Security-2",     0.25f),   // EMPTY
        Map.entry("Gate A3",        0.22f),   // EMPTY
        Map.entry("Gate C2",        0.18f),   // EMPTY
        Map.entry("Lounge-2",       0.12f)    // EMPTY
    );

    /** Kayıt yoksa fallback density */
    private static final float FALLBACK_DENSITY = 0.30f;

    private float getDensity(String zoneName) {
        return DEMO_DENSITY.getOrDefault(zoneName, FALLBACK_DENSITY);
    }

    // ── OccupancyService → /api/occupancy/heatmap ───────────────────────────

    /**
     * OccupancyService.getHeatmapData() yerine çağrılır.
     * ZoneOccupancyResponse listesi + meta döndürür.
     */
    @Transactional(readOnly = true)
    public HeatmapResponse buildOccupancyHeatmapResponse() {
        List<Zone> zones = zoneRepo.findByStatus(ZoneStatus.ACTIVE);

        List<ZoneOccupancyResponse> responses = zones.stream()
                .map(zone -> {
                    float density = getDensity(zone.getZoneName());
                    DensityLevel level = DensityLevel.of(density);
                    int capacity = zone.getMaxCapacity() != null ? zone.getMaxCapacity() : 100;
                    int people   = Math.round(density * capacity);

                    return new ZoneOccupancyResponse(
                            zone.getZoneId(),
                            zone.getZoneName(),
                            zone.getType().name(),
                            zone.getMaxCapacity(),
                            zone.getCriticalThreshold(),
                            people,
                            density,
                            level,
                            level.getColorCode(),
                            Instant.now()
                    );
                })
                .toList();

        return HeatmapResponse.of(responses);
    }

    // ── CrowdMonitorService → /api/heatmap/live ──────────────────────────────

    /**
     * CrowdMonitorService.getHeatmapData() yerine çağrılır.
     * ZoneCrowdStatusResponse + SVG koordinatları + özet döndürür.
     */
    @Transactional(readOnly = true)
    public HeatmapSummaryResponse buildHeatmapSummaryResponse() {
        List<Zone> zones = zoneRepo.findByStatus(ZoneStatus.ACTIVE);

        Map<Long, ZoneMapPosition> posByZoneId = positionRepo.findAllActiveZonePositions()
                .stream()
                .collect(Collectors.toMap(pos -> pos.getZone().getZoneId(), pos -> pos));

        List<ZoneCrowdStatusResponse> zoneDtos = new ArrayList<>();
        for (Zone zone : zones) {
            float density    = getDensity(zone.getZoneName());
            ZoneMapPosition pos = posByZoneId.get(zone.getZoneId());
            int capacity     = zone.getMaxCapacity() != null ? zone.getMaxCapacity() : 100;
            int people       = Math.round(density * capacity);

            ZoneCrowdStatusResponse dto = ZoneCrowdStatusResponse.builder()
                    .zoneId(zone.getZoneId())
                    .zoneName(zone.getZoneName())
                    .zoneType(zone.getType().name())
                    .currentDensity(density)
                    .peopleCount(people)
                    .capacity(zone.getMaxCapacity())
                    .status(ZoneCrowdStatusResponse.resolveStatus(density))
                    .trend("STABLE")
                    .predictedLoad(density)
                    .riskLevel(ZoneCrowdStatusResponse.resolveRisk(density))
                    .lastUpdated(Instant.now())
                    .build();

            if (pos != null) {
                dto.setSection(pos.getSection());
                dto.setPosX(pos.getPosX());
                dto.setPosY(pos.getPosY());
                dto.setWidth(pos.getWidth());
                dto.setHeight(pos.getHeight());
            }
            zoneDtos.add(dto);
        }

        // Harita görüntüleme sırasına göre sırala
        zoneDtos.sort(Comparator.comparingInt(z -> {
            ZoneMapPosition p = posByZoneId.get(z.getZoneId());
            return p != null ? p.getDisplayOrder() : 999;
        }));

        List<String> alertZones = zoneDtos.stream()
                .filter(z -> "FULL".equals(z.getStatus()))
                .map(ZoneCrowdStatusResponse::getZoneName)
                .collect(Collectors.toList());

        List<String> suggestedZones = zoneDtos.stream()
                .filter(z -> "EMPTY".equals(z.getStatus()) || "MODERATE".equals(z.getStatus()))
                .map(ZoneCrowdStatusResponse::getZoneName)
                .collect(Collectors.toList());

        long fullCount     = zoneDtos.stream().filter(z -> "FULL".equals(z.getStatus())).count();
        long busyCount     = zoneDtos.stream().filter(z -> "BUSY".equals(z.getStatus())).count();
        long moderateCount = zoneDtos.stream().filter(z -> "MODERATE".equals(z.getStatus())).count();
        long emptyCount    = zoneDtos.stream().filter(z -> "EMPTY".equals(z.getStatus())).count();

        String aiSummary = buildAiSummary(alertZones, suggestedZones, (int) fullCount, (int) busyCount);

        return new HeatmapSummaryResponse(
                zones.size(),
                (int) fullCount, (int) busyCount, (int) moderateCount, (int) emptyCount,
                zoneDtos,
                alertZones, suggestedZones,
                aiSummary,
                LocalDateTime.now()
        );
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private String buildAiSummary(List<String> alertZones, List<String> suggested,
                                   int fullCount, int busyCount) {
        if (alertZones.isEmpty() && busyCount == 0) {
            return "Tüm terminal bölgeleri normal yoğunlukta. Herhangi bir müdahale gerekmemektedir.";
        }
        StringBuilder sb = new StringBuilder();
        if (!alertZones.isEmpty()) {
            sb.append(String.join(", ", alertZones)).append(" dolu durumda. ");
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
