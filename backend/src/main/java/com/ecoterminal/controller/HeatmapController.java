package com.ecoterminal.controller;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.HeatmapSummaryResponse;
import com.ecoterminal.model.dto.OccupancyTimeSeriesPoint;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.AIPredictionClient;
import com.ecoterminal.service.CrowdMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Terminal heatmap API'si.
 * - GET /api/heatmap/live       → Anlık tüm zone durumu + koordinatlar
 * - GET /api/heatmap/history    → Zone doluluk geçmişi (grafik verisi)
 * - POST /api/heatmap/refresh   → AI tahminini yenile + audit log (ADMIN)
 */
@Slf4j
@RestController
@RequestMapping("/api/heatmap")
@RequiredArgsConstructor
public class HeatmapController {

    private final CrowdMonitorService crowdMonitorService;
    private final AIPredictionClient  aiPredictionClient;
    private final JdbcTemplate        jdbcTemplate;

    /**
     * GET /api/heatmap/live
     * Terminal heatmap verisi: zone durumu + SVG koordinatları + AI tahminleri + özet.
     */
    @GetMapping("/live")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<HeatmapSummaryResponse>> getHeatmapLive() {
        HeatmapSummaryResponse data = crowdMonitorService.getHeatmapData();
        log.debug("GET /api/heatmap/live → {} zone, {} dolu", data.totalZones(), data.fullCount());
        return ResponseEntity.ok(ApiResponse.ok(data, "Heatmap verisi hazır"));
    }

    /**
     * GET /api/heatmap/history?zone_id={id}&hours=24
     * Zone'un son X saatlik doluluk grafiği (zaman serisi).
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<OccupancyTimeSeriesPoint>>> getHistory(
            @RequestParam("zone_id") Long zoneId,
            @RequestParam(value = "hours", defaultValue = "24") int hours) {

        if (hours < 1 || hours > 168) hours = 24; // max 1 hafta
        List<OccupancyTimeSeriesPoint> series = crowdMonitorService.getHistory(zoneId, hours);
        log.debug("GET /api/heatmap/history zone={} hours={} → {} nokta", zoneId, hours, series.size());
        return ResponseEntity.ok(ApiResponse.ok(series, series.size() + " veri noktası"));
    }

    /**
     * POST /api/heatmap/refresh  (ADMIN only)
     * AI tahminleri yenilenir, audit_logs tablosuna yazılır, güncel heatmap döndürülür.
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HeatmapSummaryResponse>> refreshHeatmap(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Heatmap refresh tetiklendi — kullanıcı: {}", principal.getEmail());

        try {
            aiPredictionClient.getAllPredictions();
        } catch (AiServiceException e) {
            log.warn("AI yenileme başarısız, eski veri kullanılıyor: {}", e.getMessage());
        }

        // Audit log yaz
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (actor_id, action_type, target_table, performed_at) VALUES (?, ?, ?, NOW())",
                    principal.getUserId(), "HEATMAP_REFRESH", "ai_predictions"
            );
        } catch (Exception e) {
            log.warn("Audit log yazılamadı: {}", e.getMessage());
        }

        HeatmapSummaryResponse data = crowdMonitorService.getHeatmapData();
        return ResponseEntity.ok(ApiResponse.ok(data, "Heatmap yenilendi"));
    }
}
