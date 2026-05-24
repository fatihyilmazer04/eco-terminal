package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.HeatmapResponse;
import com.ecoterminal.model.dto.RedirectRequest;
import com.ecoterminal.model.dto.RedirectResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.dto.ZoneResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.OccupancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Yoğunluk ve bölge endpoint'leri.
 * Tüm endpoint'ler ADMIN ve USER rollerine açıktır.
 * SecurityConfig'de anyRequest().authenticated() kuralı kapsar.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Occupancy", description = "Bölge yoğunluk ve heatmap verileri")
public class OccupancyController {

    private final OccupancyService occupancyService;

    // ── GET /api/zones ─────────────────────────────────────────────────────

    @GetMapping("/api/zones")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> getAllZones() {
        List<ZoneResponse> zones = occupancyService.getAllZones();
        return ResponseEntity.ok(ApiResponse.ok(zones));
    }

    // ── GET /api/zones/{id}/occupancy ──────────────────────────────────────

    @GetMapping("/api/zones/{id}/occupancy")
    public ResponseEntity<ApiResponse<ZoneOccupancyResponse>> getZoneOccupancy(
            @PathVariable Long id) {
        ZoneOccupancyResponse response = occupancyService.getCurrentOccupancy(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── GET /api/occupancy/heatmap ─────────────────────────────────────────

    @GetMapping("/api/occupancy/heatmap")
    public ResponseEntity<ApiResponse<HeatmapResponse>> getHeatmap() {
        HeatmapResponse heatmap = occupancyService.getHeatmapData();
        return ResponseEntity.ok(ApiResponse.ok(heatmap));
    }

    // ── GET /api/occupancy/current ─────────────────────────────────────────

    @GetMapping("/api/occupancy/current")
    public ResponseEntity<ApiResponse<List<ZoneOccupancyResponse>>> getCurrentOccupancy() {
        List<ZoneOccupancyResponse> all = occupancyService.getAllZonesWithOccupancy();
        return ResponseEntity.ok(ApiResponse.ok(all));
    }

    // ── POST /api/occupancy/redirect ─────────────────────────────────────

    @PostMapping("/api/occupancy/redirect")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RedirectResponse>> redirectPassengers(
            @Valid @RequestBody RedirectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        RedirectResponse response = occupancyService.redirectPassengers(request, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
