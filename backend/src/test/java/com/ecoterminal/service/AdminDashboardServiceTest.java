package com.ecoterminal.service;

import com.ecoterminal.model.dto.AdminDashboardResponse;
import com.ecoterminal.model.dto.SavingSuggestion;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.FlightStatus;
import com.ecoterminal.repository.FlightRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardService Unit Tests")
class AdminDashboardServiceTest {

    @Mock private OccupancyService occupancyService;
    @Mock private EnergyService    energyService;
    @Mock private FlightRepository flightRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository   userRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
    }

    // ── getSummary Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("getSummary_aggregatesZonePassengerCounts")
    void getSummary_aggregatesZonePassengerCounts() {
        // given — 2 zone, 50 + 80 = 130 yolcu
        List<ZoneOccupancyResponse> zones = List.of(
                buildZoneOccupancy(1L, "Gate A1", 50, 0.25f, DensityLevel.LOW),
                buildZoneOccupancy(2L, "Gate B2", 80, 0.40f, DensityLevel.LOW)
        );
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(zones);
        when(energyService.getTotalEnergyKwh()).thenReturn(120.5f);
        when(energyService.getSavingSuggestions()).thenReturn(List.of());
        when(flightRepository.findByStatus(FlightStatus.SCHEDULED)).thenReturn(List.of());
        when(flightRepository.findByStatus(FlightStatus.BOARDING)).thenReturn(List.of());
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByCreatedAtBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(3L);

        // when
        AdminDashboardResponse result = adminDashboardService.getSummary();

        // then
        assertThat(result.totalPassengers()).isEqualTo(130);
    }

    @Test
    @DisplayName("getSummary_countsCriticalZones")
    void getSummary_countsCriticalZones() {
        // given — 1 HIGH, 1 CRITICAL, 1 LOW → kritik sayısı = 2
        List<ZoneOccupancyResponse> zones = List.of(
                buildZoneOccupancy(1L, "Gate A1", 170, 0.85f, DensityLevel.HIGH),
                buildZoneOccupancy(2L, "Gate B2", 190, 0.95f, DensityLevel.CRITICAL),
                buildZoneOccupancy(3L, "Lounge", 20, 0.10f, DensityLevel.LOW)
        );
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(zones);
        when(energyService.getTotalEnergyKwh()).thenReturn(200f);
        when(energyService.getSavingSuggestions()).thenReturn(List.of());
        when(flightRepository.findByStatus(any())).thenReturn(List.of());
        when(userRepository.count()).thenReturn(50L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);

        // when
        AdminDashboardResponse result = adminDashboardService.getSummary();

        // then
        assertThat(result.criticalZoneCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getSummary_calculatesAverageDensityPct")
    void getSummary_calculatesAverageDensityPct() {
        // given — densities: 0.20 + 0.60 = avg 0.40
        List<ZoneOccupancyResponse> zones = List.of(
                buildZoneOccupancy(1L, "Zone A", 40, 0.20f, DensityLevel.LOW),
                buildZoneOccupancy(2L, "Zone B", 120, 0.60f, DensityLevel.MEDIUM)
        );
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(zones);
        when(energyService.getTotalEnergyKwh()).thenReturn(80f);
        when(energyService.getSavingSuggestions()).thenReturn(List.of());
        when(flightRepository.findByStatus(any())).thenReturn(List.of());
        when(userRepository.count()).thenReturn(200L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);

        // when
        AdminDashboardResponse result = adminDashboardService.getSummary();

        // then
        assertThat(result.averageDensityPct()).isCloseTo(0.40f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    @DisplayName("getSummary_countsActiveFlights")
    void getSummary_countsActiveFlights() {
        // given — 3 SCHEDULED + 2 BOARDING = 5 aktif uçuş
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of());
        when(energyService.getTotalEnergyKwh()).thenReturn(0f);
        when(energyService.getSavingSuggestions()).thenReturn(List.of());
        when(flightRepository.findByStatus(FlightStatus.SCHEDULED))
                .thenReturn(java.util.Arrays.asList(null, null, null)); // 3 uçuş
        when(flightRepository.findByStatus(FlightStatus.BOARDING))
                .thenReturn(java.util.Arrays.asList(null, null));       // 2 uçuş
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);

        // when
        AdminDashboardResponse result = adminDashboardService.getSummary();

        // then
        assertThat(result.activeFlightCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getSummary_countsSavingSuggestions")
    void getSummary_countsSavingSuggestions() {
        // given — 2 enerji tasarrufu önerisi
        SavingSuggestion suggestion1 = new SavingSuggestion(1L, "Zone A", 0.10f, 25.0f, "Işıkları kapat", 15);
        SavingSuggestion suggestion2 = new SavingSuggestion(2L, "Zone B", 0.15f, 30.0f, "AC'yi azalt", 10);

        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of());
        when(energyService.getTotalEnergyKwh()).thenReturn(50f);
        when(energyService.getSavingSuggestions()).thenReturn(List.of(suggestion1, suggestion2));
        when(flightRepository.findByStatus(any())).thenReturn(List.of());
        when(userRepository.count()).thenReturn(30L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);

        // when
        AdminDashboardResponse result = adminDashboardService.getSummary();

        // then
        assertThat(result.savingSuggestionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getSummary_includesUserCountsAndNewUsersToday")
    void getSummary_includesUserCountsAndNewUsersToday() {
        // given
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of());
        when(energyService.getTotalEnergyKwh()).thenReturn(0f);
        when(energyService.getSavingSuggestions()).thenReturn(List.of());
        when(flightRepository.findByStatus(any())).thenReturn(List.of());
        when(userRepository.count()).thenReturn(500L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(7L);

        // when
        AdminDashboardResponse result = adminDashboardService.getSummary();

        // then
        assertThat(result.totalUsers()).isEqualTo(500L);
        assertThat(result.newUsersToday()).isEqualTo(7);
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private ZoneOccupancyResponse buildZoneOccupancy(
            Long zoneId, String name, int count, float density, DensityLevel level) {
        return new ZoneOccupancyResponse(
                zoneId, name, "GATE", 200, 0.85f,
                count, density, level, level.getColorCode(), null);
    }
}
