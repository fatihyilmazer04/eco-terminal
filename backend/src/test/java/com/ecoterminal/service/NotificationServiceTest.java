package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ManualNotificationRequest;
import com.ecoterminal.model.dto.NotificationResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository  notifRepository;
    @Mock private UserRepository          userRepository;
    @Mock private UserProfileRepository   profileRepository;
    @Mock private ZoneRepository          zoneRepository;
    @Mock private TicketRepository        ticketRepository;
    @Mock private FcmService              fcmService;
    @Mock private NotificationRateLimiter rateLimiter;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private Zone testZone;
    private Notification testNotif;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("user@eco.com")
                .role(Role.USER)
                .isActive(true)
                .build();

        testZone = Zone.builder()
                .zoneId(10L)
                .zoneName("Gate A1")
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .build();

        testNotif = Notification.builder()
                .notifId(100L)
                .user(testUser)
                .title("Test Başlık")
                .body("Test mesaj")
                .type(NotificationType.CROWD_ALERT)
                .isRead(false)
                .sentVia("IN_APP")
                .build();
    }

    // ── triggerCrowdAlert Tests ───────────────────────────────────────────

    @Test
    @DisplayName("triggerCrowdAlert_withNonExistentZone_returnsZero")
    void triggerCrowdAlert_withNonExistentZone_returnsZero() {
        // given
        when(zoneRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        int sent = notificationService.triggerCrowdAlert(999L, 0.9f);

        // then
        assertThat(sent).isZero();
        verify(notifRepository, never()).save(any());
    }

    @Test
    @DisplayName("triggerCrowdAlert_withRateLimitedUser_skipsNotification")
    void triggerCrowdAlert_withRateLimitedUser_skipsNotification() {
        // given
        Ticket ticket = Ticket.builder().ticketId(1L).user(testUser).build();
        when(zoneRepository.findById(10L)).thenReturn(Optional.of(testZone));
        when(ticketRepository.findActiveTicketsByGateZoneId(10L)).thenReturn(List.of(ticket));
        when(rateLimiter.canSend(1L, 10L)).thenReturn(false); // rate limited
        when(fcmService.sendToTopic(any(), any(), any())).thenReturn(true);

        // when
        int sent = notificationService.triggerCrowdAlert(10L, 0.9f);

        // then
        assertThat(sent).isZero();
        verify(notifRepository, never()).save(any());
    }

    @Test
    @DisplayName("triggerCrowdAlert_withEligibleUser_sendsNotification")
    void triggerCrowdAlert_withEligibleUser_sendsNotification() {
        // given
        Ticket ticket = Ticket.builder().ticketId(1L).user(testUser).build();
        UserProfile profile = UserProfile.builder().user(testUser).fcmToken("fcm-token").build();

        when(zoneRepository.findById(10L)).thenReturn(Optional.of(testZone));
        when(ticketRepository.findActiveTicketsByGateZoneId(10L)).thenReturn(List.of(ticket));
        when(rateLimiter.canSend(1L, 10L)).thenReturn(true);
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(profile));
        when(notifRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fcmService.sendToToken(any(), any(), any())).thenReturn(true);
        when(fcmService.sendToTopic(any(), any(), any())).thenReturn(true);

        // when
        int sent = notificationService.triggerCrowdAlert(10L, 0.9f);

        // then
        assertThat(sent).isEqualTo(1);
        verify(notifRepository).save(any(Notification.class));
        verify(fcmService).sendToToken(eq("fcm-token"), any(), any());
    }

    // ── sendManual Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("sendManual_withValidRequest_savesAndReturnsResponse")
    void sendManual_withValidRequest_savesAndReturnsResponse() {
        // given
        ManualNotificationRequest req = new ManualNotificationRequest(
                1L, "Duyuru", "Terminal değişikliği", NotificationType.SYSTEM);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(notifRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            // ID atanmış gibi simüle et
            return Notification.builder()
                    .notifId(50L).user(testUser)
                    .title(n.getTitle()).body(n.getBody())
                    .type(n.getType()).sentVia("IN_APP").isRead(false).build();
        });
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.empty());

        // when
        NotificationResponse response = notificationService.sendManual(req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Duyuru");
        verify(notifRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("sendManual_withNonExistentUser_throwsNotFound")
    void sendManual_withNonExistentUser_throwsNotFound() {
        // given
        ManualNotificationRequest req = new ManualNotificationRequest(
                99L, "Başlık", "Mesaj", NotificationType.SYSTEM);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> notificationService.sendManual(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── getMyNotifications Tests ──────────────────────────────────────────

    @Test
    @DisplayName("getMyNotifications_returnsListForUser")
    void getMyNotifications_returnsListForUser() {
        // given
        when(notifRepository.findByUser_UserIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(testNotif));

        // when
        List<NotificationResponse> result = notificationService.getMyNotifications(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Test Başlık");
    }

    @Test
    @DisplayName("getMyNotifications_withNoNotifications_returnsEmptyList")
    void getMyNotifications_withNoNotifications_returnsEmptyList() {
        // given
        when(notifRepository.findByUser_UserIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of());

        // when
        List<NotificationResponse> result = notificationService.getMyNotifications(1L);

        // then
        assertThat(result).isEmpty();
    }

    // ── getUnreadCount Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("getUnreadCount_returnsCorrectCount")
    void getUnreadCount_returnsCorrectCount() {
        // given
        when(notifRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(3L);

        // when
        int count = notificationService.getUnreadCount(1L);

        // then
        assertThat(count).isEqualTo(3);
    }

    // ── markAsRead Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("markAsRead_withOwner_setsReadTrue")
    void markAsRead_withOwner_setsReadTrue() {
        // given
        when(notifRepository.findById(100L)).thenReturn(Optional.of(testNotif));
        when(notifRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        notificationService.markAsRead(100L, 1L);

        // then
        assertThat(testNotif.getIsRead()).isTrue();
        verify(notifRepository).save(testNotif);
    }

    @Test
    @DisplayName("markAsRead_withDifferentUser_throwsForbidden")
    void markAsRead_withDifferentUser_throwsForbidden() {
        // given
        when(notifRepository.findById(100L)).thenReturn(Optional.of(testNotif));

        // when / then — userId=2 ama bildirim userId=1'e ait
        assertThatThrownBy(() -> notificationService.markAsRead(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("markAsRead_withNonExistentNotif_throwsNotFound")
    void markAsRead_withNonExistentNotif_throwsNotFound() {
        // given
        when(notifRepository.findById(999L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── markAllAsRead Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("markAllAsRead_callsRepositoryMethod")
    void markAllAsRead_callsRepositoryMethod() {
        // when
        notificationService.markAllAsRead(1L);

        // then
        verify(notifRepository).markAllAsReadByUserId(1L);
    }

    // ── deleteNotification Tests ──────────────────────────────────────────

    @Test
    @DisplayName("deleteNotification_withOwner_deletesNotification")
    void deleteNotification_withOwner_deletesNotification() {
        // given
        when(notifRepository.findById(100L)).thenReturn(Optional.of(testNotif));

        // when
        notificationService.deleteNotification(100L, 1L);

        // then
        verify(notifRepository).delete(testNotif);
    }

    @Test
    @DisplayName("deleteNotification_withDifferentUser_throwsForbidden")
    void deleteNotification_withDifferentUser_throwsForbidden() {
        // given
        when(notifRepository.findById(100L)).thenReturn(Optional.of(testNotif));

        // when / then
        assertThatThrownBy(() -> notificationService.deleteNotification(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(notifRepository, never()).delete(any());
    }

    // ── deleteAllNotifications Tests ──────────────────────────────────────

    @Test
    @DisplayName("deleteAllNotifications_callsRepositoryBulkDelete")
    void deleteAllNotifications_callsRepositoryBulkDelete() {
        // when
        notificationService.deleteAllNotifications(1L);

        // then
        verify(notifRepository).deleteAllByUserId(1L);
    }
}
