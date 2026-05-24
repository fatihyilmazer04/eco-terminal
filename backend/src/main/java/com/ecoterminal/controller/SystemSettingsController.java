package com.ecoterminal.controller;

import com.ecoterminal.model.dto.*;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sistem Ayarları — sadece ADMIN erişebilir.
 * GET  /api/admin/settings/system       → genel sistem istatistikleri
 * GET  /api/admin/settings/zones        → bölge eşik listesi
 * PUT  /api/admin/settings/zones/{id}/threshold → eşik güncelle
 * GET  /api/admin/settings/services     → servis sağlık durumu
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingsController {

    private final SystemSettingsService settingsService;

    /** Sistem geneli istatistikler */
    @GetMapping("/system")
    public ResponseEntity<ApiResponse<SystemStatsResponse>> getSystemStats() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.getSystemStats()));
    }

    /** Tüm aktif bölgeler + eşik değerleri */
    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<ZoneSettingsResponse>>> getZoneSettings() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.getAllZoneSettings()));
    }

    /** Bölge kritik doluluk eşiğini güncelle */
    @PutMapping("/zones/{zoneId}/threshold")
    public ResponseEntity<ApiResponse<ZoneSettingsResponse>> updateThreshold(
            @PathVariable Long zoneId,
            @Valid @RequestBody ZoneThresholdRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        ZoneSettingsResponse updated = settingsService.updateZoneThreshold(
                zoneId, request.criticalThreshold(), principal.getUserId());

        return ResponseEntity.ok(ApiResponse.<ZoneSettingsResponse>ok(updated, "Eşik değeri güncellendi"));
    }

    /** Servis sağlık durumları (Backend, AI, DB) */
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceHealthResponse>>> getServicesHealth() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.checkServicesHealth()));
    }
}
