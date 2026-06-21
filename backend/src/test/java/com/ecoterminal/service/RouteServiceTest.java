package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.RouteCompletionRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneMapPositionRepository;
import com.ecoterminal.repository.ZoneRepository;
import com.ecoterminal.service.pathfinding.DijkstraService;
import com.ecoterminal.service.pathfinding.GraphService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteService Unit Tests")
class RouteServiceTest {

    @Mock private TicketRepository           ticketRepository;
    @Mock private ZoneRepository             zoneRepository;
    @Mock private OccupancyService           occupancyService;
    @Mock private ZoneMapPositionRepository  zoneMapPositionRepository;
    @Mock private RouteCompletionRepository  completionRepository;
    @Mock private DijkstraService            dijkstraService;
    @Mock private GraphService               graphService;

    @InjectMocks
    private RouteService routeService;

    // ── getSuggestedRoute Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("getSuggestedRoute_withNoActiveTickets_throwsNotFound")
    void getSuggestedRoute_withNoActiveTickets_throwsNotFound() {
        // given — kullanıcının yaklaşan uçuşu yok
        when(ticketRepository.findActiveTicketsWithFlight(1L)).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> routeService.getSuggestedRoute(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getSuggestedRoute_withPastFlightOnly_throwsNotFound")
    void getSuggestedRoute_withPastFlightOnly_throwsNotFound() {
        // given — uçuş zamanı geçmiş
        Flight pastFlight = buildFlight(null, Instant.now().minus(2, ChronoUnit.HOURS));
        Ticket ticket = buildTicket(pastFlight);
        when(ticketRepository.findActiveTicketsWithFlight(1L)).thenReturn(List.of(ticket));

        // when / then
        assertThatThrownBy(() -> routeService.getSuggestedRoute(1L))
                .isInstanceOf(BusinessException.class);
    }

    // ── getQuietAlternatives Tests ────────────────────────────────────────────

    @Test
    @DisplayName("getQuietAlternatives_filtersHighDensityZones")
    void getQuietAlternatives_filtersHighDensityZones() {
        // given — 1 düşük yoğunluklu, 1 yüksek yoğunluklu
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Zone A", "GATE", 0.30f),
                buildZone(2L, "Zone B", "GATE", 0.80f)  // yüksek yoğunluk
        ));

        // when — hedef zone 99 (listede yok, sadece filtre test)
        List<ZoneOccupancyResponse> result = routeService.getQuietAlternatives(99L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).zoneId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getQuietAlternatives_excludesTargetZone")
    void getQuietAlternatives_excludesTargetZone() {
        // given — hedef zone'un kendisi de düşük yoğunluklu
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(5L, "Target Zone", "GATE", 0.20f),
                buildZone(6L, "Other Zone",  "GATE", 0.30f)
        ));

        // when — hedef zone = 5
        List<ZoneOccupancyResponse> result = routeService.getQuietAlternatives(5L);

        // then — zone 5 dahil edilmemeli
        assertThat(result).hasSize(1);
        assertThat(result.get(0).zoneId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("getQuietAlternatives_sortedByDensityAscending")
    void getQuietAlternatives_sortedByDensityAscending() {
        // given — farklı yoğunlukta 3 zone
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Zone A", "GATE", 0.40f),
                buildZone(2L, "Zone B", "GATE", 0.10f),
                buildZone(3L, "Zone C", "GATE", 0.25f)
        ));

        // when
        List<ZoneOccupancyResponse> result = routeService.getQuietAlternatives(99L);

        // then — artan yoğunluk sırası
        assertThat(result).hasSize(3);
        assertThat(result.get(0).zoneId()).isEqualTo(2L); // 0.10
        assertThat(result.get(1).zoneId()).isEqualTo(3L); // 0.25
        assertThat(result.get(2).zoneId()).isEqualTo(1L); // 0.40
    }

    @Test
    @DisplayName("getQuietAlternatives_withAllHighDensity_returnsEmpty")
    void getQuietAlternatives_withAllHighDensity_returnsEmpty() {
        // given — hepsi yoğun
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Zone A", "GATE", 0.60f),
                buildZone(2L, "Zone B", "GATE", 0.90f)
        ));

        // when
        List<ZoneOccupancyResponse> result = routeService.getQuietAlternatives(99L);

        // then
        assertThat(result).isEmpty();
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private ZoneOccupancyResponse buildZone(Long id, String name, String type, float density) {
        DensityLevel level = density >= 0.85f ? DensityLevel.CRITICAL
                : density >= 0.60f ? DensityLevel.HIGH
                : density >= 0.40f ? DensityLevel.MEDIUM : DensityLevel.LOW;
        return new ZoneOccupancyResponse(
                id, name, type, 200, 0.85f,
                (int)(density * 200), density, level,
                level.getColorCode(), null
        );
    }

    private Flight buildFlight(Zone gate, Instant departureTime) {
        return Flight.builder()
                .flightId(10L)
                .flightCode("TK100")
                .destination("İstanbul")
                .gate(gate)
                .departureTime(departureTime)
                .status(FlightStatus.SCHEDULED)
                .build();
    }

    private Ticket buildTicket(Flight flight) {
        return Ticket.builder()
                .ticketId(1L)
                .flight(flight)
                .pnrCode("TK-ABC123")
                .build();
    }
}
