package com.ecoterminal.service;

import com.ecoterminal.model.dto.EnergyResponse;
import com.ecoterminal.model.dto.SavingSuggestion;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnergyService Unit Tests")
class EnergyServiceTest {

    @Mock private EnvironmentalMetricRepository metricRepository;
    @Mock private OccupancyReadingRepository    occupancyRepository;
    @Mock private ZoneRepository                zoneRepository;

    @InjectMocks
    private EnergyService energyService;

    private Zone testZone;

    @BeforeEach
    void setUp() {
        testZone = Zone.builder()
                .zoneId(1L)
                .zoneName("Gate-A1")
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .status(ZoneStatus.ACTIVE)
                .build();
    }

    private EnvironmentalMetric buildMetric(Zone zone, float kwh) {
        EnvironmentalMetric m = new EnvironmentalMetric();
        m.setZone(zone);
        m.setEnergyKwh(kwh);
        m.setTemp(22.0f);
        m.setLightingLux(500);
        m.setRecordedAt(Instant.now());
        return m;
    }

    private OccupancyReading buildReading(Zone zone, float density) {
        return OccupancyReading.builder()
                .zone(zone)
                .densityPct(density)
                .peopleCount((int)(density * zone.getMaxCapacity()))
                .recordedAt(Instant.now())
                .build();
    }

    // ── efficiencyStatus iş kuralı ────────────────────────────────────────

    @Test
    @DisplayName("computeStatus_withLowDensityHighEnergy_returnsWasteful")
    void computeStatus_withLowDensityHighEnergy_returnsWasteful() {
        // doluluk=0.15, kwh=25.0 → WASTEFUL
        String status = EnergyResponse.computeStatus(0.15f, 25.0f);
        assertThat(status).isEqualTo("WASTEFUL");
    }

    @Test
    @DisplayName("computeStatus_withHighDensityHighEnergy_returnsEfficient")
    void computeStatus_withHighDensityHighEnergy_returnsEfficient() {
        // doluluk=0.80, kwh=25.0 → EFFICIENT (kalabalık enerji haklı)
        String status = EnergyResponse.computeStatus(0.80f, 25.0f);
        assertThat(status).isEqualTo("EFFICIENT");
    }

    @Test
    @DisplayName("computeStatus_withLowDensityLowEnergy_returnsNormal")
    void computeStatus_withLowDensityLowEnergy_returnsNormal() {
        // doluluk=0.15, kwh=8.0 → NORMAL (enerji düşük, wasteful değil)
        String status = EnergyResponse.computeStatus(0.15f, 8.0f);
        assertThat(status).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("computeStatus_withBoundaryDensity_returnsWasteful")
    void computeStatus_withBoundaryDensity_returnsWasteful() {
        // doluluk=0.19 (< 0.20), kwh=20.5 → WASTEFUL
        String status = EnergyResponse.computeStatus(0.19f, 20.5f);
        assertThat(status).isEqualTo("WASTEFUL");
    }

    // ── getSavingSuggestions ──────────────────────────────────────────────

    @Test
    @DisplayName("getSavingSuggestions_withWastefulZone_returnsSuggestion")
    void getSavingSuggestions_withWastefulZone_returnsSuggestion() {
        // given — 1 bölge, düşük doluluk + yüksek enerji → WASTEFUL
        EnvironmentalMetric metric = buildMetric(testZone, 25.0f);
        OccupancyReading reading = buildReading(testZone, 0.15f);

        when(metricRepository.findLatestPerZone()).thenReturn(List.of(metric));
        when(occupancyRepository.findLatestPerZone()).thenReturn(List.of(reading));
        when(zoneRepository.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)).thenReturn(List.of(testZone));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(testZone));

        // when
        List<SavingSuggestion> suggestions = energyService.getSavingSuggestions();

        // then
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0).zoneId()).isEqualTo(1L);
        assertThat(suggestions.get(0).potentialSavingPct()).isBetween(20, 45);
    }

    @Test
    @DisplayName("getSavingSuggestions_withHighDensityZone_returnsEmpty")
    void getSavingSuggestions_withHighDensityZone_returnsEmpty() {
        // given — yüksek doluluk → EFFICIENT → öneri yok
        EnvironmentalMetric metric = buildMetric(testZone, 25.0f);
        OccupancyReading reading = buildReading(testZone, 0.80f);

        when(metricRepository.findLatestPerZone()).thenReturn(List.of(metric));
        when(occupancyRepository.findLatestPerZone()).thenReturn(List.of(reading));

        // when
        List<SavingSuggestion> suggestions = energyService.getSavingSuggestions();

        // then
        assertThat(suggestions).isEmpty();
    }

    @Test
    @DisplayName("getSavingSuggestions_withLowDensityLowEnergy_returnsEmpty")
    void getSavingSuggestions_withLowDensityLowEnergy_returnsEmpty() {
        // given — düşük doluluk AMA düşük enerji → NORMAL → öneri yok
        EnvironmentalMetric metric = buildMetric(testZone, 8.0f);
        OccupancyReading reading = buildReading(testZone, 0.15f);

        when(metricRepository.findLatestPerZone()).thenReturn(List.of(metric));
        when(occupancyRepository.findLatestPerZone()).thenReturn(List.of(reading));

        // when
        List<SavingSuggestion> suggestions = energyService.getSavingSuggestions();

        // then
        assertThat(suggestions).isEmpty();
    }
}
