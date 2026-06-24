package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import org.springframework.http.HttpStatus;
import com.ecoterminal.model.dto.AdminTicketRequest;
import com.ecoterminal.model.dto.PnrClaimRequest;
import com.ecoterminal.model.dto.TicketDetailResponse;
import com.ecoterminal.model.entity.Flight;
import com.ecoterminal.model.entity.Ticket;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.repository.FlightRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final FlightRepository flightRepository;
    private final UserRepository userRepository;

    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous chars

    // ── Admin: bilet listesi ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TicketDetailResponse> getAllTickets() {
        return ticketRepository.findAllWithDetails().stream()
                .map(this::toDetail)
                .toList();
    }

    // ── Admin: bilet oluştur ──────────────────────────────────────────────

    @Transactional
    public TicketDetailResponse createTicket(AdminTicketRequest req) {
        Flight flight = flightRepository.findById(req.flightId())
                .orElseThrow(() -> BusinessException.notFound("Uçuş"));

        String pnr = generateUniquePnr(flight.getAirline() != null
                ? flight.getAirline().getIataCode() : "EC");

        Ticket ticket = Ticket.builder()
                .flight(flight)
                .seatNumber(req.seatNumber())
                .seatClass(req.seatClass())
                .passengerName(req.passengerName())
                .pnrCode(pnr)
                .ticketStatus("ACTIVE")
                .isActive(true)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        log.info("Admin created ticket PNR={} for flight {}", pnr, flight.getFlightCode());
        return toDetail(saved);
    }

    // ── User: biletini kaldır (user_id = NULL yap) ────────────────────────

    @Transactional
    public void unclaimTicket(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Bilet"));

        if (ticket.getUser() == null || !ticket.getUser().getUserId().equals(userId)) {
            throw new BusinessException("Bu bilet size ait değil.", HttpStatus.FORBIDDEN);
        }

        ticket.setUser(null);
        ticketRepository.save(ticket);
        log.info("User {} unclaimed ticket id={} pnr={}", userId, ticketId, ticket.getPnrCode());
    }

    // ── Admin: bilet sil ──────────────────────────────────────────────────

    @Transactional
    public void deleteTicket(Long ticketId) {
        if (!ticketRepository.existsById(ticketId))
            throw BusinessException.notFound("Bilet");
        ticketRepository.deleteById(ticketId);
    }

    // ── User: PNR sorgula (preview, claim etmez) ─────────────────────────

    @Transactional(readOnly = true)
    public TicketDetailResponse lookupByPnr(String pnrCode) {
        Ticket ticket = ticketRepository.findByPnrCodeWithDetails(pnrCode.toUpperCase())
                .orElseThrow(() -> new BusinessException("PNR bulunamadı: " + pnrCode, HttpStatus.NOT_FOUND));
        return toDetail(ticket);
    }

    // ── User: PNR claim (bileti hesabına ekle) ────────────────────────────

    @Transactional
    public TicketDetailResponse claimTicket(String pnrCode, Long userId) {
        Ticket ticket = ticketRepository.findByPnrCodeWithDetails(pnrCode.toUpperCase())
                .orElseThrow(() -> new BusinessException("PNR bulunamadı: " + pnrCode, HttpStatus.NOT_FOUND));

        if (ticket.getUser() != null) {
            if (ticket.getUser().getUserId().equals(userId)) {
                throw new BusinessException("Bu bilet zaten hesabınızda kayıtlı.", HttpStatus.CONFLICT);
            }
            throw new BusinessException("Bu bilet başka bir kullanıcıya aittir.", HttpStatus.CONFLICT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        ticket.setUser(user);
        Ticket saved = ticketRepository.save(ticket);
        log.info("User {} claimed ticket PNR={}", userId, pnrCode);
        return toDetail(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String generateUniquePnr(String iataCode) {
        String prefix = (iataCode != null && iataCode.length() >= 2)
                ? iataCode.substring(0, 2).toUpperCase()
                : "EC";
        for (int attempt = 0; attempt < 20; attempt++) {
            String suffix = randomSuffix(6);
            String pnr = prefix + "-" + suffix;
            if (ticketRepository.findByPnrCode(pnr).isEmpty()) {
                return pnr;
            }
        }
        throw new BusinessException("PNR üretimi başarısız oldu. Lütfen tekrar deneyin.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private TicketDetailResponse toDetail(Ticket t) {
        Flight f = t.getFlight();
        return new TicketDetailResponse(
                t.getTicketId(),
                t.getPnrCode(),
                t.getPassengerName(),
                t.getTicketStatus(),
                t.getSeatNumber(),
                t.getSeatClass(),
                t.getUser() != null ? t.getUser().getUserId() : null,
                t.getUser() != null ? t.getUser().getEmail() : null,
                f != null ? f.getFlightId() : null,
                f != null ? f.getFlightCode() : null,
                f != null && f.getAirline() != null ? f.getAirline().getName() : null,
                f != null && f.getAirline() != null ? f.getAirline().getIataCode() : null,
                f != null ? f.getDestination() : null,
                f != null ? f.getOrigin() : null,
                f != null ? f.getDepartureTime() : null,
                f != null && f.getGate() != null ? f.getGate().getZoneName() : null,
                t.getBookedAt()
        );
    }
}
