package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AdminTicketRequest;
import com.ecoterminal.model.dto.TicketDetailResponse;
import com.ecoterminal.model.entity.*;
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
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService Unit Tests")
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private FlightRepository flightRepository;
    @Mock private UserRepository   userRepository;

    @InjectMocks
    private TicketService ticketService;

    private User testUser;
    private Flight testFlight;
    private Ticket testTicket;
    private Airline testAirline;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("user@eco.com")
                .role(Role.USER)
                .isActive(true)
                .build();

        testAirline = Airline.builder()
                .airlineId(1L)
                .iataCode("TK")
                .name("Türk Hava Yolları")
                .build();

        testFlight = Flight.builder()
                .flightId(1L)
                .flightCode("TK-100")
                .airline(testAirline)
                .destination("İstanbul")
                .origin("Ankara")
                .departureTime(Instant.now().plus(3, ChronoUnit.HOURS))
                .status(FlightStatus.SCHEDULED)
                .build();

        testTicket = Ticket.builder()
                .ticketId(1L)
                .flight(testFlight)
                .user(testUser)
                .pnrCode("TK-ABC123")
                .passengerName("Test Yolcu")
                .seatNumber("12A")
                .seatClass(SeatClass.ECONOMY)
                .ticketStatus("ACTIVE")
                .isActive(true)
                .build();
    }

    // ── lookupByPnr Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("lookupByPnr_withValidPnr_returnsTicketDetail")
    void lookupByPnr_withValidPnr_returnsTicketDetail() {
        // given
        when(ticketRepository.findByPnrCodeWithDetails("TK-ABC123"))
                .thenReturn(Optional.of(testTicket));

        // when
        TicketDetailResponse result = ticketService.lookupByPnr("TK-ABC123");

        // then
        assertThat(result.pnrCode()).isEqualTo("TK-ABC123");
        assertThat(result.passengerName()).isEqualTo("Test Yolcu");
        assertThat(result.flightCode()).isEqualTo("TK-100");
    }

    @Test
    @DisplayName("lookupByPnr_withInvalidPnr_throwsNotFound")
    void lookupByPnr_withInvalidPnr_throwsNotFound() {
        // given
        when(ticketRepository.findByPnrCodeWithDetails("INVALID")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> ticketService.lookupByPnr("INVALID"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("lookupByPnr_convertsToUppercase")
    void lookupByPnr_convertsToUppercase() {
        // given
        when(ticketRepository.findByPnrCodeWithDetails("TK-ABC123"))
                .thenReturn(Optional.of(testTicket));

        // when — lowercase gönderiliyor
        ticketService.lookupByPnr("tk-abc123");

        // then — uppercase olarak sorgulanmış olmalı
        verify(ticketRepository).findByPnrCodeWithDetails("TK-ABC123");
    }

    // ── claimTicket Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("claimTicket_withUnclaimedTicket_assignsUserAndReturns")
    void claimTicket_withUnclaimedTicket_assignsUserAndReturns() {
        // given — bilet henüz claim edilmemiş
        testTicket.setUser(null);
        when(ticketRepository.findByPnrCodeWithDetails("TK-ABC123"))
                .thenReturn(Optional.of(testTicket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        TicketDetailResponse result = ticketService.claimTicket("TK-ABC123", 1L);

        // then
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(testTicket.getUser()).isEqualTo(testUser);
        verify(ticketRepository).save(testTicket);
    }

    @Test
    @DisplayName("claimTicket_withAlreadyOwnedByCurrentUser_throwsConflict")
    void claimTicket_withAlreadyOwnedByCurrentUser_throwsConflict() {
        // given — bilet zaten bu kullanıcıya ait
        when(ticketRepository.findByPnrCodeWithDetails("TK-ABC123"))
                .thenReturn(Optional.of(testTicket)); // testTicket.user = testUser(id=1)

        // when / then
        assertThatThrownBy(() -> ticketService.claimTicket("TK-ABC123", 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("claimTicket_withTicketOwnedByOtherUser_throwsConflict")
    void claimTicket_withTicketOwnedByOtherUser_throwsConflict() {
        // given — bilet başka kullanıcıya ait
        User otherUser = User.builder().userId(2L).email("other@eco.com").build();
        testTicket.setUser(otherUser);
        when(ticketRepository.findByPnrCodeWithDetails("TK-ABC123"))
                .thenReturn(Optional.of(testTicket));

        // when / then
        assertThatThrownBy(() -> ticketService.claimTicket("TK-ABC123", 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── unclaimTicket Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("unclaimTicket_byOwner_removesUserFromTicket")
    void unclaimTicket_byOwner_removesUserFromTicket() {
        // given
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        ticketService.unclaimTicket(1L, 1L);

        // then
        assertThat(testTicket.getUser()).isNull();
        verify(ticketRepository).save(testTicket);
    }

    @Test
    @DisplayName("unclaimTicket_byNonOwner_throwsForbidden")
    void unclaimTicket_byNonOwner_throwsForbidden() {
        // given
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));

        // when / then — userId=2 bilet sahibi değil
        assertThatThrownBy(() -> ticketService.unclaimTicket(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("unclaimTicket_withNullUserOnTicket_throwsForbidden")
    void unclaimTicket_withNullUserOnTicket_throwsForbidden() {
        // given — bilet zaten claim edilmemiş
        testTicket.setUser(null);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));

        // when / then
        assertThatThrownBy(() -> ticketService.unclaimTicket(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── createTicket Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("createTicket_withValidRequest_generatesUniquePnr")
    void createTicket_withValidRequest_generatesUniquePnr() {
        // given
        AdminTicketRequest req = new AdminTicketRequest(
                1L, "14B", SeatClass.BUSINESS, "Test Yolcu");
        when(flightRepository.findById(1L)).thenReturn(Optional.of(testFlight));
        when(ticketRepository.findByPnrCode(anyString())).thenReturn(Optional.empty()); // PNR benzersiz
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setTicketId(99L);
            return t;
        });

        // when
        TicketDetailResponse result = ticketService.createTicket(req);

        // then
        assertThat(result.pnrCode()).startsWith("TK-"); // TK iataCode prefix
        assertThat(result.pnrCode()).hasSize(9);         // TK-XXXXXX
        assertThat(result.seatClass()).isEqualTo(SeatClass.BUSINESS);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("createTicket_withNonExistentFlight_throwsNotFound")
    void createTicket_withNonExistentFlight_throwsNotFound() {
        // given
        AdminTicketRequest req = new AdminTicketRequest(99L, "1A", SeatClass.ECONOMY, "Ad");
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> ticketService.createTicket(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── deleteTicket Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("deleteTicket_withValidId_deletesTicket")
    void deleteTicket_withValidId_deletesTicket() {
        // given
        when(ticketRepository.existsById(1L)).thenReturn(true);

        // when
        ticketService.deleteTicket(1L);

        // then
        verify(ticketRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteTicket_withNonExistentId_throwsNotFound")
    void deleteTicket_withNonExistentId_throwsNotFound() {
        // given
        when(ticketRepository.existsById(99L)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> ticketService.deleteTicket(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(ticketRepository, never()).deleteById(any());
    }
}
