package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AdminFlightRequest;
import com.ecoterminal.model.dto.AdminFlightResponse;
import com.ecoterminal.model.dto.FlightDetailResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.AirlineRepository;
import com.ecoterminal.repository.FlightRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlightService Unit Tests")
class FlightServiceTest {

    @Mock private FlightRepository   flightRepository;
    @Mock private TicketRepository   ticketRepository;
    @Mock private OccupancyService   occupancyService;
    @Mock private AirlineRepository  airlineRepository;
    @Mock private ZoneRepository     zoneRepository;

    @InjectMocks
    private FlightService flightService;

    private Airline testAirline;
    private Zone    testGate;
    private Flight  testFlight;

    @BeforeEach
    void setUp() {
        testAirline = Airline.builder()
                .airlineId(1L)
                .iataCode("TK")
                .name("Türk Hava Yolları")
                .build();

        testGate = Zone.builder()
                .zoneId(5L)
                .zoneName("Gate B3")
                .type(ZoneType.GATE)
                .maxCapacity(150)
                .build();

        testFlight = Flight.builder()
                .flightId(1L)
                .flightCode("TK-001")
                .airline(testAirline)
                .destination("İstanbul")
                .origin("Ankara")
                .departureTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .arrivalTime(Instant.now().plus(3, ChronoUnit.HOURS))
                .gate(testGate)
                .status(FlightStatus.SCHEDULED)
                .build();
    }

