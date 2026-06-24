package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AdminFlightRequest;
import com.ecoterminal.model.dto.AdminFlightResponse;
import com.ecoterminal.model.dto.AirlineResponse;
import com.ecoterminal.model.dto.FlightDetailResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.AirlineRepository;
import com.ecoterminal.repository.FlightRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneRepository;
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
    private final AirlineRepository airlineRepository;
    private final ZoneRepository zoneRepository;

    // ── USER: kendi biletleri ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FlightDetailResponse> getMyFlights(Long userId) {
        List<Ticket> tickets = ticketRepository.findActiveTicketsWithFlight(userId);
        return tickets.stream()
                .map(t -> toFlightDetailFromFlight(t.getFlight(), t.getSeatNumber(),
                        t.getSeatClass().name(), t.getPnrCode(), t.getTicketId()))
                .toList();
    }

    // ── ADMIN: tüm uçuşlar ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FlightDetailResponse> getAllFlights() {
        return flightRepository.findAll().stream()
                .map(f -> toFlightDetailFromFlight(f, null, null, null, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminFlightResponse> getAllFlightsForAdmin() {
        return flightRepository.findAll().stream()
                .map(this::toAdminFlightResponse)
                .toList();
    }

    @Transactional
    public AdminFlightResponse createFlight(AdminFlightRequest req) {
        Airline airline = airlineRepository.findById(req.airlineId())
                .orElseThrow(() -> BusinessException.notFound("Havayolu"));
        Zone gate = req.gateId() != null
                ? zoneRepository.findById(req.gateId()).orElse(null)
                : null;

        Flight flight = Flight.builder()
                .flightCode(req.flightCode())
                .airline(airline)
                .destination(req.destination())
                .origin(req.origin())
                .departureTime(req.departureTime())
                .arrivalTime(req.arrivalTime())
                .gate(gate)
                .status(req.status() != null ? req.status() : FlightStatus.SCHEDULED)
                .build();

        return toAdminFlightResponse(flightRepository.save(flight));
    }

    @Transactional
    public AdminFlightResponse updateFlight(Long flightId, AdminFlightRequest req) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> BusinessException.notFound("Uçuş"));

        Airline airline = airlineRepository.findById(req.airlineId())
                .orElseThrow(() -> BusinessException.notFound("Havayolu"));
        Zone gate = req.gateId() != null
                ? zoneRepository.findById(req.gateId()).orElse(null)
                : null;

        flight.setFlightCode(req.flightCode());
        flight.setAirline(airline);
        flight.setDestination(req.destination());
        flight.setOrigin(req.origin());
        flight.setDepartureTime(req.departureTime());
        flight.setArrivalTime(req.arrivalTime());
        flight.setGate(gate);
        if (req.status() != null) flight.setStatus(req.status());

        return toAdminFlightResponse(flightRepository.save(flight));
    }

    @Transactional
    public void deleteFlight(Long flightId) {
        if (!flightRepository.existsById(flightId))
            throw BusinessException.notFound("Uçuş");
        flightRepository.deleteById(flightId);
    }

    @Transactional(readOnly = true)
    public List<AirlineResponse> getAllAirlines() {
        return airlineRepository.findAll().stream()
                .map(a -> new AirlineResponse(a.getAirlineId(), a.getIataCode(), a.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getGateZones() {
        return zoneRepository.findAll().stream()
                .filter(z -> ZoneType.GATE.equals(z.getType()))
                .map(z -> new ZoneResponse(z.getZoneId(), z.getZoneName()))
                .toList();
    }

    private AdminFlightResponse toAdminFlightResponse(Flight f) {
        return new AdminFlightResponse(
                f.getFlightId(),
                f.getFlightCode(),
                f.getAirline() != null ? f.getAirline().getAirlineId() : null,
                f.getAirline() != null ? f.getAirline().getName() : "Bilinmiyor",
                f.getAirline() != null ? f.getAirline().getIataCode() : "",
                f.getDestination(),
                f.getOrigin(),
                f.getDepartureTime(),
                f.getArrivalTime(),
                f.getGate() != null ? f.getGate().getZoneId() : null,
                f.getGate() != null ? f.getGate().getZoneName() : null,
                f.getStatus()
        );
    }

    // Simple projection records used internally
    public record ZoneResponse(Long zoneId, String zoneName) {}

    // ── Tek uçuş detayı ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FlightDetailResponse getFlightDetails(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> BusinessException.notFound("Uçuş"));
        return toFlightDetailFromFlight(flight, null, null, null, null);
    }

    // ── Yardımcı: kalkışa kalan dakika ────────────────────────────────────

    public long getMinutesToDeparture(Instant departure) {
        return ChronoUnit.MINUTES.between(Instant.now(), departure);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private FlightDetailResponse toFlightDetailFromFlight(
            Flight flight, String seatNumber, String flightClass, String pnrCode, Long ticketId) {

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
                flight.getStatus(),
                pnrCode,
                ticketId
        );
    }
}
