package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ServiceHealthResponse;
import com.ecoterminal.model.dto.SystemStatsResponse;
import com.ecoterminal.model.dto.ZoneSettingsResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.IoTDeviceRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.UserRepository;
import com.ecoterminal.repository.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingsService Unit Tests")
class SystemSettingsServiceTest {

    @Mock private ZoneRepository             zoneRepo;
    @Mock private UserRepository             userRepo;
    @Mock private OccupancyReadingRepository occupancyRepo;
    @Mock private IoTDeviceRepository        deviceRepo;
    @Mock private JdbcTemplate               jdbcTemplate;
    @Mock private RestTemplate               restTemplate;

    @InjectMocks
    private SystemSettingsService systemSettingsService;

    // ── getSystemStats Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("getSystemStats_returnsCorrectCounts")
    void getSystemStats_returnsCorrectCounts() {
        // given
        Zone zone1 = buildZone(1L, ZoneStatus.ACTIVE);
        Zone zone2 = buildZone(2L, ZoneStatus.INACTIVE);
        IoTDevice device1 = buildDevice(DeviceStatus.ONLINE);
        IoTDevice device2 = buildDevice(DeviceStatus.OFFLINE);

        when(zoneRepo.findAll()).thenReturn(List.of(zone1, zone2));
        when(zoneRepo.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of(zone1));
        when(occupancyRepo.count()).thenReturn(1500L);
        when(userRepo.count()).thenReturn(50L);
        when(deviceRepo.count()).thenReturn(2L);
        when(deviceRepo.findByStatus(DeviceStatus.ONLINE)).thenReturn(List.of(device1));

        // when
        SystemStatsResponse result = systemSettingsService.getSystemStats();

        // then
        assertThat(result.totalZones()).isEqualTo(2);
        assertThat(result.activeZones()).isEqualTo(1);
        assertThat(result.totalReadings()).isEqualTo(1500L);
        assertThat(result.totalUsers()).isEqualTo(50L);
        assertThat(result.totalDevices()).isEqualTo(2);
        assertThat(result.onlineDevices()).isEqualTo(1);
    }

    // ── getAllZoneSettings Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllZoneSettings_returnsMappedSettings")
    void getAllZoneSettings_returnsMappedSettings() {
        // given
        Zone zone = buildZoneWithThreshold(1L, "Gate A1", 0.85f);
        when(zoneRepo.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)).thenReturn(List.of(zone));

        // when
        List<ZoneSettingsResponse> result = systemSettingsService.getAllZoneSettings();

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAllZoneSettings_withNoActiveZones_returnsEmpty")
    void getAllZoneSettings_withNoActiveZones_returnsEmpty() {
        // given
        when(zoneRepo.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)).thenReturn(List.of());

        // when
        List<ZoneSettingsResponse> result = systemSettingsService.getAllZoneSettings();

        // then
        assertThat(result).isEmpty();
    }

    // ── updateZoneThreshold Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("updateZoneThreshold_withValidZone_updatesThresholdAndAudits")
    void updateZoneThreshold_withValidZone_updatesThresholdAndAudits() {
        // given
        Zone zone = buildZoneWithThreshold(1L, "Gate A1", 0.85f);
        when(zoneRepo.findById(1L)).thenReturn(Optional.of(zone));
        when(zoneRepo.save(any(Zone.class))).thenReturn(zone);

        // when
        systemSettingsService.updateZoneThreshold(1L, 0.75f, 99L);

        // then — threshold güncellendi, audit log yazıldı
        assertThat(zone.getCriticalThreshold()).isEqualTo(0.75f);
        verify(jdbcTemplate).update(anyString(), eq(99L), eq("UPDATE_ZONE_THRESHOLD"),
                eq("zones"), eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("updateZoneThreshold_withNonExistentZone_throwsNotFound")
    void updateZoneThreshold_withNonExistentZone_throwsNotFound() {
        // given
        when(zoneRepo.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() ->
                systemSettingsService.updateZoneThreshold(99L, 0.80f, 1L))
                .isInstanceOf(BusinessException.class);
    }

    // ── checkServicesHealth Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("checkServicesHealth_returnsThreeServiceResults")
    void checkServicesHealth_returnsThreeServiceResults() {
        // given — DB sorgusu başarılı, AI servisi erişilemez
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // when
        List<ServiceHealthResponse> results = systemSettingsService.checkServicesHealth();

        // then — her zaman 3 servis durumu döner
        assertThat(results).hasSize(3);
        assertThat(results.stream().map(ServiceHealthResponse::name))
                .containsExactlyInAnyOrder("Backend (Spring Boot)", "AI Servisi (Flask)", "PostgreSQL");
    }

    @Test
    @DisplayName("checkServicesHealth_whenDbFails_returnsDownForPostgres")
    void checkServicesHealth_whenDbFails_returnsDownForPostgres() {
        // given — DB sorgusunda hata
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new RuntimeException("DB bağlantı hatası"));
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("OK");

        // when
        List<ServiceHealthResponse> results = systemSettingsService.checkServicesHealth();

        // then
        ServiceHealthResponse dbHealth = results.stream()
                .filter(r -> "PostgreSQL".equals(r.name()))
                .findFirst().orElseThrow();
        assertThat(dbHealth.status()).isEqualTo("DOWN");
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private Zone buildZone(Long id, ZoneStatus status) {
        return Zone.builder()
                .zoneId(id)
                .zoneName("Zone " + id)
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .status(status)
                .build();
    }

    private Zone buildZoneWithThreshold(Long id, String name, float threshold) {
        return Zone.builder()
                .zoneId(id)
                .zoneName(name)
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .status(ZoneStatus.ACTIVE)
                .criticalThreshold(threshold)
                .build();
    }

    private IoTDevice buildDevice(DeviceStatus status) {
        return IoTDevice.builder()
                .deviceId(1L)
                .serialNumber("SN-001")
                .status(status)
                .build();
    }
}
