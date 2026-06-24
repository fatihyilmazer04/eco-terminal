package com.ecoterminal.service;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AIPredictionResponse;
import com.ecoterminal.model.dto.EnergyForecastPoint;
import com.ecoterminal.model.dto.ForecastDataPoint;
import com.ecoterminal.model.dto.ZoneForecastResponse;
import com.ecoterminal.model.entity.AIPrediction;
import com.ecoterminal.model.entity.EnvironmentalMetric;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
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
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIPredictionService {

    private final AIPredictionClient          aiClient;
    private final AIPredictionRepository      predRepository;
    private final ZoneRepository              zoneRepository;
    private final EnvironmentalMetricRepository metricRepo;

    // ── Scheduled Refresh ───────────────────────────────────────────────────

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void fetchAndStorePredictions() {
        log.info("AI tahmin yenileme başladı...");
        try {
            List<AIPredictionResponse> predictions = aiClient.getAllPredictions();
            _storePredictions(predictions);
            log.info("AI tahminleri güncellendi: {} bölge", predictions.size());

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

    @Transactional
    public List<AIPredictionResponse> refreshPredictions() {
        List<AIPredictionResponse> predictions = aiClient.getAllPredictions();
        _storePredictions(predictions);
        log.info("Manuel AI tahmin yenileme: {} bölge", predictions.size());
        return predictions;
    }

    /**
     * Görüntü analizi sonrası tek zone için cache'i atlayıp hemen yeni tahmin al.
     * Cache kontrolü yapılmaz — her çağrıda AI servisini gerçek zamanlı çağırır.
     */
    /**
     * Görüntü analizi sonrası tek zone için cache'i atlayıp hemen yeni tahmin al.
     * @return predictedLoad (0.0–1.0) ya da AI servis hatasında null
     */
    @Transactional
    public Double refreshPredictionForZone(Long zoneId) {
        try {
            AIPredictionResponse fresh = aiClient.getPredictionForZone(zoneId);
            _storeOne(fresh);
            log.info("Zone {} için anlık AI tahmini güncellendi: load={} risk={}",
                    zoneId, fresh.predictedLoad(), fresh.riskLevel());
            return fresh.predictedLoad() != null ? fresh.predictedLoad().doubleValue() : null;
        } catch (Exception e) {
            log.warn("Zone {} için anlık AI tahmini güncellenemedi (ana akış etkilenmez): {}",
                    zoneId, e.getMessage());
            return null;
        }
    }

    // ── Okuma ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AIPredictionResponse> getPredictionsForAdmin() {
        return predRepository.findLatestPerZone()
                .stream()
                .map(AIPredictionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AIPredictionResponse> getHighRiskZones() {
        // Her zone'un EN SON tahmini üzerinden HIGH olanları filtrele (geçmiş birikmiş kayıtları sayma)
        return predRepository.findLatestPerZone()
                .stream()
                .filter(p -> "HIGH".equals(p.getRiskLevel()))
                .map(AIPredictionResponse::from)
                .toList();
    }

    @Transactional
    public AIPredictionResponse getPredictionForZone(Long zoneId) {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<AIPrediction> recent = predRepository.findRecentByZoneId(zoneId, fiveMinutesAgo);

        if (!recent.isEmpty()) {
            log.debug("Cache hit: zone {} tahmini DB'den döndürüldü", zoneId);
            return AIPredictionResponse.from(recent.get(0));
        }

        AIPredictionResponse fresh = aiClient.getPredictionForZone(zoneId);
        _storeOne(fresh);
        return fresh;
    }

    // ── Zone Forecast (Yoğunluk — çok horizonlu) ───────────────────────────

    private static final DateTimeFormatter HM_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional(readOnly = true)
    public ZoneForecastResponse getZoneForecast(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        AIPredictionResponse latest = getPredictionForZone(zoneId);
        double baseLoad = latest.predictedLoad() != null ? latest.predictedLoad() : 0.5;
        String trend    = latest.trend()          != null ? latest.trend()         : "STABLE";
        float  conf     = latest.confidence()     != null ? latest.confidence()    : 0.75f;

        List<ForecastDataPoint> shortTerm = buildForecast(baseLoad, trend, conf,
                new int[]{30, 60, 120}, "min");
        List<ForecastDataPoint> longTerm  = buildForecast(baseLoad, trend, conf,
                new int[]{360, 720, 1440}, "hour");

        String confStr = String.format("%.0f%%", conf * 100);
        String rec     = buildForecastRecommendation(baseLoad, trend);

        return new ZoneForecastResponse(
                zoneId, zone.getZoneName(),
                latest.riskLevel(), baseLoad,
                shortTerm, longTerm,
                confStr, rec,
                null   // dataPoints — OCCUPANCY tipinde null
        );
    }

    private List<ForecastDataPoint> buildForecast(double baseLoad, String trend, float baseConf,
                                                    int[] minuteOffsets, String unit) {
        List<ForecastDataPoint> points = new ArrayList<>();
        for (int i = 0; i < minuteOffsets.length; i++) {
            int mins   = minuteOffsets[i];
            double hours = mins / 60.0;
            double drift = "INCREASING".equals(trend) ?  0.04 * hours
                         : "DECREASING".equals(trend) ? -0.04 * hours : 0.0;
            // Math.random() kaldırıldı — deterministik tahmin, her çağrıda aynı değer
            double load = Math.min(1.0, Math.max(0.0, baseLoad + drift));
            double conf = Math.max(0.3, baseConf - 0.05 * i);
            String label = unit.equals("min") ? "+" + mins + " dk" : "+" + (mins / 60) + " sa";
            String risk  = load >= 0.85 ? "HIGH" : load >= 0.60 ? "MEDIUM" : "LOW";
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

    // ── Zone Forecast (Enerji) ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ZoneForecastResponse getEnergyForecast(Long zoneId, String range) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        Instant since = switch (range) {
            case "1W" -> Instant.now().minus(7,  ChronoUnit.DAYS);
            case "1M" -> Instant.now().minus(28, ChronoUnit.DAYS);
            default   -> Instant.now().minus(24, ChronoUnit.HOURS);
        };

        List<EnvironmentalMetric> metrics = metricRepo.findTimeSeriesByZoneId(zoneId, since);

        List<EnergyForecastPoint> dataPoints = metrics.isEmpty()
                ? buildFallbackEnergyPoints(zoneId, range, zone.getMaxCapacity())
                : switch (range) {
                    case "1W" -> buildWeeklyEnergyPoints(metrics);
                    case "1M" -> buildMonthlyEnergyPoints(metrics);
                    default   -> buildHourlyEnergyPoints(metrics);
                };

        return new ZoneForecastResponse(
                zoneId, zone.getZoneName(),
                null, 0.0,
                List.of(), List.of(),
                null, null,
                dataPoints
        );
    }

    private List<EnergyForecastPoint> buildHourlyEnergyPoints(List<EnvironmentalMetric> metrics) {
        Map<Integer, List<EnvironmentalMetric>> byHour = metrics.stream()
                .collect(Collectors.groupingBy(
                        m -> LocalDateTime.ofInstant(m.getRecordedAt(), ZoneOffset.UTC).getHour()));

        double avgKwh = metrics.stream()
                .mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0)
                .average().orElse(10.0);

        List<EnergyForecastPoint> points = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d:00", h);
            List<EnvironmentalMetric> hm = byHour.getOrDefault(h, List.of());
            double kwh = hm.stream().mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0)
                           .average().orElse(0.0);
            double lux = hm.stream().mapToDouble(m -> m.getLightingLux() != null ? m.getLightingLux() : 400)
                           .average().orElse(400.0);
            kwh = Math.round(kwh * 10.0) / 10.0;
            lux = Math.round(lux);
            String status = kwh > avgKwh * 1.2 ? "YÜKSEK" : kwh < avgKwh * 0.8 ? "DÜŞÜK" : "NORMAL";
            points.add(new EnergyForecastPoint(label, kwh, lux, status));
        }
        return points;
    }

    private List<EnergyForecastPoint> buildWeeklyEnergyPoints(List<EnvironmentalMetric> metrics) {
        String[] dayLabels = {"Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"};
        Map<Integer, List<EnvironmentalMetric>> byDay = metrics.stream()
                .collect(Collectors.groupingBy(
                        m -> LocalDateTime.ofInstant(m.getRecordedAt(), ZoneOffset.UTC)
                                .getDayOfWeek().getValue())); // 1=Mon...7=Sun

        double avgKwh = metrics.stream()
                .mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0)
                .average().orElse(10.0);

        List<EnergyForecastPoint> points = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            List<EnvironmentalMetric> dm = byDay.getOrDefault(d, List.of());
            double kwh = dm.stream().mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0)
                           .average().orElse(0.0);
            double lux = dm.stream().mapToDouble(m -> m.getLightingLux() != null ? m.getLightingLux() : 400)
                           .average().orElse(400.0);
            kwh = Math.round(kwh * 10.0) / 10.0;
            lux = Math.round(lux);
            String status = kwh > avgKwh * 1.2 ? "YÜKSEK" : kwh < avgKwh * 0.8 ? "DÜŞÜK" : "NORMAL";
            points.add(new EnergyForecastPoint(dayLabels[d - 1], kwh, lux, status));
        }
        return points;
    }

    private List<EnergyForecastPoint> buildMonthlyEnergyPoints(List<EnvironmentalMetric> metrics) {
        String[] weekLabels = {"1. Hafta", "2. Hafta", "3. Hafta", "4. Hafta"};
        Instant now = Instant.now();

        double avgKwh = metrics.stream()
                .mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0)
                .average().orElse(10.0);

        List<EnergyForecastPoint> points = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            Instant start = now.minus((4 - w) * 7L, ChronoUnit.DAYS);
            Instant end   = now.minus((3 - w) * 7L, ChronoUnit.DAYS);
            List<EnvironmentalMetric> wm = metrics.stream()
                    .filter(m -> !m.getRecordedAt().isBefore(start) && m.getRecordedAt().isBefore(end))
                    .collect(Collectors.toList());
            double kwh = wm.stream().mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0)
                           .average().orElse(0.0);
            double lux = wm.stream().mapToDouble(m -> m.getLightingLux() != null ? m.getLightingLux() : 400)
                           .average().orElse(400.0);
            kwh = Math.round(kwh * 10.0) / 10.0;
            lux = Math.round(lux);
            String status = kwh > avgKwh * 1.2 ? "YÜKSEK" : kwh < avgKwh * 0.8 ? "DÜŞÜK" : "NORMAL";
            points.add(new EnergyForecastPoint(weekLabels[w], kwh, lux, status));
        }
        return points;
    }

    private List<EnergyForecastPoint> buildFallbackEnergyPoints(Long zoneId, String range, Integer capacity) {
        double baseKwh = (capacity == null || capacity <= 100) ? 10.0
                       : (capacity <= 200) ? 20.0 : 30.0;
        double avgKwh  = baseKwh + 2.5;

        List<String> labels = switch (range) {
            case "1W" -> List.of("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz");
            case "1M" -> List.of("1. Hafta", "2. Hafta", "3. Hafta", "4. Hafta");
            default   -> IntStream.range(0, 24)
                                  .mapToObj(h -> String.format("%02d:00", h))
                                  .collect(Collectors.toList());
        };

        List<EnergyForecastPoint> points = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            double seed = (zoneId * 13L + (long) i * 7L) % 100L;
            double kwh  = Math.round((baseKwh + (seed / 100.0) * 5) * 10.0) / 10.0;
            double lux  = 300 + ((zoneId * 5L + (long) i * 11L) % 300L);
            String status = kwh > avgKwh * 1.2 ? "YÜKSEK" : kwh < avgKwh * 0.8 ? "DÜŞÜK" : "NORMAL";
            points.add(new EnergyForecastPoint(labels.get(i), kwh, lux, status));
        }
        return points;
    }

    // ── Yardımcı ───────────────────────────────────────────────────────────

    private void _storePredictions(List<AIPredictionResponse> predictions) {
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