    // ── getAllFlights Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("getAllFlights_returnsAllFlightsAsDtos")
    void getAllFlights_returnsAllFlightsAsDtos() {
        // given
        when(flightRepository.findAll()).thenReturn(List.of(testFlight));
        when(occupancyService.getCurrentOccupancy(5L))
                .thenThrow(new RuntimeException("no reading")); // gate occupancy fallback

        // when
        List<FlightDetailResponse> result = flightService.getAllFlights();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).flightCode()).isEqualTo("TK-001");
        assertThat(result.get(0).destination()).isEqualTo("İstanbul");
    }

    @Test
    @DisplayName("getAllFlights_withNoFlights_returnsEmptyList")
    void getAllFlights_withNoFlights_returnsEmptyList() {
        // given
        when(flightRepository.findAll()).thenReturn(List.of());

        // when
        List<FlightDetailResponse> result = flightService.getAllFlights();

        // then
        assertThat(result).isEmpty();
    }

    // ── getFlightDetails Tests ────────────────────────────────────────────

    @Test
    @DisplayName("getFlightDetails_withValidId_returnsFlight")
    void getFlightDetails_withValidId_returnsFlight() {
        // given
        when(flightRepository.findById(1L)).thenReturn(Optional.of(testFlight));
        when(occupancyService.getCurrentOccupancy(5L))
                .thenThrow(new RuntimeException("fallback"));

        // when
        FlightDetailResponse result = flightService.getFlightDetails(1L);

        // then
        assertThat(result.flightId()).isEqualTo(1L);
        assertThat(result.flightCode()).isEqualTo("TK-001");
        assertThat(result.airline()).isEqualTo("Türk Hava Yolları");
        assertThat(result.gateZoneName()).isEqualTo("Gate B3");
    }

    @Test
    @DisplayName("getFlightDetails_withNonExistentId_throwsNotFound")
    void getFlightDetails_withNonExistentId_throwsNotFound() {
        // given
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> flightService.getFlightDetails(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── createFlight Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("createFlight_withValidRequest_savesFlight")
    void createFlight_withValidRequest_savesFlight() {
        // given
        AdminFlightRequest req = new AdminFlightRequest(
                "TK-999", 1L, "Berlin", "İstanbul",
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                5L, FlightStatus.SCHEDULED);

        when(airlineRepository.findById(1L)).thenReturn(Optional.of(testAirline));
        when(zoneRepository.findById(5L)).thenReturn(Optional.of(testGate));
        when(flightRepository.save(any(Flight.class))).thenAnswer(inv -> {
            Flight f = inv.getArgument(0);
            f.setFlightId(10L);
            return f;
        });

        // when
        AdminFlightResponse response = flightService.createFlight(req);

        // then
        assertThat(response.flightCode()).isEqualTo("TK-999");
        assertThat(response.airlineName()).isEqualTo("Türk Hava Yolları");
        verify(flightRepository).save(any(Flight.class));
    }

    @Test
    @DisplayName("createFlight_withNonExistentAirline_throwsNotFound")
    void createFlight_withNonExistentAirline_throwsNotFound() {
        // given
        AdminFlightRequest req = new AdminFlightRequest(
                "XX-001", 99L, "Paris", "İstanbul",
                Instant.now(), Instant.now(), null, null);
        when(airlineRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> flightService.createFlight(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── updateFlight Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("updateFlight_withValidRequest_updatesAndSaves")
    void updateFlight_withValidRequest_updatesAndSaves() {
        // given
        AdminFlightRequest req = new AdminFlightRequest(
                "TK-001-UPDATED", 1L, "Londra", "İstanbul",
                Instant.now().plus(3, ChronoUnit.HOURS),
                Instant.now().plus(6, ChronoUnit.HOURS),
                null, FlightStatus.BOARDING);

        when(flightRepository.findById(1L)).thenReturn(Optional.of(testFlight));
        when(airlineRepository.findById(1L)).thenReturn(Optional.of(testAirline));
        when(flightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        AdminFlightResponse response = flightService.updateFlight(1L, req);

        // then
        assertThat(response.flightCode()).isEqualTo("TK-001-UPDATED");
        assertThat(response.destination()).isEqualTo("Londra");
        assertThat(response.status()).isEqualTo(FlightStatus.BOARDING);
        verify(flightRepository).save(testFlight);
    }

    @Test
    @DisplayName("updateFlight_withNonExistentFlight_throwsNotFound")
    void updateFlight_withNonExistentFlight_throwsNotFound() {
        // given
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());
        AdminFlightRequest req = new AdminFlightRequest(
                "XX", 1L, "Y", "Z", Instant.now(), Instant.now(), null, null);

        // when / then
        assertThatThrownBy(() -> flightService.updateFlight(99L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── deleteFlight Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFlight_withValidId_deletesFromRepository")
    void deleteFlight_withValidId_deletesFromRepository() {
        // given
        when(flightRepository.existsById(1L)).thenReturn(true);

        // when
        flightService.deleteFlight(1L);

        // then
        verify(flightRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteFlight_withNonExistentId_throwsNotFound")
    void deleteFlight_withNonExistentId_throwsNotFound() {
        // given
        when(flightRepository.existsById(99L)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> flightService.deleteFlight(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(flightRepository, never()).deleteById(any());
    }

    // ── getMinutesToDeparture Tests ───────────────────────────────────────

    @Test
    @DisplayName("getMinutesToDeparture_withFutureDeparture_returnsPositive")
    void getMinutesToDeparture_withFutureDeparture_returnsPositive() {
        // given
        Instant departure = Instant.now().plus(90, ChronoUnit.MINUTES);

        // when
        long minutes = flightService.getMinutesToDeparture(departure);

        // then — yaklaşık 90 dakika olmalı (1 dakika tolerans)
        assertThat(minutes).isBetween(88L, 91L);
    }

    @Test
    @DisplayName("getMinutesToDeparture_withPastDeparture_returnsNegative")
    void getMinutesToDeparture_withPastDeparture_returnsNegative() {
        // given
        Instant departure = Instant.now().minus(30, ChronoUnit.MINUTES);

        // when
        long minutes = flightService.getMinutesToDeparture(departure);

        // then
        assertThat(minutes).isNegative();
    }

    // ── getAllAirlines Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllAirlines_returnsAirlineList")
    void getAllAirlines_returnsAirlineList() {
        // given
        when(airlineRepository.findAll()).thenReturn(List.of(testAirline));

        // when
        var result = flightService.getAllAirlines();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).iataCode()).isEqualTo("TK");
        assertThat(result.get(0).name()).isEqualTo("Türk Hava Yolları");
    }

    // ── getGateZones Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("getGateZones_returnsOnlyGateTypeZones")
    void getGateZones_returnsOnlyGateTypeZones() {
        // given
        Zone lounge = Zone.builder().zoneId(6L).zoneName("Lounge").type(ZoneType.LOUNGE).build();
        when(zoneRepository.findAll()).thenReturn(List.of(testGate, lounge));

        // when
        var result = flightService.getGateZones();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).zoneName()).isEqualTo("Gate B3");
    }
}
