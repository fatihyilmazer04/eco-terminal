package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.FlightDetailResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.FlightRepository;
import com.ecoterminal.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final TicketRepository ticketRepository;
    private final OccupancyService occupancyService;

    // ── USER: kendi biletleri ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FlightDetailResponse> getMyFlights(Long userId) {
        List<Ticket> tickets = ticketRepository.findActiveTicketsWithFlight(userId);
        return tickets.stream()
                .map(this::toFlightDetail)
                .toList();
    }

    // ── ADMIN: tüm uçuşlar ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FlightDetailResponse> getAllFlights() {
        return flightRepository.findAll().stream()
                .map(f -> toFlightDetailFromFlight(f, null, null))
                .toList();
    }

    // ── Tek uçuş detayı ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FlightDetailResponse getFlightDetails(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> BusinessException.notFound("Uçuş"));
        return toFlightDetailFromFlight(flight, null, null);
    }

    // ── Yardımcı: kalkışa kalan dakika ────────────────────────────────────

    public long getMinutesToDeparture(Instant departure) {
        return ChronoUnit.MINUTES.between(Instant.now(), departure);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private FlightDetailResponse toFlightDetail(Ticket ticket) {
        Flight flight   = ticket.getFlight();
        String seatNum  = ticket.getSeatNumber();
        String cls      = ticket.getSeatClass().name();
        return toFlightDetailFromFlight(flight, seatNum, cls);
    }

    private FlightDetailResponse toFlightDetailFromFlight(
            Flight flight, String seatNumber, String flightClass) {

        // Kapı doluluk bilgisini OccupancyService'ten al
        Float gateDensityPct       = null;
        DensityLevel gateDensityLv = DensityLevel.LOW;
        String colorCode           = DensityLevel.LOW.getColorCode();

        if (flight.getGate() != null) {
            try {
                ZoneOccupancyResponse occ =
                        occupancyService.getCurrentOccupancy(flight.getGate().getZoneId());
                gateDensityPct = occ.densityPct();
                gateDensityLv  = occ.densityLevel();
                colorCode      = occ.colorCode();
            } catch (Exception e) {
                log.warn("Gate occupancy fetch failed for zone {}: {}",
                        flight.getGate().getZoneId(), e.getMessage());
            }
        }

        long minutesToDep = getMinutesToDeparture(flight.getDepartureTime());

        return new FlightDetailResponse(
                flight.getFlightId(),
                flight.getFlightCode(),
                flight.getAirline() != null ? flight.getAirline().getName() : "Bilinmiyor",
                flight.getAirline() != null ? flight.getAirline().getIataCode() : "",
                flight.getDestination(),
                flight.getOrigin(),
                flight.getDepartureTime(),
                flight.getGate() != null ? flight.getGate().getZoneName() : "Belirsiz",
                minutesToDep,
                gateDensityPct,
                gateDensityLv,
                colorCode,
                seatNumber,
                flightClass,
                flight.getStatus()
        );
    }
}
