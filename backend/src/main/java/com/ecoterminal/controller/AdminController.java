package com.ecoterminal.controller;

import com.ecoterminal.model.dto.AdminDashboardResponse;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.EnergySummaryResponse;
import com.ecoterminal.model.dto.HourlyDataPoint;
import com.ecoterminal.model.dto.OccupancySummaryResponse;
import com.ecoterminal.model.dto.ReportContent;
import com.ecoterminal.model.dto.SystemHealthResponse;
import com.ecoterminal.model.dto.UpdateUserRequest;
import com.ecoterminal.model.dto.UserListResponse;
import com.ecoterminal.model.dto.AiAccuracyResponse;
import com.ecoterminal.model.dto.AiSummaryResponse;
import com.ecoterminal.model.dto.UserReportResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.AdminDashboardService;
import com.ecoterminal.service.ReportNarrativeService;
import com.ecoterminal.service.ReportService;
import com.ecoterminal.service.UserManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin dashboard ve raporlama endpoint'leri (ADMIN rolü gerekli)")
public class AdminController {

    private final AdminDashboardService  adminDashboardService;
    private final ReportService          reportService;
    private final ReportNarrativeService reportNarrativeService;
    private final UserManagementService  userManagementService;
    private final HealthEndpoint         healthEndpoint;

    @Value("${ai-service.base-url:http://localhost:5000}")
    private String aiServiceUrl;

    @Value("${yolov8-service.base-url:http://yolov8-service:5001}")
    private String yolov8ServiceUrl;

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

    /**
     * GET /api/admin/reports/occupancy/range?startDate=...&endDate=...
     * Tarih aralığının saatlik yoğunluk ortalamaları (grafik için).
     */
    @GetMapping("/reports/occupancy/range")
    public ResponseEntity<ApiResponse<List<HourlyDataPoint>>> getOccupancyReportRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getOccupancyReportForRange(startDate, endDate)));
    }

    /**
     * GET /api/admin/reports/energy/range?startDate=...&endDate=...
     * Tarih aralığının saatlik enerji ortalamaları (grafik için).
     */
    @GetMapping("/reports/energy/range")
    public ResponseEntity<ApiResponse<List<HourlyDataPoint>>> getEnergyReportRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getEnergyReportForRange(startDate, endDate)));
    }

    /**
     * GET /api/admin/reports/occupancy/summary?range=LAST_30
     * Dönem yoğunluk özeti: ortalama, peak saat, top zone'lar, içgörü.
     */
    @GetMapping("/reports/occupancy/summary")
    public ResponseEntity<ApiResponse<OccupancySummaryResponse>> getOccupancySummary(
            @RequestParam(defaultValue = "LAST_30") String range) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getOccupancySummary(range)));
    }

    /**
     * GET /api/admin/reports/energy/summary?range=LAST_30
     * Dönem enerji özeti: toplam kWh, top zone, ort. sıcaklık/lux, içgörü.
     */
    @GetMapping("/reports/energy/summary")
    public ResponseEntity<ApiResponse<EnergySummaryResponse>> getEnergySummary(
            @RequestParam(defaultValue = "LAST_30") String range) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getEnergySummary(range)));
    }

    /**
     * GET /api/admin/reports/ai-accuracy?startDate=...&endDate=...
     * AI model doğruluğu: predicted_load vs gerçek density_pct karşılaştırması.
     */
    @GetMapping("/reports/ai-accuracy")
    public ResponseEntity<ApiResponse<AiAccuracyResponse>> getAiAccuracy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getAiAccuracy(startDate, endDate)));
    }

    /**
     * GET /api/admin/reports/ai-summary?startDate=...&endDate=...
     * AI tahmin özeti: risk dağılımı, top problemli zone'lar, günlük HIGH alarm.
     */
    @GetMapping("/reports/ai-summary")
    public ResponseEntity<ApiResponse<AiSummaryResponse>> getAiSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getAiSummary(startDate, endDate)));
    }

    /**
     * GET /api/admin/reports/users/summary?startDate=...&endDate=...
     * Kullanıcı raporu özeti: kayıt sayıları, email doğrulama, eco puan istatistikleri.
     */
    @GetMapping("/reports/users/summary")
    public ResponseEntity<ApiResponse<UserReportResponse>> getUserReportSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getUserReportSummary(startDate, endDate)));
    }

    /**
     * GET /api/admin/reports/generate?type=USER_REGISTRATION&startDate=...&endDate=...
     * Akıllı şablon tabanlı yazılı analiz raporu üretir.
     * type: USER_REGISTRATION | USER_ECO_POINTS | USER_ACTIVITY |
     *       ENERGY_CONSUMPTION | ENERGY_SAVINGS | ENERGY_HOURLY |
     *       OCCUPANCY_GENERAL | OCCUPANCY_ZONE_DETAIL | OCCUPANCY_PEAK_HOURS |
     *       AI_ACCURACY | AI_RISK_DISTRIBUTION
     */
    @GetMapping("/reports/generate")
    public ResponseEntity<ApiResponse<ReportContent>> generateReport(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(
                reportNarrativeService.generateReport(type, startDate, endDate)));
    }

    /**
     * GET /api/admin/system/health
     * Backend, DB, Redis, AI servisi ve YOLOv8 sağlık durumunu toplar.
     * Herhangi bir dış servis DOWN/timeout olsa bile 200 döner; o alan "DOWN" gösterilir.
     */
    @GetMapping("/system/health")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> getSystemHealth() {
        String dbStatus    = componentHealth("db");
        String redisStatus = componentHealth("redis");
        String aiStatus    = httpHealthCheck(aiServiceUrl + "/health");
        String yoloStatus  = httpHealthCheck(yolov8ServiceUrl + "/health");

        return ResponseEntity.ok(ApiResponse.ok(
                new SystemHealthResponse("UP", dbStatus, redisStatus, aiStatus, yoloStatus)));
    }

    /** Spring Actuator health component'inden "UP"/"DOWN" döner. */
    private String componentHealth(String componentName) {
        try {
            HealthComponent hc = healthEndpoint.healthForPath(componentName);
            return hc != null ? hc.getStatus().getCode() : "UNKNOWN";
        } catch (Exception e) {
            log.warn("Health check failed for component '{}': {}", componentName, e.getMessage());
            return "DOWN";
        }
    }

    /** HTTP GET yaparak 200 alınırsa "UP", hata/timeout ise "DOWN" döner. */
    private String httpHealthCheck(String url) {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2_500);
            factory.setReadTimeout(2_500);
            new RestTemplate(factory).getForObject(url, String.class);
            return "UP";
        } catch (Exception e) {
            log.debug("HTTP health check DOWN for {}: {}", url, e.getMessage());
            return "DOWN";
        }
    }

    // ── Kullanıcı Yönetimi ─────────────────────────────────────────────────────

    /** GET /api/admin/users — tüm kullanıcıları listele */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserListResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getAllUsers()));
    }

    /** PATCH /api/admin/users/{id} — rol ve durum güncelle */
    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserListResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                userManagementService.updateUser(id, request, principal.getUserId())));
    }
}
