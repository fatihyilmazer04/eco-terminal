package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.EnergyResponse;
import com.ecoterminal.model.dto.EnergySettingRequest;
import com.ecoterminal.model.dto.EnergySettingResponse;
import com.ecoterminal.model.dto.EnergyTrendPoint;
import com.ecoterminal.model.dto.SavingSuggestion;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.EnergyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EnergyController {

    private final EnergyService energyService;

    /**
     * GET /api/energy/usage
     * Tüm bölgelerin anlık enerji + verimlilik durumu.
     */
    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<List<EnergyResponse>>> getAllUsage() {
        return ResponseEntity.ok(ApiResponse.ok(energyService.getAllZonesEnergy()));
    }

    /**
     * GET /api/energy/usage/{zoneId}
     * Tek bölgenin enerji + verimlilik durumu.
     */
    @GetMapping("/usage/{zoneId}")
    public ResponseEntity<ApiResponse<EnergyResponse>> getZoneUsage(@PathVariable Long zoneId) {
        return ResponseEntity.ok(ApiResponse.ok(energyService.getEnergyByZone(zoneId)));
    }

    /**
     * GET /api/energy/savings
     * Tasarruf önerileri (WASTEFUL bölgeler).
     */
    @GetMapping("/savings")
    public ResponseEntity<ApiResponse<List<SavingSuggestion>>> getSavings() {
        return ResponseEntity.ok(ApiResponse.ok(energyService.getSavingSuggestions()));
    }

    /**
     * GET /api/energy/trend/{zoneId}?hours=6
     * Belirli bölgenin son N saatlik enerji trendi.
     */
    @GetMapping("/trend/{zoneId}")
    public ResponseEntity<ApiResponse<List<EnergyTrendPoint>>> getTrend(
            @PathVariable Long zoneId,
            @RequestParam(defaultValue = "6") int hours) {
        if (hours < 1 || hours > 168) hours = 6;
        return ResponseEntity.ok(ApiResponse.ok(energyService.getEnergyTrend(zoneId, hours)));
    }

    /**
     * PATCH /api/energy/zones/{zoneId}/settings
     * Bölgenin hedef sıcaklık ve aydınlatma seviyesini günceller.
     */
    @PatchMapping("/zones/{zoneId}/settings")
    public ResponseEntity<ApiResponse<EnergySettingResponse>> updateSettings(
            @PathVariable Long zoneId,
            @Valid @RequestBody EnergySettingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                energyService.updateZoneSettings(zoneId, request, principal.getUserId())));
    }
}
