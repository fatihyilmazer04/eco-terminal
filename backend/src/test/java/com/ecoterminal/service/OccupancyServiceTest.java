package com.ecoterminal.service;

import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.*;
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
@DisplayName("OccupancyService Unit Tests")
class OccupancyServiceTest {

    @Mock private ZoneRepository              zoneRepository;
    @Mock private OccupancyReadingRepository  occupancyReadingRepository;

    @InjectMocks
    private OccupancyService occupancyService;

    private Zone testZone;

    @BeforeEach
    void setUp() {
        testZone = Zone.builder()
                .zoneId(1L)
                .zoneName("Test Gate")
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .criticalThreshold(0.85f)
                .status(ZoneStatus.ACTIVE)
                .build();
    }

    // ── DensityLevel Hesaplama ─────────────────────────────────────────────

    @Test
    @DisplayName("getDensityLevel_withLowValue_returnsLow")
    void getDensityLevel_withLowValue_returnsLow() {
        assertThat(occupancyService.getDensityLevel(0.45f)).isEqualTo(DensityLevel.LOW);
    }

    @Test
    @DisplayName("getDensityLevel_withHighValue_returnsHigh")
    void getDensityLevel_withHighValue_returnsHigh() {
        assertThat(occupancyService.getDensityLevel(0.87f)).isEqualTo(DensityLevel.HIGH);
    }

    @Test
    @DisplayName("getDensityLevel_withCriticalValue_returnsCritical")
    void getDensityLevel_withCriticalValue_returnsCritical() {
        assertThat(occupancyService.getDensityLevel(0.96f)).isEqualTo(DensityLevel.CRITICAL);
    }

    @Test
    @DisplayName("getDensityLevel_atExactThreshold_returnsHigh")
    void getDensityLevel_atExactThreshold_returnsHigh() {
        // 0.85 → HIGH (>= 0.85 ve < 0.95)
        assertThat(occupancyService.getDensityLevel(0.85f)).isEqualTo(DensityLevel.HIGH);
    }

    @Test
    @DisplayName("getDensityLevel_atMediumBoundary_returnsMedium")
    void getDensityLevel_atMediumBoundary_returnsMedium() {
        // 0.60 → MEDIUM başlangıcı
        assertThat(occupancyService.getDensityLevel(0.60f)).isEqualTo(DensityLevel.MEDIUM);
    }

    // ── Anlık Yoğunluk ────────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentOccupancy_withNoReadings_returnsZeroDensity")
    void getCurrentOccupancy_withNoReadings_returnsZeroDensity() {
        // given
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(testZone));
        when(occupancyReadingRepository.findTopByZoneOrderByRecordedAtDesc(testZone))
                .thenReturn(Optional.empty());

        // when
        ZoneOccupancyResponse result = occupancyService.getCurrentOccupancy(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.densityPct()).isEqualTo(0.0f);
        assertThat(result.zoneId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getCurrentOccupancy_withReading_returnsDensityFromReading")
    void getCurrentOccupancy_withReading_returnsDensityFromReading() {
        // given
        OccupancyReading reading = OccupancyReading.builder()
                .zone(testZone)
                .peopleCount(90)
                .densityPct(0.45f)
                .recordedAt(Instant.now())
                .build();

        when(zoneRepository.findById(1L)).thenReturn(Optional.of(testZone));
        when(occupancyReadingRepository.findTopByZoneOrderByRecordedAtDesc(testZone))
                .thenReturn(Optional.of(reading));

        // when
        ZoneOccupancyResponse result = occupancyService.getCurrentOccupancy(1L);

        // then
        assertThat(result.densityPct()).isEqualTo(0.45f);
        assertThat(result.densityLevel()).isEqualTo(DensityLevel.LOW);
    }

    // ── Renk Kodu Doğrulama ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllZonesWithOccupancy_returnsCorrectColorCodes")
    void getAllZonesWithOccupancy_returnsCorrectColorCodes() {
        // DensityLevel enum renk kodları doğrudan test edilir
        assertThat(DensityLevel.LOW.getColorCode()).isEqualTo("#2ECC71");
        assertThat(DensityLevel.MEDIUM.getColorCode()).isEqualTo("#F39C12");
        assertThat(DensityLevel.HIGH.getColorCode()).isEqualTo("#E67E22");
        assertThat(DensityLevel.CRITICAL.getColorCode()).isEqualTo("#E74C3C");
    }

    @Test
    @DisplayName("getAllZonesWithOccupancy_withActiveZones_returnsList")
    void getAllZonesWithOccupancy_withActiveZones_returnsList() {
        // given — boş heatmap döner (latestPerZone boş)
        when(zoneRepository.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of(testZone));
        when(occupancyReadingRepository.findLatestPerZone()).thenReturn(List.of());

        // when
        List<ZoneOccupancyResponse> result = occupancyService.getAllZonesWithOccupancy();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).densityPct()).isEqualTo(0.0f);
    }
}
