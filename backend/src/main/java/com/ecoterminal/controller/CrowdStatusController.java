package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ZoneCrowdStatusResponse;
import com.ecoterminal.service.CrowdStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anlık kalabalık durum API'si.
 * YOLOv8 ve sensör verilerini birleştirerek her zone için durum özeti sunar.
 */
@Slf4j
@RestController
@RequestMapping("/api/crowd")
@RequiredArgsConstructor
public class CrowdStatusController {

    private final CrowdStatusService crowdStatusService;

    /**
     * GET /api/crowd/status
     * Tüm aktif zone'ların anlık kalabalık durumunu döndürür.
     * Her zone: currentDensity, peopleCount, status (EMPTY/MODERATE/BUSY/FULL),
     *           trend ve predictedLoad (AI tahmininden) içerir.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ZoneCrowdStatusResponse>>> getCrowdStatus() {
        List<ZoneCrowdStatusResponse> statuses = crowdStatusService.getAllZoneStatuses();
        log.debug("GET /api/crowd/status → {} zone", statuses.size());
        return ResponseEntity.ok(ApiResponse.ok(statuses, statuses.size() + " zone durumu"));
    }
}
