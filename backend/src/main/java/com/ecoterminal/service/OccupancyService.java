package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.HeatmapResponse;
import com.ecoterminal.model.dto.RedirectRequest;
import com.ecoterminal.model.dto.RedirectResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.dto.ZoneResponse;
import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// audit_logs JDBC
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OccupancyService {

    private final ZoneRepository zoneRepository;
    private final OccupancyReadingRepository occupancyReadingRepository;
    private final JdbcTemplate jdbcTemplate;

    // ── Bölge Listesi ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAllZones() {
        return zoneRepository.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)
                .stream()
                .map(ZoneResponse::from)
                .toList();
    }

    // ── Tek Bölge Anlık Yoğunluğu ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ZoneOccupancyResponse getCurrentOccupancy(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        return occupancyReadingRepository
                .findTopByZoneOrderByRecordedAtDesc(zone)
                .map(r -> toOccupancyResponse(zone, r))
                .orElse(ZoneOccupancyResponse.empty(
                        zone.getZoneId(), zone.getZoneName(),
                        zone.getType(), zone.getMaxCapacity(),
                        zone.getCriticalThreshold()
                ));
    }

    // ── Heatmap (tüm bölgeler, tek sorgu) ─────────────────────────────────

    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmapData() {
        List<Zone> zones = zoneRepository.findByStatus(ZoneStatus.ACTIVE);

        // Tüm bölgelerin son okuması tek sorguda — N+1 yok
        Map<Long, OccupancyReading> latestByZoneId =
                occupancyReadingRepository.findLatestPerZone()
                        .stream()
                        .collect(Collectors.toMap(
                                r -> r.getZone().getZoneId(),
                                r -> r,
                                (existing, duplicate) -> existing   // DISTINCT ON zaten önler; güvenlik ağı
                        ));

        List<ZoneOccupancyResponse> responses = zones.stream()
                .map(zone -> {
                    OccupancyReading reading = latestByZoneId.get(zone.getZoneId());
                    if (reading == null) {
                        return ZoneOccupancyResponse.empty(
                                zone.getZoneId(), zone.getZoneName(),
                                zone.getType(), zone.getMaxCapacity(),
                                zone.getCriticalThreshold()
                        );
                    }
                    return toOccupancyResponse(zone, reading);
                })
                .toList();

        log.debug("Heatmap generated: {} zones, {} critical",
                responses.size(),
                responses.stream().filter(ZoneOccupancyResponse::isCritical).count());

        return HeatmapResponse.of(responses);
    }

    // ── Tüm anlık yoğunluklar (current endpoint için) ─────────────────────

    @Transactional(readOnly = true)
    public List<ZoneOccupancyResponse> getAllZonesWithOccupancy() {
        return getHeatmapData().zones();
    }

    // ── Yoğun Bölgeden Yolcu Yönlendirme ─────────────────────────────────────

    @Transactional
    public RedirectResponse redirectPassengers(RedirectRequest request, Long actorId) {
        Zone fromZone = zoneRepository.findById(request.fromZoneId())
                .orElseThrow(() -> BusinessException.notFound("Kaynak bölge"));
        Zone toZone = zoneRepository.findById(request.toZoneId())
                .orElseThrow(() -> BusinessException.notFound("Hedef bölge"));

        // Mevcut anlık yolcu sayısı (yönlendirme sayısı için)
        int fromCount = occupancyReadingRepository.findTopByZoneOrderByRecordedAtDesc(fromZone)
                .map(OccupancyReading::getPeopleCount).orElse(0);
        // Tahmini yönlendirme: doluluk > eşik olan kısımdaki yolcu sayısı
        int notifCount = Math.max(1, (int) (fromCount * 0.3));

        // Audit log
        String oldVal = String.format("{\"zone\":\"%s\",\"action\":\"redirect_source\"}", fromZone.getZoneName());
        String newVal = String.format("{\"zone\":\"%s\",\"message\":\"%s\",\"estimated_redirected\":%d}",
                toZone.getZoneName(), request.message().replace("\"", "'"), notifCount);
        jdbcTemplate.update(
                "INSERT INTO audit_logs (actor_id, action_type, target_table, target_id, old_value, new_value) " +
                "VALUES (?, 'REDIRECT', 'zones', ?, ?::jsonb, ?::jsonb)",
                actorId, fromZone.getZoneId(), oldVal, newVal);

        log.info("Admin {} yönlendirdi: {} → {}, mesaj='{}'", actorId, fromZone.getZoneName(), toZone.getZoneName(), request.message());

        return new RedirectResponse(
                fromZone.getZoneId(), fromZone.getZoneName(),
                toZone.getZoneId(), toZone.getZoneName(),
                request.message(), notifCount, Instant.now()
        );
    }

    // ── DensityLevel Hesaplama ─────────────────────────────────────────────

    /**
     * density_pct → DensityLevel enum.
     * Eşik değerleri DensityLevel.of() statik metodunda merkezi tanımlı.
     */
    public DensityLevel getDensityLevel(Float densityPct) {
        if (densityPct == null) return DensityLevel.LOW;
        return DensityLevel.of(densityPct);
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private ZoneOccupancyResponse toOccupancyResponse(Zone zone, OccupancyReading reading) {
        DensityLevel level = DensityLevel.of(reading.getDensityPct());
        return new ZoneOccupancyResponse(
                zone.getZoneId(),
                zone.getZoneName(),
                zone.getType().name(),
                zone.getMaxCapacity(),
                zone.getCriticalThreshold(),
                reading.getPeopleCount(),
                reading.getDensityPct(),
                level,
                level.getColorCode(),
                reading.getRecordedAt()
        );
    }
}
