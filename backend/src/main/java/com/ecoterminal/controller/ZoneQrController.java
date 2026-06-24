package com.ecoterminal.controller;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ZoneQrResponse;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.ZoneRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin: Zone QR kod listesi.
 * Sadece ADMIN rolü erişebilir.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/zones")
@RequiredArgsConstructor
@Tag(name = "ZoneQR", description = "Admin: zone QR kod yönetimi")
public class ZoneQrController {

    private final ZoneRepository zoneRepository;

    /**
     * GET /api/admin/zones/qr-codes
     *
     * Tüm aktif zone'ların QR token ve QR içeriklerini döner.
     * Frontend bu veriyle qrcode.react kullanarak QR kod görseli üretir.
     *
     * qrContent formatı (QR'a kodlanacak JSON string):
     *   {"token":"SEC1-A3F2B1","name":"Security-1","zoneId":2}
     */
    @Operation(summary = "Zone QR kodlarını listele", description = "Admin paneli için tüm zone QR token ve içeriklerini döner")
    @GetMapping("/qr-codes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ZoneQrResponse>>> getZoneQrCodes() {

        List<Zone> zones = zoneRepository.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE);

        List<ZoneQrResponse> result = zones.stream()
                .filter(z -> z.getQrToken() != null)
                .map(z -> {
                    String qrContent = buildQrContent(z);
                    return new ZoneQrResponse(
                            z.getZoneId(),
                            z.getZoneName(),
                            z.getType().name(),
                            z.getQrToken(),
                            qrContent
                    );
                })
                .toList();

        log.debug("QR kod listesi döndürüldü: {} zone", result.size());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/admin/zones/list
     * Tüm aktif zone'ları döner (QR token durumundan bağımsız — modal dropdown için).
     */
    @Operation(summary = "Tüm zone listesi", description = "QR eklemek için dropdown listesi")
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAllZones() {
        List<Map<String, Object>> result = zoneRepository.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)
                .stream()
                .map(z -> Map.<String, Object>of(
                        "zoneId",   z.getZoneId(),
                        "zoneName", z.getZoneName(),
                        "zoneType", z.getType().name(),
                        "hasQr",    z.getQrToken() != null
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * POST /api/admin/zones/{zoneId}/qr/generate
     * Zone için rastgele QR token üretir, kaydeder ve döner.
     */
    @Operation(summary = "Zone QR token üret")
    @PostMapping("/{zoneId}/qr/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ZoneQrResponse>> generateQrToken(@PathVariable Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Zone"));

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        zone.setQrToken(token);
        zoneRepository.save(zone);

        String qrContent = buildQrContent(zone);
        ZoneQrResponse response = new ZoneQrResponse(
                zone.getZoneId(), zone.getZoneName(), zone.getType().name(), token, qrContent);
        log.info("Zone {} için QR token üretildi: {}", zoneId, token);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * DELETE /api/admin/zones/{zoneId}/qr
     * Zone QR token'ını temizler.
     */
    @Operation(summary = "Zone QR token sil")
    @DeleteMapping("/{zoneId}/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteQrToken(@PathVariable Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Zone"));

        zone.setQrToken(null);
        zoneRepository.save(zone);

        log.info("Zone {} QR token silindi", zoneId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * QR koda kodlanacak JSON string'i oluşturur.
     * Escape karakterleri olmadan düz string döner — frontend JSON.parse ile parse eder.
     */
    private String buildQrContent(Zone zone) {
        return String.format(
                "{\"token\":\"%s\",\"name\":\"%s\",\"zoneId\":%d}",
                zone.getQrToken(),
                zone.getZoneName(),
                zone.getZoneId()
        );
    }
}
