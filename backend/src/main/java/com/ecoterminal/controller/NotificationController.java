package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ManualNotificationRequest;
import com.ecoterminal.model.dto.NotificationResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifService;

    /** GET /api/notifications/my — giriş yapan kullanıcının bildirimleri */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(notifService.getMyNotifications(principal.getUserId())));
    }

    /** GET /api/notifications/unread-count — okunmamış bildirim sayısı */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        int count = notifService.getUnreadCount(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }

    /** PUT /api/notifications/{id}/read — tek bildirimi okundu işaretle */
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        notifService.markAsRead(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** PUT /api/notifications/read-all — tüm bildirimleri okundu işaretle */
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        notifService.markAllAsRead(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** POST /api/notifications/send — admin manuel bildirim gönder */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendManual(
            @RequestBody @Valid ManualNotificationRequest request) {
        log.info("Admin manuel bildirim: userId={}, tür={}", request.userId(), request.type());
        return ResponseEntity.ok(ApiResponse.ok(notifService.sendManual(request)));
    }

    /** DELETE /api/notifications/{id} — tek bildirimi sil */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        notifService.deleteNotification(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** DELETE /api/notifications/clear-all — tüm bildirimleri sil */
    @DeleteMapping("/clear-all")
    public ResponseEntity<ApiResponse<Void>> clearAllNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {
        notifService.deleteAllNotifications(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
