package com.ecoterminal.controller;

import com.ecoterminal.model.dto.AdminDashboardResponse;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.HourlyDataPoint;
import com.ecoterminal.service.AdminDashboardService;
import com.ecoterminal.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin dashboard ve raporlama endpoint'leri (ADMIN rolü gerekli)")
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final ReportService         reportService;

    /**
     * GET /api/admin/dashboard
     * Sistem geneli özet: bölge yoğunlukları, enerji, aktif uçuşlar, tasarruf.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getSummary()));
    }

    /**
     * GET /api/admin/reports/occupancy?date=2024-01-15
     * Belirli günün saatlik yoğunluk ortalamaları.
     */
    @GetMapping("/reports/occupancy")
    public ResponseEntity<ApiResponse<List<HourlyDataPoint>>> getOccupancyReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getOccupancyReport(date)));
    }

    /**
     * GET /api/admin/reports/energy?date=2024-01-15
     * Belirli günün saatlik enerji tüketimi.
     */
    @GetMapping("/reports/energy")
    public ResponseEntity<ApiResponse<List<HourlyDataPoint>>> getEnergyReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getEnergyReport(date)));
    }
}
