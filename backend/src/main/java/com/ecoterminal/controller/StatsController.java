package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.CameraStatusResponse;
import com.ecoterminal.model.dto.HourlyDataPoint;
import com.ecoterminal.service.StatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AdminDashboard için istatistik endpoint'leri.
 * GET /api/stats/visitors  — son 24 saatlik ziyaretçi istatistiği
 * GET /api/stats/energy    — son 24 saatlik enerji istatistiği
 * GET /api/stats/cameras   — IoT cihaz durumları
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Stats", description = "AdminDashboard istatistik endpoint'leri")
public class StatsController {

    private final StatsService statsService;

    /** Son 24 saatlik saatlik yolcu sayısı */
    @GetMapping("/visitors")
    public ResponseEntity<ApiResponse<List<HourlyDataPoint>>> getVisitorStats() {
        return ResponseEntity.ok(ApiResponse.ok(statsService.get24hVisitorStats()));
    }

    /** Son 24 saatlik saatlik enerji tüketimi (kWh) */
    @GetMapping("/energy")
    public ResponseEntity<ApiResponse<List<HourlyDataPoint>>> getEnergyStats() {
        return ResponseEntity.ok(ApiResponse.ok(statsService.get24hEnergyStats()));
    }

    /** Tüm IoT cihazlarının anlık durumu */
    @GetMapping("/cameras")
    public ResponseEntity<ApiResponse<List<CameraStatusResponse>>> getCameraStatuses() {
        return ResponseEntity.ok(ApiResponse.ok(statsService.getCameraStatuses()));
    }
}
