package com.ecoterminal.controller;

import com.ecoterminal.model.dto.AIPredictionResponse;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ZoneForecastResponse;
import com.ecoterminal.service.AIPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai/predictions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AIPredictionController {

    private final AIPredictionService predictionService;

    /** GET /api/ai/predictions — tüm son tahminler */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AIPredictionResponse>>> getAllPredictions() {
        return ResponseEntity.ok(ApiResponse.ok(predictionService.getPredictionsForAdmin()));
    }

    /** GET /api/ai/predictions/{zoneId} — bölge tahmini (cache veya live) */
    @GetMapping("/{zoneId}")
    public ResponseEntity<ApiResponse<AIPredictionResponse>> getPredictionForZone(
            @PathVariable Long zoneId) {
        return ResponseEntity.ok(ApiResponse.ok(predictionService.getPredictionForZone(zoneId)));
    }

    /** GET /api/ai/predictions/high-risk — HIGH riskli bölgeler */
    @GetMapping("/high-risk")
    public ResponseEntity<ApiResponse<List<AIPredictionResponse>>> getHighRisk() {
        return ResponseEntity.ok(ApiResponse.ok(predictionService.getHighRiskZones()));
    }

    /** POST /api/ai/predictions/refresh — AI servisi çağırıp DB'yi güncelle */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<List<AIPredictionResponse>>> refresh() {
        log.info("Admin manuel tahmin yenileme isteği");
        List<AIPredictionResponse> updated = predictionService.refreshPredictions();
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /** GET /api/ai/predictions/zone-forecast?zoneId=1 — Çok horizonlu bölge tahmini */
    @GetMapping("/zone-forecast")
    public ResponseEntity<ApiResponse<ZoneForecastResponse>> getZoneForecast(
            @RequestParam Long zoneId) {
        return ResponseEntity.ok(ApiResponse.ok(predictionService.getZoneForecast(zoneId)));
    }
}
