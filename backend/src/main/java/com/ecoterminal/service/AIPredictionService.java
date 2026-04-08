package com.ecoterminal.service;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.model.dto.AIPredictionResponse;
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
import java.time.temporal.ChronoUnit;
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
