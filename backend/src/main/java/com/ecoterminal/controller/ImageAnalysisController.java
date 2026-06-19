package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ImageAnalysisRequest;
import com.ecoterminal.model.dto.ImageAnalysisResponse;
import com.ecoterminal.model.dto.ImageAnalysisResponse.Detection;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import com.ecoterminal.service.AIPredictionService;
import com.ecoterminal.service.pathfinding.GraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Görüntü tabanlı doluluk analizi endpoint'i.
 * Frontend'den gelen base64 görüntüyü YOLOv8 servisine iletir,
 * sonucu zenginleştirip döner. Hem ADMIN hem USER erişebilir.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "ImageAnalysis", description = "YOLOv8 görüntü tabanlı doluluk analizi")
public class ImageAnalysisController {

    private final ZoneRepository              zoneRepository;
    private final OccupancyReadingRepository  occupancyReadingRepository;
    private final GraphService                graphService;
    private final AIPredictionService         aiPredictionService;

    @Qualifier("yoloRestTemplate")
    private final RestTemplate yoloRestTemplate;

    @Value("${yolov8-service.base-url:http://yolov8-service:5001}")
    private String yoloServiceUrl;

    // ── POST /api/zones/{zoneId}/analyze-image ─────────────────────────────────

    @Operation(summary = "Görüntüden kişi sayısı tespiti",
               description = "Base64 görüntüyü YOLOv8'e iletir; sonucu kaydedip döner.")
    @PostMapping("/api/zones/{zoneId}/analyze-image")
    public ResponseEntity<ApiResponse<ImageAnalysisResponse>> analyzeImage(
            @PathVariable Long zoneId,
            @Valid @RequestBody ImageAnalysisRequest request) {

        // ── 1. Zone'u doğrula ────────────────────────────────────────────────
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Zone bulunamadı: " + zoneId));

        // ── 2. Base64 prefix'i temizle (data:image/jpeg;base64, vb.) ─────────
        String raw = request.image_base64();
        if (raw.contains(",")) {
            raw = raw.substring(raw.indexOf(',') + 1);
        }

        // ── 3. Temel boyut kontrolü (~10 MB base64 ≈ 13.6 MB) ───────────────
        if (raw.length() > 14_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Görüntü çok büyük. Maksimum 10 MB yükleyin.");
        }

        // ── 4. YOLOv8 servisine ilet ─────────────────────────────────────────
        String detectUrl = yoloServiceUrl + "/detect";
        Map<String, Object> yoloBody = Map.of(
                "zone_id",      zoneId,
                "image_base64", raw
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(yoloBody, headers);

        // Render free plan: soğuk başlatma sırasında 502 döner, servis ~30-60s içinde ayağa kalkar.
        // 3 deneme × 20s bekleme = toplamda 60s tolerans.
        Map<?, ?> yoloResponse = null;
        // Render free plan soğuk başlatma ~60-90s sürebilir.
        // 5 deneme × 25s = 125s toplam tolerans (yoloRestTemplate readTimeout=90s ile uyumlu).
        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<Map> resp = yoloRestTemplate.postForEntity(
                        detectUrl, entity, Map.class);
                yoloResponse = resp.getBody();
                break; // başarılı — döngüden çık
            } catch (HttpServerErrorException ex) {
                // 502/503: servis uyanıyor olabilir
                if (attempt < maxAttempts && (ex.getStatusCode() == HttpStatus.BAD_GATEWAY
                        || ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE)) {
                    log.warn("YOLOv8 servisi soğuk başlatma (deneme {}/{}): {} — 25s bekleniyor...",
                            attempt, maxAttempts, ex.getStatusCode());
                    try { Thread.sleep(25_000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.error("YOLOv8 servisine bağlanılamadı ({}): {}", detectUrl, ex.getMessage());
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Görüntü analiz servisi şu an erişilemiyor. Lütfen tekrar deneyin.");
                }
            } catch (Exception ex) {
                log.error("YOLOv8 servisine bağlanılamadı ({}): {}", detectUrl, ex.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Görüntü analiz servisi şu an erişilemiyor. Lütfen tekrar deneyin.");
            }
        }

        if (yoloResponse == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "YOLOv8 servisi boş yanıt döndü.");
        }

        // ── 5. Yanıtı parse et ───────────────────────────────────────────────
        int peopleCount  = toInt(yoloResponse.get("people_count"), 0);
        double density   = toDouble(yoloResponse.get("density_pct"), 0.0);
        String source    = toString(yoloResponse.get("source"), "yolov8_live");
        String timestamp = toString(yoloResponse.get("timestamp"), "");

        Object detsObj = yoloResponse.get("detections");
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Map> rawDets = (detsObj instanceof List<?> list)
                ? (List<Map>) list
                : Collections.emptyList();

        List<Detection> detections = rawDets.stream()
                .map(d -> {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    List<Number> bbox = (List<Number>) d.getOrDefault("bbox", List.of());
                    double conf       = toDouble(d.get("confidence"), 0.0);
                    List<Double> bboxDoubles = bbox.stream()
                            .map(n -> n == null ? 0.0 : n.doubleValue())
                            .toList();
                    return new Detection(bboxDoubles, conf);
                })
                .toList();

        // ── 6. Occupancy reading kaydet ──────────────────────────────────────
        Double freshPredictedLoad = null;
        try {
            OccupancyReading reading = OccupancyReading.builder()
                    .zone(zone)
                    .peopleCount(peopleCount)
                    .densityPct((float) density)
                    .source(source)
                    .build();
            occupancyReadingRepository.save(reading);
            log.info("Görüntü analizi kaydedildi: zone={} kişi={} doluluk={}",
                    zoneId, peopleCount, density);

            // Dijkstra graph cache'ini anında güncelle (5 dakikalık scheduler'ı beklemeden)
            graphService.refreshDensities();
            log.debug("Dijkstra density cache güncellendi — zone={}", zoneId);

            // AI tahminini anında yenile (lag_1 artık yeni değeri içeriyor)
            freshPredictedLoad = aiPredictionService.refreshPredictionForZone(zoneId);
        } catch (Exception ex) {
            // Kayıt hatası analiz sonucunu engellemez
            log.warn("Occupancy reading kaydedilemedi: {}", ex.getMessage());
        }

        // ── 7. Yanıt oluştur ─────────────────────────────────────────────────
        ImageAnalysisResponse response = new ImageAnalysisResponse(
                zone.getZoneId(),
                zone.getZoneName(),
                zone.getMaxCapacity(),
                peopleCount,
                density,
                ImageAnalysisResponse.toRiskLevel(density),
                source,
                timestamp,
                detections,
                freshPredictedLoad
        );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Yardımcı dönüştürücüler ───────────────────────────────────────────────

    private static int toInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private static double toDouble(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    private static String toString(Object v, String def) {
        return v != null ? v.toString() : def;
    }
}
