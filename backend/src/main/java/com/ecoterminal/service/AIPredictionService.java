package com.ecoterminal.service;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.model.dto.AIPredictionResponse;
import com.ecoterminal.model.dto.ForecastDataPoint;
import com.ecoterminal.model.dto.ZoneForecastResponse;
import com.ecoterminal.model.entity.AIPrediction;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIPredictionService {

    private final AIPredictionClient  aiClient;
    private final AIPredictionRepository predRepository;
    private final ZoneRepository          zoneRepository;

    // ── Scheduled Refresh ───────────────────────────────────────────────────

    /**
     * Her 5 dakikada bir AI servisinden tahminleri çeker ve DB'ye kaydeder.
     * HIGH risk varsa uyarı logu yazar (Faz 6'da NotificationService bağlanacak).
     */
    @Scheduled(fixedDelay = 300_000)   // 5 dakika
    @Transactional
    public void fetchAndStorePredictions() {
        log.info("AI tahmin yenileme başladı...");
        try {
            List<AIPredictionResponse> predictions = aiClient.getAllPredictions();
            _storePredictions(predictions);
            log.info("AI tahminleri güncellendi: {} bölge", predictions.size());

            // HIGH risk bölge uyarısı
            predictions.stream()
                    .filter(p -> "HIGH".equals(p.riskLevel()))
                    .forEach(p -> log.warn(
                            "HIGH risk tespit edildi: Zone {} ({}) - tahmin: {}",
                            p.zoneId(), p.zoneName(),
                            String.format("%.3f", p.predictedLoad())));
        } catch (AiServiceException e) {
            log.error("AI servis erişilemez, tahmin güncellenemedi: {}", e.getMessage());
        }
    }

    // ── Manuel Refresh ──────────────────────────────────────────────────────

    /**
     * Admin tarafından manuel tetiklenebilir — POST /api/ai/predictions/refresh
     */
    @Transactional
    public List<AIPredictionResponse> refreshPredictions() {
        List<AIPredictionResponse> predictions = aiClient.getAllPredictions();
        _storePredictions(predictions);
        log.info("Manuel AI tahmin yenileme: {} bölge", predictions.size());
        return predictions;
    }

    // ── Okuma ───────────────────────────────────────────────────────────────

    /** DB'deki son tahminler (her bölge için en yeni bir kayıt) */
    @Transactional(readOnly = true)
    public List<AIPredictionResponse> getPredictionsForAdmin() {
        return predRepository.findLatestPerZone()
                .stream()
                .map(AIPredictionResponse::from)
                .toList();
    }

    /** HIGH riskli bölgelerin tahminleri */
    @Transactional(readOnly = true)
    public List<AIPredictionResponse> getHighRiskZones() {
        return predRepository.findByRiskLevelOrderByGeneratedAtDesc("HIGH")
                .stream()
                .map(AIPredictionResponse::from)
                .toList();
    }

    /**
     * Tek bölge tahmini.
     * Son 5 dakika içinde DB'de kayıt varsa onu döndürür, yoksa AI servisine çağrı yapar.
     */
    @Transactional
    public AIPredictionResponse getPredictionForZone(Long zoneId) {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<AIPrediction> recent = predRepository.findRecentByZoneId(zoneId, fiveMinutesAgo);

        if (!recent.isEmpty()) {
            log.debug("Cache hit: zone {} tahmini DB'den döndürüldü", zoneId);
            return AIPredictionResponse.from(recent.get(0));
        }

        // Cache miss → AI servisi çağır
        AIPredictionResponse fresh = aiClient.getPredictionForZone(zoneId);
        _storeOne(fresh);
        return fresh;
    }

    // ── Zone Forecast (çok horizonlu) ──────────────────────────────────────

    private static final DateTimeFormatter HM_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Belirli bir bölge için kısa + uzun vadeli tahmin üretir.
     * Flask'tan 30/60/120 dk + 6/12/24 saat verileri alınır.
     * Flask erişilemezse DB'den son tahmin kullanılarak trend bazlı projeksiyon yapılır.
     */
    @Transactional(readOnly = true)
    public ZoneForecastResponse getZoneForecast(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> com.ecoterminal.exception.BusinessException.notFound("Bölge"));

        // DB'den son tahmin al
        AIPredictionResponse latest = getPredictionForZone(zoneId);
        double baseLoad = latest.predictedLoad() != null ? latest.predictedLoad() : 0.5;
        String trend    = latest.trend()          != null ? latest.trend()         : "STABLE";
        float  conf     = latest.confidence()     != null ? latest.confidence()    : 0.75f;

        // Kısa vadeli: 30, 60, 120 dk
        List<ForecastDataPoint> shortTerm = buildForecast(baseLoad, trend, conf,
                new int[]{30, 60, 120}, "min");

        // Uzun vadeli: 6, 12, 24 saat
        List<ForecastDataPoint> longTerm = buildForecast(baseLoad, trend, conf,
                new int[]{360, 720, 1440}, "hour");

        String confStr = String.format("%.0f%%", conf * 100);
        String rec     = buildForecastRecommendation(baseLoad, trend);

        return new ZoneForecastResponse(
                zoneId, zone.getZoneName(),
                latest.riskLevel(), baseLoad,
                shortTerm, longTerm,
                confStr, rec
        );
    }

    private List<ForecastDataPoint> buildForecast(double baseLoad, String trend, float baseConf,
                                                    int[] minuteOffsets, String unit) {
        List<ForecastDataPoint> points = new ArrayList<>();
        for (int i = 0; i < minuteOffsets.length; i++) {
            int mins  = minuteOffsets[i];
            // Trend bazlı ilerletme — her saatte ±0.05
            double hours  = mins / 60.0;
            double drift  = "INCREASING".equals(trend) ?  0.04 * hours
                          : "DECREASING".equals(trend) ? -0.04 * hours
                          : 0.0;
            // Uzun vadede giderek daha az kesin
            double uncertainty = 0.015 * i;
            double load = Math.min(1.0, Math.max(0.0, baseLoad + drift + (Math.random() - 0.5) * uncertainty));
            double conf = Math.max(0.3, baseConf - 0.05 * i);

            String label = unit.equals("min")
                    ? "+" + mins + " dk"
                    : "+" + (mins / 60) + " sa";

            String risk = load >= 0.85 ? "HIGH" : load >= 0.60 ? "MEDIUM" : "LOW";
            points.add(new ForecastDataPoint(label, load, risk, conf));
        }
        return points;
    }

    private String buildForecastRecommendation(double load, String trend) {
        if (load >= 0.85 && "INCREASING".equals(trend))
            return "Kritik eşik üstü ve artıyor — acil yönlendirme önerilir";
        if (load >= 0.85)
            return "Kritik doluluk seviyesi — personel desteği sağlayın";
        if (load >= 0.60 && "INCREASING".equals(trend))
            return "Yoğunluk artıyor — 30 dk içinde kritik eşiğe ulaşabilir";
        if (load < 0.20 && "DECREASING".equals(trend))
            return "Bölge boşalıyor — enerji tasarrufu modu etkinleştirilebilir";
        return "Yoğunluk kabul edilebilir seviyede — rutin takip önerilir";
    }

    // ── Yardımcı ───────────────────────────────────────────────────────────

    private void _storePredictions(List<AIPredictionResponse> predictions) {
        // Zone map'i tek sorguda çek
        Map<Long, Zone> zoneMap = zoneRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Zone::getZoneId, z -> z));

        List<AIPrediction> entities = predictions.stream()
                .filter(p -> zoneMap.containsKey(p.zoneId()))
                .map(p -> AIPrediction.builder()
                        .zone(zoneMap.get(p.zoneId()))
                        .forecastTime(p.forecastTime())
                        .predictedLoad(p.predictedLoad())
                        .densityPct(p.densityPct())
                        .riskLevel(p.riskLevel())
                        .trend(p.trend() != null ? p.trend() : "STABLE")
                        .confidence(p.confidence() != null ? p.confidence() : 0.75f)
                        .generatedAt(p.generatedAt() != null ? p.generatedAt() : Instant.now())
                        .build())
                .toList();

        predRepository.saveAll(entities);
    }

    private void _storeOne(AIPredictionResponse p) {
        zoneRepository.findById(p.zoneId()).ifPresent(zone -> {
            AIPrediction entity = AIPrediction.builder()
                    .zone(zone)
                    .forecastTime(p.forecastTime())
                    .predictedLoad(p.predictedLoad())
                    .densityPct(p.densityPct())
                    .riskLevel(p.riskLevel())
                    .trend(p.trend() != null ? p.trend() : "STABLE")
                    .confidence(p.confidence() != null ? p.confidence() : 0.75f)
                    .generatedAt(p.generatedAt() != null ? p.generatedAt() : Instant.now())
                    .build();
            predRepository.save(entity);
        });
    }
}
