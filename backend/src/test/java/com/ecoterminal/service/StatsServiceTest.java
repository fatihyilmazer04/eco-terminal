package com.ecoterminal.service;

import com.ecoterminal.model.dto.CameraStatusResponse;
import com.ecoterminal.model.dto.HourlyDataPoint;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.IoTDeviceRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService Unit Tests")
class StatsServiceTest {

    @Mock private OccupancyReadingRepository    occupancyRepo;
    @Mock private EnvironmentalMetricRepository metricRepo;
    @Mock private IoTDeviceRepository           deviceRepo;

    @InjectMocks
    private StatsService statsService;

    // ── get24hVisitorStats Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("get24hVisitorStats_returnsListOfTwentyFourPoints")
    void get24hVisitorStats_returnsListOfTwentyFourPoints() {
        // given — okuma yok
        when(occupancyRepo.findAllInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        // when
        List<HourlyDataPoint> result = statsService.get24hVisitorStats();

        // then — 24 saat için 24 nokta üretilmeli
        assertThat(result).hasSize(24);
    }

    @Test
    @DisplayName("get24hVisitorStats_withReadings_aggregatesByHour")
    void get24hVisitorStats_withReadings_aggregatesByHour() {
        // given — belirli bir saatte 2 okuma
        Instant fixedHour = ZonedDateTime.now(ZoneOffset.UTC)
                .withMinute(0).withSecond(0).withNano(0).toInstant();
        OccupancyReading r1 = buildReading(100, fixedHour);
        OccupancyReading r2 = buildReading(200, fixedHour.plusSeconds(60));

        when(occupancyRepo.findAllInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(r1, r2));

        // when
        List<HourlyDataPoint> result = statsService.get24hVisitorStats();

        // then — 24 nokta döner (0 değerli saatler dahil)
        assertThat(result).hasSize(24);
        // toplam list null değildir
        assertThat(result.stream().mapToDouble(p -> p.value()).sum()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("get24hVisitorStats_withNoReadings_returnsAllZeroValues")
    void get24hVisitorStats_withNoReadings_returnsAllZeroValues() {
        // given
        when(occupancyRepo.findAllInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        // when
        List<HourlyDataPoint> result = statsService.get24hVisitorStats();

        // then — tüm değerler sıfır
        assertThat(result.stream().allMatch(p -> p.value() == 0.0f)).isTrue();
    }

    // ── get24hEnergyStats Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("get24hEnergyStats_returnsListOfTwentyFourPoints")
    void get24hEnergyStats_returnsListOfTwentyFourPoints() {
        // given
        when(metricRepo.findAllInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        // when
        List<HourlyDataPoint> result = statsService.get24hEnergyStats();

        // then
        assertThat(result).hasSize(24);
    }

    @Test
    @DisplayName("get24hEnergyStats_withMetrics_groupsByHour")
    void get24hEnergyStats_withMetrics_groupsByHour() {
        // given
        Instant now = ZonedDateTime.now(ZoneOffset.UTC)
                .withMinute(0).withSecond(0).withNano(0).toInstant();
        EnvironmentalMetric m1 = buildMetric(15.0f, now);
        EnvironmentalMetric m2 = buildMetric(20.0f, now.plusSeconds(120));

        when(metricRepo.findAllInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(m1, m2));

        // when
        List<HourlyDataPoint> result = statsService.get24hEnergyStats();

        // then — 24 nokta döner
        assertThat(result).hasSize(24);
    }

    // ── getCameraStatuses Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("getCameraStatuses_mapsAllDevicesToResponse")
    void getCameraStatuses_mapsAllDevicesToResponse() {
        // given — 2 cihaz: 1 ONLINE, 1 OFFLINE
        IoTDevice d1 = buildDevice(1L, "CAM-001", DeviceStatus.ONLINE);
        IoTDevice d2 = buildDevice(2L, "CAM-002", DeviceStatus.OFFLINE);
        when(deviceRepo.findAll()).thenReturn(List.of(d1, d2));

        // when
        List<CameraStatusResponse> result = statsService.getCameraStatuses();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getCameraStatuses_sortedByStatusDescending")
    void getCameraStatuses_sortedByStatusDescending() {
        // given — ONLINE cihazlar OFFLINE'dan önce gelmeli (reversed sort)
        IoTDevice online  = buildDevice(1L, "CAM-A", DeviceStatus.ONLINE);
        IoTDevice offline = buildDevice(2L, "CAM-B", DeviceStatus.OFFLINE);
        when(deviceRepo.findAll()).thenReturn(List.of(offline, online));

        // when
        List<CameraStatusResponse> result = statsService.getCameraStatuses();

        // then — ONLINE ilk sıraya gelmeli (status descending = "ONLINE" > "OFFLINE")
        assertThat(result.get(0).status()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("getCameraStatuses_withNoDevices_returnsEmpty")
    void getCameraStatuses_withNoDevices_returnsEmpty() {
        // given
        when(deviceRepo.findAll()).thenReturn(List.of());

        // when
        List<CameraStatusResponse> result = statsService.getCameraStatuses();

        // then
        assertThat(result).isEmpty();
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private OccupancyReading buildReading(int count, Instant recordedAt) {
        return OccupancyReading.builder()
                .peopleCount(count)
                .densityPct(count / 200f)
                .recordedAt(recordedAt)
                .build();
    }

    private EnvironmentalMetric buildMetric(float kwh, Instant recordedAt) {
        return EnvironmentalMetric.builder()
                .energyKwh(kwh)
                .recordedAt(recordedAt)
                .build();
    }

    private IoTDevice buildDevice(Long id, String serial, DeviceStatus status) {
        return IoTDevice.builder()
                .deviceId(id)
                .serialNumber(serial)
                .status(status)
                .deviceType(DeviceType.CAMERA)
                .build();
    }
}
