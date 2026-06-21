package com.ecoterminal.service;

import com.ecoterminal.model.dto.ZoneCrowdStatusResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrowdStatusService Unit Tests")
class CrowdStatusServiceTest {

    @Mock private ZoneRepository             zoneRepo;
    @Mock private OccupancyReadingRepository occupancyRepo;
    @Mock private AIPredictionRepository     predictionRepo;

    @InjectMocks
    private CrowdStatusService crowdStatusService;

    // ── getAllZoneStatuses Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllZoneStatuses_returnsStatusForActiveZonesWithReadings")
    void getAllZoneStatuses_returnsStatusForActiveZonesWithReadings() {
        // given — 1 aktif zone, 1 okuma
        Zone activeZone = buildZone(1L, "Gate A1", ZoneStatus.ACTIVE);
        OccupancyReading reading = buildReading(activeZone, 0.60f);

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(reading));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(zoneRepo.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of()); // hepsi reading'de zaten

        // when
        List<ZoneCrowdStatusResponse> result = crowdStatusService.getAllZoneStatuses();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getZoneId()).isEqualTo(1L);
        assertThat(result.get(0).getZoneName()).isEqualTo("Gate A1");
    }

    @Test
    @DisplayName("getAllZoneStatuses_excludesInactiveZones")
    void getAllZoneStatuses_excludesInactiveZones() {
        // given — 1 INACTIVE zone'a ait okuma var
        Zone inactiveZone = buildZone(2L, "Old Gate", ZoneStatus.INACTIVE);
        OccupancyReading reading = buildReading(inactiveZone, 0.50f);

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(reading));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(zoneRepo.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of());

        // when
        List<ZoneCrowdStatusResponse> result = crowdStatusService.getAllZoneStatuses();

        // then — inactive zone dahil edilmemeli
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllZoneStatuses_includesActiveZonesWithNoReadings")
    void getAllZoneStatuses_includesActiveZonesWithNoReadings() {
        // given — reading yok, ama 1 aktif zone var
        Zone zoneWithNoReading = buildZone(3L, "Lounge-1", ZoneStatus.ACTIVE);

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of());
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(zoneRepo.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of(zoneWithNoReading));

        // when
        List<ZoneCrowdStatusResponse> result = crowdStatusService.getAllZoneStatuses();

        // then — okuma olmayan zone EMPTY olarak dahil edilmeli
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getZoneId()).isEqualTo(3L);
        assertThat(result.get(0).getStatus()).isEqualTo("EMPTY");
        assertThat(result.get(0).getPeopleCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAllZoneStatuses_combinesReadingsAndPredictions")
    void getAllZoneStatuses_combinesReadingsAndPredictions() {
        // given — 1 aktif zone, 1 okuma + 1 tahmin
        Zone zone = buildZone(1L, "Gate A1", ZoneStatus.ACTIVE);
        OccupancyReading reading = buildReading(zone, 0.70f);
        AIPrediction prediction  = buildPrediction(zone, 0.80f, "HIGH");

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(reading));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of(prediction));
        when(zoneRepo.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of());

        // when
        List<ZoneCrowdStatusResponse> result = crowdStatusService.getAllZoneStatuses();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPredictedLoad()).isGreaterThan(0.0f);
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private Zone buildZone(Long id, String name, ZoneStatus status) {
        return Zone.builder()
                .zoneId(id)
                .zoneName(name)
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .status(status)
                .build();
    }

    private OccupancyReading buildReading(Zone zone, float density) {
        return OccupancyReading.builder()
                .zone(zone)
                .densityPct(density)
                .peopleCount((int)(density * zone.getMaxCapacity()))
                .recordedAt(Instant.now())
                .build();
    }

    private AIPrediction buildPrediction(Zone zone, float load, String riskLevel) {
        return AIPrediction.builder()
                .zone(zone)
                .predictedLoad(load)
                .densityPct(load)
                .riskLevel(riskLevel)
                .trend("STABLE")
                .confidence(0.80f)
                .forecastTime(Instant.now())
                .generatedAt(Instant.now())
                .build();
    }
}
