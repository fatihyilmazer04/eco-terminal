package com.ecoterminal.service;

import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneType;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneMapPositionRepository;
import com.ecoterminal.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CrowdMonitorService Unit Tests")
class CrowdMonitorServiceTest {

    @Mock private ZoneRepository             zoneRepo;
    @Mock private OccupancyReadingRepository occupancyRepo;
    @Mock private AIPredictionRepository     predictionRepo;
    @Mock private ZoneMapPositionRepository  positionRepo;
    @Mock private DemoOccupancyProvider      demoProvider;

    @InjectMocks
    private CrowdMonitorService crowdMonitorService;

    private Zone testZone;

    @BeforeEach
    void setUp() {
        // Demo modu kapalı — gerçek veri akışı test edilecek
        ReflectionTestUtils.setField(crowdMonitorService, "demoMode", false);

        testZone = Zone.builder()
                .zoneId(1L)
                .zoneName("Gate A1")
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .build();
    }

    // ── calculateTrend Tests (getHistory üzerinden dolaylı) ───────────────

    @Test
    @DisplayName("getHistory_returnsTimeSeriesPoints")
    void getHistory_returnsTimeSeriesPoints() {
        // given
        Object[] row1 = {"08:00", 0.45, 90};
        Object[] row2 = {"09:00", 0.60, 120};
        when(occupancyRepo.findHourlyAveragesByZoneId(eq(1L), any(Instant.class)))
                .thenReturn(List.of(row1, row2));

        // when
        var result = crowdMonitorService.getHistory(1L, 24);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).time()).isEqualTo("08:00");
        assertThat(result.get(0).densityPct()).isEqualTo(0.45);
        assertThat(result.get(1).time()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("getHistory_withEmptyReadings_returnsEmptyList")
    void getHistory_withEmptyReadings_returnsEmptyList() {
        // given
        when(occupancyRepo.findHourlyAveragesByZoneId(eq(1L), any(Instant.class)))
                .thenReturn(List.of());

        // when
        var result = crowdMonitorService.getHistory(1L, 6);

        // then
        assertThat(result).isEmpty();
    }

    // ── getHeatmapData Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("getHeatmapData_inDemoMode_callsDemoProvider")
    void getHeatmapData_inDemoMode_callsDemoProvider() {
        // given — demo modu açık
        ReflectionTestUtils.setField(crowdMonitorService, "demoMode", true);
        when(demoProvider.buildHeatmapSummaryResponse()).thenReturn(null);

        // when
        crowdMonitorService.getHeatmapData();

        // then
        verify(demoProvider).buildHeatmapSummaryResponse();
        verify(zoneRepo, never()).findByStatus(any());
    }

    @Test
    @DisplayName("getHeatmapData_inLiveMode_queriesRepositories")
    void getHeatmapData_inLiveMode_queriesRepositories() {
        // given — demo kapalı
        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of());
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(positionRepo.findAllActiveZonePositions()).thenReturn(List.of());
        when(zoneRepo.findByStatus(any())).thenReturn(List.of(testZone));
        when(occupancyRepo.findTopNByZoneId(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of());

        // when
        var result = crowdMonitorService.getHeatmapData();

        // then
        assertThat(result).isNotNull();
        assertThat(result.totalZones()).isEqualTo(1);
        verify(zoneRepo).findByStatus(any());
        verify(demoProvider, never()).buildHeatmapSummaryResponse();
    }

    // ── calculateTrend (via getHeatmapData) ───────────────────────────────

    @Test
    @DisplayName("trend_INCREASING_whenDensityRisesMoreThan5Pct")
    void trend_INCREASING_whenDensityRisesMoreThan5Pct() {
        // given — 4 okuma: 0.30 → 0.40 → 0.50 → 0.60 (fark 0.30 > 0.05)
        OccupancyReading r1 = buildReading(testZone, 0.30f, Instant.now().minus(3, ChronoUnit.MINUTES));
        OccupancyReading r2 = buildReading(testZone, 0.40f, Instant.now().minus(2, ChronoUnit.MINUTES));
        OccupancyReading r3 = buildReading(testZone, 0.50f, Instant.now().minus(1, ChronoUnit.MINUTES));
        OccupancyReading r4 = buildReading(testZone, 0.60f, Instant.now());

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(r4));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(positionRepo.findAllActiveZonePositions()).thenReturn(List.of());
        when(zoneRepo.findByStatus(any())).thenReturn(List.of(testZone));
        when(occupancyRepo.findTopNByZoneId(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(r4, r3, r2, r1));

        // when
        var result = crowdMonitorService.getHeatmapData();

        // then
        assertThat(result.zones()).hasSize(1);
        assertThat(result.zones().get(0).getTrend()).isEqualTo("INCREASING");
    }

    @Test
    @DisplayName("trend_DECREASING_whenDensityDropsMoreThan5Pct")
    void trend_DECREASING_whenDensityDropsMoreThan5Pct() {
        // given — 4 okuma: 0.80 → 0.70 → 0.60 → 0.50 (fark -0.30 < -0.05)
        OccupancyReading r1 = buildReading(testZone, 0.80f, Instant.now().minus(3, ChronoUnit.MINUTES));
        OccupancyReading r2 = buildReading(testZone, 0.70f, Instant.now().minus(2, ChronoUnit.MINUTES));
        OccupancyReading r3 = buildReading(testZone, 0.60f, Instant.now().minus(1, ChronoUnit.MINUTES));
        OccupancyReading r4 = buildReading(testZone, 0.50f, Instant.now());

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(r4));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(positionRepo.findAllActiveZonePositions()).thenReturn(List.of());
        when(zoneRepo.findByStatus(any())).thenReturn(List.of(testZone));
        when(occupancyRepo.findTopNByZoneId(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(r4, r3, r2, r1));

        // when
        var result = crowdMonitorService.getHeatmapData();

        // then
        assertThat(result.zones().get(0).getTrend()).isEqualTo("DECREASING");
    }

    @Test
    @DisplayName("trend_STABLE_whenDensityChangeLessThan5Pct")
    void trend_STABLE_whenDensityChangeLessThan5Pct() {
        // given — 4 okuma: 0.50 → 0.51 → 0.52 → 0.53 (fark 0.03 < 0.05)
        OccupancyReading r1 = buildReading(testZone, 0.50f, Instant.now().minus(3, ChronoUnit.MINUTES));
        OccupancyReading r2 = buildReading(testZone, 0.51f, Instant.now().minus(2, ChronoUnit.MINUTES));
        OccupancyReading r3 = buildReading(testZone, 0.52f, Instant.now().minus(1, ChronoUnit.MINUTES));
        OccupancyReading r4 = buildReading(testZone, 0.53f, Instant.now());

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(r4));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(positionRepo.findAllActiveZonePositions()).thenReturn(List.of());
        when(zoneRepo.findByStatus(any())).thenReturn(List.of(testZone));
        when(occupancyRepo.findTopNByZoneId(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(r4, r3, r2, r1));

        // when
        var result = crowdMonitorService.getHeatmapData();

        // then
        assertThat(result.zones().get(0).getTrend()).isEqualTo("STABLE");
    }

    @Test
    @DisplayName("trend_STABLE_whenFewerThan2Readings")
    void trend_STABLE_whenFewerThan2Readings() {
        // given — sadece 1 okuma var
        OccupancyReading r1 = buildReading(testZone, 0.70f, Instant.now());

        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of(r1));
        when(predictionRepo.findLatestPerZone()).thenReturn(List.of());
        when(positionRepo.findAllActiveZonePositions()).thenReturn(List.of());
        when(zoneRepo.findByStatus(any())).thenReturn(List.of(testZone));
        when(occupancyRepo.findTopNByZoneId(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(r1)); // sadece 1

        // when
        var result = crowdMonitorService.getHeatmapData();

        // then
        assertThat(result.zones().get(0).getTrend()).isEqualTo("STABLE");
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private OccupancyReading buildReading(Zone zone, float density, Instant recordedAt) {
        return OccupancyReading.builder()
                .zone(zone)
                .densityPct(density)
                .peopleCount((int)(density * zone.getMaxCapacity()))
                .recordedAt(recordedAt)
                .build();
    }
}
