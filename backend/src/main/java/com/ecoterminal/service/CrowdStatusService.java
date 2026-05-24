package com.ecoterminal.service;

import com.ecoterminal.model.dto.ZoneCrowdStatusResponse;
import com.ecoterminal.model.entity.AIPrediction;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrowdStatusService {

    private final ZoneRepository              zoneRepo;
    private final OccupancyReadingRepository  occupancyRepo;
    private final AIPredictionRepository      predictionRepo;

    /**
     * Tüm aktif zone'ların anlık kalabalık durumunu döndürür.
     * En son OccupancyReading + en son AIPrediction join edilir.
     */
    @Transactional(readOnly = true)
    public List<ZoneCrowdStatusResponse> getAllZoneStatuses() {

        // Her zone'un en son okuması (tek sorgu)
        List<OccupancyReading> latestReadings = occupancyRepo.findLatestPerZone();

        // Her zone'un en son AI tahmini (tek sorgu)
        Map<Long, AIPrediction> predByZone = predictionRepo.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getZone().getZoneId(),
                        p -> p
                ));

        List<ZoneCrowdStatusResponse> result = latestReadings.stream()
                .filter(r -> r.getZone().getStatus() == ZoneStatus.ACTIVE)
                .map(r -> {
                    Long zoneId = r.getZone().getZoneId();
                    AIPrediction pred = predByZone.get(zoneId);
                    return ZoneCrowdStatusResponse.from(r, pred);
                })
                .collect(Collectors.toList());

        // Okuma olmayan aktif zone'ları da dahil et (boş göster)
        List<Long> readingZoneIds = latestReadings.stream()
                .map(r -> r.getZone().getZoneId())
                .collect(Collectors.toList());

        zoneRepo.findByStatus(ZoneStatus.ACTIVE).stream()
                .filter(z -> !readingZoneIds.contains(z.getZoneId()))
                .map(z -> emptyZoneStatus(z))
                .forEach(result::add);

        log.debug("CrowdStatus: {} zone döndürüldü", result.size());
        return result;
    }

    private ZoneCrowdStatusResponse emptyZoneStatus(Zone zone) {
        return ZoneCrowdStatusResponse.builder()
                .zoneId(zone.getZoneId())
                .zoneName(zone.getZoneName())
                .zoneType(zone.getType().name())
                .currentDensity(0.0f)
                .peopleCount(0)
                .capacity(zone.getMaxCapacity())
                .status("EMPTY")
                .trend("STABLE")
                .predictedLoad(0.0f)
                .lastUpdated(null)
                .build();
    }
}
