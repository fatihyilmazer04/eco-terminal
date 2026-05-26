package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ManualNotificationRequest;
import com.ecoterminal.model.dto.NotificationResponse;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifRepository;
    private final UserRepository         userRepository;
    private final UserProfileRepository  profileRepository;
    private final ZoneRepository         zoneRepository;
    private final TicketRepository       ticketRepository;
    private final FcmService             fcmService;
    private final NotificationRateLimiter rateLimiter;

    // ── Kalabalık Uyarısı ──────────────────────────────────────────────────

    /**
     * Belirtilen bölgede aktif bileti olan kullanıcılara kalabalık uyarısı gönderir.
     * Rate limiter: aynı user+zone için 5 dakikada max 1 bildirim.
     */
    @Transactional
    public int triggerCrowdAlert(Long zoneId, Float densityPct) {
        Zone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null) {
            log.warn("triggerCrowdAlert: zoneId={} bulunamadı", zoneId);
            return 0;
        }

        String title = "⚠ " + zone.getZoneName() + " Yoğunluk Uyarısı";
        String body = String.format(
                "Doluluk oranı %%%d'e ulaştı. Alternatif rotalar için uygulamayı açın.",
                Math.round(densityPct * 100));

        // O bölgede aktif bileti olan kullanıcıları bul
        List<Ticket> tickets = ticketRepository.findActiveTicketsByGateZoneId(zoneId);

        int sent = 0;
        for (Ticket ticket : tickets) {
            User user = ticket.getUser();
            Long userId = user.getUserId();

            if (!rateLimiter.canSend(userId, zoneId)) {
                log.debug("Rate limit aşıldı: user={}, zone={} — bildirim atlandı", userId, zoneId);
                continue;
            }

            // DB'ye kaydet
            String fcmToken = profileRepository.findByUserUserId(userId)
                    .map(UserProfile::getFcmToken)
                    .orElse(null);

            Notification notif = Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .type(NotificationType.CROWD_ALERT)
                    .zone(zone)
                    .sentVia(fcmToken != null ? "BOTH" : "IN_APP")
                    .build();
            notifRepository.save(notif);

            // FCM gönder
            if (fcmToken != null) {
                fcmService.sendToToken(fcmToken, title, body);
            }

            sent++;
        }

        // Topic'e de gönder (abone olan herkes)
        String topic = "zone_" + zoneId + "_alerts";
        fcmService.sendToTopic(topic, title, body);

        log.info("triggerCrowdAlert: zone={} density={}% → {} kullanıcıya bildirim gönderildi",
                zoneId, Math.round(densityPct * 100), sent);
        return sent;
    }

    // ── Rota Önerisi ───────────────────────────────────────────────────────

    @Transactional
    public void sendRouteSuggestion(Long userId, Long zoneId, String routeInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));
        Zone zone = zoneId != null ? zoneRepository.findById(zoneId).orElse(null) : null;

        String title = "🗺 Yeni Rota Önerisi";
        Notification notif = Notification.builder()
                .user(user)
                .title(title)
                .body(routeInfo)
                .type(NotificationType.ROUTE_SUGGESTION)
                .zone(zone)
                .sentVia("IN_APP")
                .build();
        notifRepository.save(notif);

        profileRepository.findByUserUserId(userId)
                .map(UserProfile::getFcmToken)
                .ifPresent(token -> fcmService.sendToToken(token, title, routeInfo));

        log.info("Rota önerisi gönderildi: userId={}", userId);
    }

    // ── Uçuş Güncellemesi ──────────────────────────────────────────────────

    @Transactional
    public void sendFlightUpdate(Long userId, Long flightId, String updateText) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        String title = "✈ Uçuş Güncelleme";
        Notification notif = Notification.builder()
                .user(user)
                .title(title)
                .body(updateText)
                .type(NotificationType.FLIGHT_UPDATE)
                .sentVia("IN_APP")
                .build();
        notifRepository.save(notif);

        profileRepository.findByUserUserId(userId)
                .map(UserProfile::getFcmToken)
                .ifPresent(token -> fcmService.sendToToken(token, title, updateText));

        log.info("Uçuş güncellemesi gönderildi: userId={}, flightId={}", userId, flightId);
    }

    // ── Manuel Admin Bildirimi ─────────────────────────────────────────────

    @Transactional
    public NotificationResponse sendManual(ManualNotificationRequest req) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));

        Notification notif = Notification.builder()
                .user(user)
                .title(req.title())
                .body(req.body())
                .type(req.type())
                .sentVia("IN_APP")
                .build();
        notif = notifRepository.save(notif);

        profileRepository.findByUserUserId(req.userId())
                .map(UserProfile::getFcmToken)
                .ifPresent(token -> fcmService.sendToToken(token, req.title(), req.body()));

        log.info("Manuel bildirim gönderildi: userId={}, tür={}", req.userId(), req.type());
        return NotificationResponse.from(notif);
    }

    // ── Okuma ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notifRepository.findByUser_UserIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(0, 50))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return (int) notifRepository.countByUser_UserIdAndIsReadFalse(userId);
    }

    // ── Okundu İşaretle ───────────────────────────────────────────────────

    @Transactional
    public void markAsRead(Long notifId, Long requestingUserId) {
        Notification notif = notifRepository.findById(notifId)
                .orElseThrow(() -> BusinessException.notFound("Bildirim"));

        if (!notif.getUser().getUserId().equals(requestingUserId)) {
            throw new BusinessException("Bu bildirimi okuma yetkiniz yok", HttpStatus.FORBIDDEN);
        }

        notif.setIsRead(true);
        notifRepository.save(notif);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notifRepository.markAllAsReadByUserId(userId);
        log.debug("Tüm bildirimler okundu işaretlendi: userId={}", userId);
    }
}
