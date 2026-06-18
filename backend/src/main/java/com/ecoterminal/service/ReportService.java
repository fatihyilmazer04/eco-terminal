package com.ecoterminal.service;

import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.EnvironmentalMetric;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OccupancyReadingRepository    occupancyRepository;
    private final EnvironmentalMetricRepository metricRepository;
    private final JdbcTemplate                  jdbc;

    // ── Mevcut tek-gün saatlik raporlar ──────────────────────────────────────

    /**
     * Belirli bir tarihin saatlik yoğunluk ortalamaları (tüm bölge geneli).
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> getOccupancyReport(LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<OccupancyReading> readings = occupancyRepository.findAllInRange(start, end);
        Map<Integer, List<Float>> byHour = new LinkedHashMap<>();
        for (OccupancyReading r : readings) {
            int hour = r.getRecordedAt().atZone(ZoneOffset.UTC).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(r.getDensityPct());
        }
        return buildHourlyAvgPoints(byHour);
    }

    /**
     * Belirli bir tarihin saatlik enerji tüketimleri (tüm bölge toplamı).
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> getEnergyReport(LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<EnvironmentalMetric> metrics = metricRepository.findAllInRange(start, end);
        Map<Integer, List<Float>> byHour = new LinkedHashMap<>();
        for (EnvironmentalMetric m : metrics) {
            int hour = m.getRecordedAt().atZone(ZoneOffset.UTC).getHour();
            if (m.getEnergyKwh() != null) {
                byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(m.getEnergyKwh());
            }
        }
        // Enerji için saatlik toplam (ortalama değil)
        List<HourlyDataPoint> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Float>> entry : byHour.entrySet()) {
            float total = entry.getValue().stream().reduce(0f, Float::sum);
            result.add(HourlyDataPoint.of(entry.getKey(), total));
        }
        result.sort(Comparator.comparingInt(HourlyDataPoint::hour));
        return result;
    }

    // ── Zaman aralığı saatlik grafik raporu (gün ortalaması) ─────────────────

    /**
     * Tarih aralığının saatlik doluluk ortalamalarını döndürür.
     * Her saat için aralıktaki tüm günlerin ortalaması alınır.
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> getOccupancyReportForRange(LocalDate startDate, LocalDate endDate) {
        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<OccupancyReading> readings = occupancyRepository.findAllInRange(start, end);
        Map<Integer, List<Float>> byHour = new LinkedHashMap<>();
        for (OccupancyReading r : readings) {
            if (r.getDensityPct() == null) continue;
            int hour = r.getRecordedAt().atZone(ZoneOffset.UTC).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(r.getDensityPct());
        }
        return buildHourlyAvgPoints(byHour);
    }

    /**
     * Tarih aralığının saatlik enerji ortalamalarını döndürür.
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> getEnergyReportForRange(LocalDate startDate, LocalDate endDate) {
        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<EnvironmentalMetric> metrics = metricRepository.findAllInRange(start, end);
        Map<Integer, List<Float>> byHour = new LinkedHashMap<>();
        for (EnvironmentalMetric m : metrics) {
            if (m.getEnergyKwh() == null) continue;
            int hour = m.getRecordedAt().atZone(ZoneOffset.UTC).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(m.getEnergyKwh());
        }
        return buildHourlyAvgPoints(byHour);
    }

    // ── Yoğunluk özet raporu ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OccupancySummaryResponse getOccupancySummary(String range) {
        Instant[] primary = primaryPeriod(range);
        Instant pStart = primary[0], pEnd = primary[1];

        List<OccupancyReading> readings = occupancyRepository.findAllInRangeWithZone(pStart, pEnd);

        // Ortalama doluluk
        double avgDensity = readings.stream()
                .filter(r -> r.getDensityPct() != null)
                .mapToDouble(r -> r.getDensityPct().doubleValue())
                .average().orElse(0.0) * 100.0;

        // Karşılaştırma dönemi
        Instant[] comp = comparisonPeriod(pStart, pEnd);
        List<OccupancyReading> prevReadings = occupancyRepository.findAllInRange(comp[0], comp[1]);
        double prevAvgDensity = prevReadings.stream()
                .filter(r -> r.getDensityPct() != null)
                .mapToDouble(r -> r.getDensityPct().doubleValue())
                .average().orElse(0.0) * 100.0;

        // Peak saat
        Map<Integer, DoubleSummaryStatistics> hourStats = readings.stream()
                .filter(r -> r.getDensityPct() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getRecordedAt().atZone(ZoneOffset.UTC).getHour(),
                        Collectors.summarizingDouble(r -> r.getDensityPct().doubleValue())));
        int peakHour = hourStats.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().getAverage()))
                .map(Map.Entry::getKey).orElse(12);
        double peakHourDensity = hourStats.containsKey(peakHour)
                ? hourStats.get(peakHour).getAverage() * 100.0 : 0.0;

        // Top 3 zone
        Map<String, DoubleSummaryStatistics> zoneStats = readings.stream()
                .filter(r -> r.getDensityPct() != null && r.getZone() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getZone().getZoneName(),
                        Collectors.summarizingDouble(r -> r.getDensityPct().doubleValue())));
        List<ZoneStatEntry> topZones = zoneStats.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, DoubleSummaryStatistics> e)
                        -> e.getValue().getAverage()).reversed())
                .limit(3)
                .map(e -> new ZoneStatEntry(e.getKey(),
                        Math.round(e.getValue().getAverage() * 1000.0) / 10.0))
                .collect(Collectors.toList());

        // Kritik okuma sayısı
        long criticalReadings = readings.stream()
                .filter(r -> r.getDensityPct() != null && r.getDensityPct() >= 0.85f)
                .count();

        // İçgörü metni
        String topZoneName  = topZones.isEmpty() ? "bilinmiyor" : topZones.get(0).zoneName();
        double delta        = avgDensity - prevAvgDensity;
        String deltaStr     = prevAvgDensity > 0
                ? (delta >= 0
                    ? String.format("önceki döneme göre +%.1f puan arttı", delta)
                    : String.format("önceki döneme göre %.1f puan azaldı", Math.abs(delta)))
                : "karşılaştırma verisi yetersiz";
        String insightText = String.format(
                "Seçili dönemde terminalde ortalama %%%s doluluk gözlemlendi. " +
                "En yoğun nokta %s oldu (ort. %%%s). Yoğunluk %s.",
                fmt1(avgDensity), topZoneName,
                topZones.isEmpty() ? "—" : fmt1(topZones.get(0).value()),
                deltaStr);

        // ── Ek istatistikler ──────────────────────────────────────────────────

        // Zone bazlı detaylı tablo (JdbcTemplate — min/max/kritik dahil)
        List<OccupancySummaryResponse.ZoneOccupancyDetail> zoneBreakdown = List.of();
        try {
            List<Map<String, Object>> zdRows = jdbc.queryForList(
                    "SELECT z.zone_name, " +
                    "ROUND((AVG(or2.density_pct)*100)::numeric,1) AS avg_pct, " +
                    "ROUND((MAX(or2.density_pct)*100)::numeric,1) AS max_pct, " +
                    "ROUND((MIN(or2.density_pct)*100)::numeric,1) AS min_pct, " +
                    "COUNT(*) FILTER (WHERE or2.density_pct >= 0.85) AS critical_count, " +
                    "COUNT(*) AS total_readings " +
                    "FROM occupancy_readings or2 JOIN zones z ON or2.zone_id = z.zone_id " +
                    "WHERE or2.recorded_at >= ? AND or2.recorded_at < ? " +
                    "AND or2.source IN ('simulator', 'yolov8_live') " +
                    "GROUP BY z.zone_name ORDER BY avg_pct DESC",
                    Timestamp.from(pStart), Timestamp.from(pEnd));
            zoneBreakdown = zdRows.stream()
                    .map(r -> new OccupancySummaryResponse.ZoneOccupancyDetail(
                            String.valueOf(r.get("zone_name")),
                            toDouble(r.get("avg_pct")),
                            toDouble(r.get("max_pct")),
                            toDouble(r.get("min_pct")),
                            toLong(r.get("critical_count")),
                            toLong(r.get("total_readings"))))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("zoneBreakdown (occupancy) sorgusu başarısız: {}", e.getMessage());
        }

        // Gün bazlı trend (seçili dönem)
        List<OccupancySummaryResponse.DailyTrend> dailyTrend = List.of();
        try {
            List<Map<String, Object>> dtRows = jdbc.queryForList(
                    "SELECT TO_CHAR(recorded_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day, " +
                    "ROUND((AVG(density_pct)*100)::numeric,1) AS avg_pct, " +
                    "COUNT(*) FILTER (WHERE density_pct >= 0.85) AS critical_count " +
                    "FROM occupancy_readings WHERE recorded_at >= ? AND recorded_at < ? " +
                    "GROUP BY day ORDER BY day",
                    Timestamp.from(pStart), Timestamp.from(pEnd));
            dailyTrend = dtRows.stream()
                    .map(r -> new OccupancySummaryResponse.DailyTrend(
                            String.valueOf(r.get("day")),
                            toDouble(r.get("avg_pct")),
                            toLong(r.get("critical_count"))))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("dailyTrend sorgusu başarısız: {}", e.getMessage());
        }

        // Saatlik pik analizi (top 5 — mevcut hourStats'tan)
        Map<Integer, Long> critByHour = readings.stream()
                .filter(r -> r.getDensityPct() != null && r.getDensityPct() >= 0.85f)
                .collect(Collectors.groupingBy(
                        r -> r.getRecordedAt().atZone(ZoneOffset.UTC).getHour(),
                        Collectors.counting()));
        List<OccupancySummaryResponse.PeakHourStat> peakHours = hourStats.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<Integer, DoubleSummaryStatistics> e) -> e.getValue().getAverage())
                        .reversed())
                .limit(5)
                .map(e -> new OccupancySummaryResponse.PeakHourStat(
                        e.getKey(),
                        round1(e.getValue().getAverage() * 100),
                        critByHour.getOrDefault(e.getKey(), 0L)))
                .collect(Collectors.toList());

        return new OccupancySummaryResponse(
                round1(avgDensity), round1(prevAvgDensity),
                peakHour, round1(peakHourDensity),
                topZones, criticalReadings, insightText,
                zoneBreakdown, dailyTrend, peakHours);
    }

    // ── Enerji özet raporu ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EnergySummaryResponse getEnergySummary(String range) {
        Instant[] primary = primaryPeriod(range);
        Instant pStart = primary[0], pEnd = primary[1];

        List<EnvironmentalMetric> metrics = metricRepository.findAllInRange(pStart, pEnd);
        boolean hasData = !metrics.isEmpty();

        // Toplam kWh
        double totalKwh = metrics.stream()
                .filter(m -> m.getEnergyKwh() != null)
                .mapToDouble(m -> m.getEnergyKwh().doubleValue())
                .sum();

        // Karşılaştırma dönemi
        Instant[] comp = comparisonPeriod(pStart, pEnd);
        List<EnvironmentalMetric> prevMetrics = metricRepository.findAllInRange(comp[0], comp[1]);
        double prevTotalKwh = prevMetrics.stream()
                .filter(m -> m.getEnergyKwh() != null)
                .mapToDouble(m -> m.getEnergyKwh().doubleValue())
                .sum();

        // En çok tüketen zone
        Map<String, Double> zoneKwh = metrics.stream()
                .filter(m -> m.getEnergyKwh() != null && m.getZone() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getZone().getZoneName(),
                        Collectors.summingDouble(m -> m.getEnergyKwh().doubleValue())));
        String topZoneName = zoneKwh.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        double topZoneKwh = zoneKwh.getOrDefault(topZoneName, 0.0);

        // Ortalama sıcaklık ve lux
        double avgTemp = metrics.stream()
                .filter(m -> m.getTemp() != null)
                .mapToDouble(m -> m.getTemp().doubleValue())
                .average().orElse(0.0);
        double avgLux = metrics.stream()
                .filter(m -> m.getLightingLux() != null)
                .mapToDouble(m -> m.getLightingLux().doubleValue())
                .average().orElse(0.0);

        // Enerji ayarı değişimi (audit_logs'tan)
        long settingChanges = 0L;
        try {
            String sql = "SELECT COUNT(*) FROM audit_logs WHERE action_type = ? " +
                         "AND performed_at >= ? AND performed_at < ?";
            Long cnt = jdbc.queryForObject(sql, Long.class,
                    "ENERGY_SETTING", Timestamp.from(pStart), Timestamp.from(pEnd));
            settingChanges = cnt != null ? cnt : 0L;
        } catch (Exception e) {
            log.warn("audit_logs sorgusu başarısız: {}", e.getMessage());
        }

        // İçgörü metni
        String deltaStr;
        if (prevTotalKwh > 0) {
            double pct = ((totalKwh - prevTotalKwh) / prevTotalKwh) * 100.0;
            deltaStr = pct >= 0
                    ? String.format("önceki döneme göre %%%s artış", fmt1(pct))
                    : String.format("önceki döneme göre %%%s tasarruf", fmt1(Math.abs(pct)));
        } else {
            deltaStr = "karşılaştırma verisi yetersiz";
        }
        String insightText = hasData
                ? String.format("Seçili dönemde toplam %s kWh tüketildi (%s). " +
                                "En yüksek tüketim %s bölgesinde gerçekleşti. " +
                                "Dönem boyunca %d enerji ayarı yapıldı.",
                                fmt1(totalKwh), deltaStr, topZoneName, settingChanges)
                : "Seçili dönem için yeterli enerji verisi bulunamadı.";

        // ── Ek istatistikler ──────────────────────────────────────────────────

        // Son 24 saat toplam kWh (anlık, range'dan bağımsız)
        double last24hKwh = 0.0;
        try {
            Instant h24ago = Instant.now().minus(24, ChronoUnit.HOURS);
            Double v = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(energy_kwh), 0) FROM environmental_metrics WHERE recorded_at >= ?",
                    Double.class, Timestamp.from(h24ago));
            last24hKwh = v != null ? round1(v) : 0.0;
        } catch (Exception e) {
            log.warn("last24h enerji sorgusu başarısız: {}", e.getMessage());
        }

        // Zone bazlı tüketim tam listesi (zoneKwh map'ten)
        List<ZoneStatEntry> zoneBreakdown = zoneKwh.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new ZoneStatEntry(e.getKey(), round1(e.getValue())))
                .collect(Collectors.toList());

        // Saatlik pik analizi — top 3 saat
        Map<Integer, DoubleSummaryStatistics> hourEnergyStats = metrics.stream()
                .filter(m -> m.getEnergyKwh() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getRecordedAt().atZone(ZoneOffset.UTC).getHour(),
                        Collectors.summarizingDouble(m -> m.getEnergyKwh().doubleValue())));
        List<EnergySummaryResponse.PeakHourEntry> topPeakHours = hourEnergyStats.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<Integer, DoubleSummaryStatistics> e) -> e.getValue().getAverage())
                        .reversed())
                .limit(3)
                .map(e -> new EnergySummaryResponse.PeakHourEntry(
                        e.getKey(), round1(e.getValue().getAverage())))
                .collect(Collectors.toList());

        // Tasarruf potansiyeli: yüksek enerji + düşük doluluk eşleşmesi
        List<EnergySummaryResponse.SavingsOpportunity> savingsOpportunities = List.of();
        try {
            List<Map<String, Object>> savRows = jdbc.queryForList(
                    "SELECT z.zone_name, " +
                    "ROUND(AVG(em.energy_kwh)::numeric,2) AS avg_kwh, " +
                    "COALESCE(ROUND((AVG(oc.density_pct)*100)::numeric,1), 0) AS avg_density " +
                    "FROM environmental_metrics em JOIN zones z ON em.zone_id = z.zone_id " +
                    "LEFT JOIN (SELECT zone_id, AVG(density_pct) AS density_pct " +
                    "           FROM occupancy_readings WHERE recorded_at >= ? AND recorded_at < ? " +
                    "           GROUP BY zone_id) oc ON oc.zone_id = z.zone_id " +
                    "WHERE em.recorded_at >= ? AND em.recorded_at < ? " +
                    "GROUP BY z.zone_name " +
                    "HAVING AVG(em.energy_kwh) > 15 AND COALESCE(AVG(oc.density_pct), 1.0) < 0.5 " +
                    "ORDER BY avg_kwh DESC",
                    Timestamp.from(pStart), Timestamp.from(pEnd),
                    Timestamp.from(pStart), Timestamp.from(pEnd));
            savingsOpportunities = savRows.stream()
                    .map(r -> new EnergySummaryResponse.SavingsOpportunity(
                            String.valueOf(r.get("zone_name")),
                            toDouble(r.get("avg_kwh")),
                            toDouble(r.get("avg_density"))))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("savings potansiyeli sorgusu başarısız: {}", e.getMessage());
        }

        return new EnergySummaryResponse(
                round1(totalKwh), round1(prevTotalKwh),
                topZoneName, round1(topZoneKwh),
                round1(avgTemp), round1(avgLux),
                settingChanges, hasData, insightText,
                last24hKwh, zoneBreakdown, topPeakHours, savingsOpportunities, zoneKwh.size());
    }

    // ── AI Özet raporu ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AiSummaryResponse getAiSummary(LocalDate startDate, LocalDate endDate) {
        Instant pStart = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pEnd   = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // 1. Toplam tahmin sayısı
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_predictions WHERE generated_at >= ? AND generated_at < ?",
                Long.class, Timestamp.from(pStart), Timestamp.from(pEnd));
        total = total != null ? total : 0L;
        final long safeTotal = total > 0 ? total : 1L;

        // 2. Risk dağılımı
        List<Map<String, Object>> riskRows = jdbc.queryForList(
                "SELECT risk_level, COUNT(*) AS cnt FROM ai_predictions " +
                "WHERE generated_at >= ? AND generated_at < ? GROUP BY risk_level",
                Timestamp.from(pStart), Timestamp.from(pEnd));
        long high = 0L, medium = 0L, low = 0L;
        for (Map<String, Object> r : riskRows) {
            String level = String.valueOf(r.get("risk_level"));
            long cnt = toLong(r.get("cnt"));
            switch (level) {
                case "HIGH"   -> high   = cnt;
                case "MEDIUM" -> medium = cnt;
                case "LOW"    -> low    = cnt;
            }
        }
        AiSummaryResponse.RiskDistribution riskDist = new AiSummaryResponse.RiskDistribution(
                high, medium, low,
                Math.round(high   * 1000.0 / safeTotal) / 10.0,
                Math.round(medium * 1000.0 / safeTotal) / 10.0,
                Math.round(low    * 1000.0 / safeTotal) / 10.0);

        // 3. Ortalama güven skoru
        Double avgConf = null;
        try {
            avgConf = jdbc.queryForObject(
                    "SELECT AVG(confidence) FROM ai_predictions " +
                    "WHERE generated_at >= ? AND generated_at < ? AND confidence IS NOT NULL",
                    Double.class, Timestamp.from(pStart), Timestamp.from(pEnd));
        } catch (Exception e) {
            log.warn("AI confidence sorgusu başarısız: {}", e.getMessage());
        }
        double avgConfidence = avgConf != null ? Math.round(avgConf * 1000.0) / 10.0 : 0.0;

        // 4. En riskli top 5 zone (HIGH sayısına göre)
        List<Map<String, Object>> zoneRows = jdbc.queryForList(
                "SELECT z.zone_name, COUNT(*) AS high_cnt " +
                "FROM ai_predictions ap JOIN zones z ON ap.zone_id = z.zone_id " +
                "WHERE ap.risk_level = 'HIGH' AND ap.generated_at >= ? AND ap.generated_at < ? " +
                "GROUP BY z.zone_name ORDER BY high_cnt DESC LIMIT 5",
                Timestamp.from(pStart), Timestamp.from(pEnd));
        List<AiSummaryResponse.ZoneRiskEntry> topRiskyZones = zoneRows.stream()
                .map(r -> new AiSummaryResponse.ZoneRiskEntry(
                        String.valueOf(r.get("zone_name")),
                        toLong(r.get("high_cnt"))))
                .collect(Collectors.toList());

        // 5. Günlük HIGH alarm sayısı (dönem içi)
        List<Map<String, Object>> dayRows = jdbc.queryForList(
                "SELECT TO_CHAR(generated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day, COUNT(*) AS cnt " +
                "FROM ai_predictions WHERE risk_level = 'HIGH' " +
                "AND generated_at >= ? AND generated_at < ? " +
                "GROUP BY day ORDER BY day",
                Timestamp.from(pStart), Timestamp.from(pEnd));
        List<AiSummaryResponse.DayCount> predictionsByDay = dayRows.stream()
                .map(r -> new AiSummaryResponse.DayCount(
                        String.valueOf(r.get("day")),
                        toLong(r.get("cnt"))))
                .collect(Collectors.toList());

        // 6. İçgörü cümlesi
        long monitoredZones = topRiskyZones.size();
        try {
            Long zc = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT zone_id) FROM ai_predictions " +
                    "WHERE generated_at >= ? AND generated_at < ?",
                    Long.class, Timestamp.from(pStart), Timestamp.from(pEnd));
            if (zc != null) monitoredZones = zc;
        } catch (Exception e) {
            log.warn("Zone count sorgusu başarısız: {}", e.getMessage());
        }
        String comparisonText = buildAiInsight(total, high, riskDist.highPct(), topRiskyZones, monitoredZones);

        return new AiSummaryResponse(
                total, riskDist, avgConfidence, avgConf != null,
                topRiskyZones, predictionsByDay, comparisonText);
    }

    private String buildAiInsight(long total, long highCount, double highPct,
                                   List<AiSummaryResponse.ZoneRiskEntry> topZones,
                                   long monitoredZones) {
        if (total == 0) {
            return "Seçili dönem için AI tahmin verisi bulunamadı.";
        }
        String topZoneStr = topZones.isEmpty()
                ? "belirsiz"
                : topZones.get(0).zoneName() + " (" + topZones.get(0).highCount() + " kez)";
        return String.format(
                "Seçili dönemde %s tahmin üretildi, bunların %%%s'i yüksek risk seviyesinde. " +
                "En sık alarm veren bölge %s oldu. " +
                "AI sistemi aktif olarak %d bölgeyi izledi.",
                String.format("%,d", total).replace(',', '.'),
                fmt1(highPct),
                topZoneStr,
                monitoredZones);
    }

    // ── Kullanıcı raporu ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserReportResponse getUserReportSummary(LocalDate startDate, LocalDate endDate) {
        Instant pStart = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pEnd   = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Karşılaştırma dönemi (aynı süre kadar geriye)
        long periodSecs = ChronoUnit.SECONDS.between(pStart, pEnd);
        Instant cStart = pStart.minusSeconds(periodSecs);

        // 1. Toplam kullanıcı / rol dağılımı
        Long totalUsers     = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        Long adminCount     = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Long.class);
        Long passengerCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'USER'", Long.class);
        totalUsers     = totalUsers     != null ? totalUsers     : 0L;
        adminCount     = adminCount     != null ? adminCount     : 0L;
        passengerCount = passengerCount != null ? passengerCount : 0L;

        // 2. Email doğrulama oranı (anlık snapshot)
        Long verifiedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email_verified = true", Long.class);
        verifiedCount = verifiedCount != null ? verifiedCount : 0L;
        double emailVerifiedRate = totalUsers > 0 ? (verifiedCount * 100.0 / totalUsers) : 0.0;

        // 3. Dönem içi yeni kayıt sayısı
        Long newInPeriod = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= ? AND created_at < ?",
                Long.class, Timestamp.from(pStart), Timestamp.from(pEnd));
        Long newInPrev = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= ? AND created_at < ?",
                Long.class, Timestamp.from(cStart), Timestamp.from(pStart));
        newInPeriod = newInPeriod != null ? newInPeriod : 0L;
        newInPrev   = newInPrev   != null ? newInPrev   : 0L;

        // 4. Son 6 ay aylık yeni kayıt grafiği (sabit rolling window)
        Instant sixMonthsAgo = YearMonth.now().minusMonths(5)
                .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Map<String, Object>> monthRows = jdbc.queryForList(
                "SELECT TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month, COUNT(*) AS cnt " +
                "FROM users WHERE created_at >= ? GROUP BY month ORDER BY month",
                Timestamp.from(sixMonthsAgo));
        List<UserReportResponse.MonthlyCount> newUsersByMonth = buildMonthlyChart(monthRows);

        // 5. Dönem içi eco puan istatistikleri
        Map<String, Object> loyaltyRow;
        try {
            loyaltyRow = jdbc.queryForMap(
                    "SELECT " +
                    "COALESCE(SUM(CASE WHEN trans_type = 'EARN' THEN amount ELSE 0 END), 0) AS earned, " +
                    "COALESCE(SUM(CASE WHEN trans_type = 'SPEND' THEN amount ELSE 0 END), 0) AS spent, " +
                    "COUNT(CASE WHEN trans_type = 'EARN' THEN 1 ELSE NULL END) AS earn_count, " +
                    "COUNT(CASE WHEN trans_type = 'SPEND' THEN 1 ELSE NULL END) AS spend_count " +
                    "FROM transaction_history WHERE created_at >= ? AND created_at < ?",
                    Timestamp.from(pStart), Timestamp.from(pEnd));
        } catch (Exception e) {
            log.warn("Loyalty stats sorgusu başarısız: {}", e.getMessage());
            loyaltyRow = Map.of("earned", 0L, "spent", 0L, "earn_count", 0L, "spend_count", 0L);
        }
        UserReportResponse.LoyaltyStats loyaltyStats = new UserReportResponse.LoyaltyStats(
                toLong(loyaltyRow.get("earned")),
                toLong(loyaltyRow.get("spent")),
                toLong(loyaltyRow.get("earn_count")),
                toLong(loyaltyRow.get("spend_count")));

        // 6. Top 5 puan kazanan (tüm zamanlar)
        List<Map<String, Object>> earnerRows;
        try {
            earnerRows = jdbc.queryForList(
                    "SELECT CASE WHEN up.full_name IS NOT NULL AND LENGTH(TRIM(up.full_name)) > 0 " +
                    "            THEN up.full_name ELSE u.email END AS display_name, " +
                    "       SUM(th.amount) AS total_pts " +
                    "FROM transaction_history th " +
                    "JOIN eco_wallets ew ON th.wallet_id = ew.wallet_id " +
                    "JOIN users u ON ew.user_id = u.user_id " +
                    "LEFT JOIN user_profiles up ON u.user_id = up.user_id " +
                    "WHERE th.trans_type = 'EARN' " +
                    "GROUP BY u.user_id, u.email, up.full_name " +
                    "ORDER BY total_pts DESC LIMIT 5");
        } catch (Exception e) {
            log.warn("Top earners sorgusu başarısız: {}", e.getMessage());
            earnerRows = List.of();
        }
        List<UserReportResponse.TopEarner> topPointEarners = earnerRows.stream()
                .map(r -> new UserReportResponse.TopEarner(
                        String.valueOf(r.getOrDefault("display_name", "—")),
                        toLong(r.get("total_pts"))))
                .collect(Collectors.toList());

        // 7. İçgörü cümlesi
        String comparisonText = buildUserInsight(
                newInPeriod, newInPrev, emailVerifiedRate, topPointEarners);

        // 8. Aktiflik ve devam oranı
        long activeEarnerCount = 0L;
        try {
            Long ae = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT ew.user_id) FROM eco_wallets ew " +
                    "JOIN transaction_history th ON th.wallet_id = ew.wallet_id " +
                    "WHERE th.trans_type = 'EARN'", Long.class);
            activeEarnerCount = ae != null ? ae : 0L;
        } catch (Exception e) {
            log.warn("activeEarner sorgusu başarısız: {}", e.getMessage());
        }
        double activeEarnerRate = passengerCount > 0
                ? Math.round(activeEarnerCount * 1000.0 / passengerCount) / 10.0 : 0.0;

        long repeatEarnerCount = 0L;
        long totalWallets      = 1L;
        try {
            Long rep = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM (" +
                    "  SELECT wallet_id FROM transaction_history WHERE trans_type = 'EARN' " +
                    "  GROUP BY wallet_id HAVING COUNT(*) > 1) t", Long.class);
            repeatEarnerCount = rep != null ? rep : 0L;
            Long tw = jdbc.queryForObject("SELECT COUNT(*) FROM eco_wallets", Long.class);
            totalWallets = (tw != null && tw > 0) ? tw : 1L;
        } catch (Exception e) {
            log.warn("repeatEarner sorgusu başarısız: {}", e.getMessage());
        }
        double repeatEarnerRate = Math.round(repeatEarnerCount * 1000.0 / totalWallets) / 10.0;

        // 9. Rota tamamlama
        long routeCompletions = 0L;
        try {
            Long rc = jdbc.queryForObject("SELECT COUNT(*) FROM route_completions", Long.class);
            routeCompletions = rc != null ? rc : 0L;
        } catch (Exception e) {
            log.warn("route_completions sorgusu başarısız: {}", e.getMessage());
        }

        return new UserReportResponse(
                totalUsers, adminCount, passengerCount,
                newInPeriod, newInPrev,
                Math.round(emailVerifiedRate * 10.0) / 10.0,
                newUsersByMonth, loyaltyStats, topPointEarners, comparisonText,
                activeEarnerCount, activeEarnerRate,
                repeatEarnerCount, repeatEarnerRate,
                routeCompletions);
    }

    /** Son 6 ay slot'larını sıfır değerle doldurarak döndürür */
    private List<UserReportResponse.MonthlyCount> buildMonthlyChart(List<Map<String, Object>> rows) {
        Map<String, Long> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String m = String.valueOf(r.get("month"));
            byMonth.put(m, toLong(r.get("cnt")));
        }
        List<UserReportResponse.MonthlyCount> result = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            String key = now.minusMonths(i).toString(); // "YYYY-MM"
            result.add(new UserReportResponse.MonthlyCount(key, byMonth.getOrDefault(key, 0L)));
        }
        return result;
    }

    private String buildUserInsight(long newInPeriod, long prevNew,
                                    double emailVerifiedRate,
                                    List<UserReportResponse.TopEarner> topEarners) {
        String deltaStr;
        if (prevNew > 0) {
            double pct = ((newInPeriod - prevNew) * 100.0) / prevNew;
            deltaStr = pct >= 0
                    ? String.format("+%.0f%%", pct)
                    : String.format("%.0f%%", pct);
        } else if (newInPeriod > 0) {
            deltaStr = "yeni";
        } else {
            deltaStr = "değişim yok";
        }

        String topEarnerStr = topEarners.isEmpty()
                ? "henüz belirsiz"
                : topEarners.get(0).displayName() + " (" + topEarners.get(0).totalPoints() + " puan)";

        return String.format(
                "Bu dönemde %d yeni kullanıcı katıldı (önceki döneme göre %s değişim). " +
                "Kullanıcıların %%%s'si email doğrulamasını tamamladı. " +
                "En aktif yolcu %s oldu.",
                newInPeriod, deltaStr,
                fmt1(emailVerifiedRate),
                topEarnerStr);
    }

    // ── AI Doğruluk raporu ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AiAccuracyResponse getAiAccuracy(LocalDate startDate, LocalDate endDate) {
        Instant pStart = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pEnd   = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT COUNT(*) AS matched_pairs, " +
                    "ROUND(AVG(ABS(ap.predicted_load - or2.density_pct))::numeric, 4) AS mae, " +
                    "COALESCE(ROUND(CORR(ap.predicted_load::float8, or2.density_pct::float8)::numeric, 4), 0) AS corr " +
                    "FROM ai_predictions ap " +
                    "JOIN LATERAL ( " +
                    "  SELECT density_pct FROM occupancy_readings oc " +
                    "  WHERE oc.zone_id = ap.zone_id " +
                    "    AND ABS(EXTRACT(EPOCH FROM (oc.recorded_at - ap.forecast_time))) < 300 " +
                    "  ORDER BY ABS(EXTRACT(EPOCH FROM (oc.recorded_at - ap.forecast_time))) LIMIT 1 " +
                    ") or2 ON true " +
                    "WHERE ap.generated_at >= ? AND ap.generated_at < ?",
                    Timestamp.from(pStart), Timestamp.from(pEnd));

            long   matched = toLong(row.get("matched_pairs"));
            double mae     = toDouble(row.get("mae"));
            double corr    = toDouble(row.get("corr"));
            double maePct  = round1(mae * 100.0);

            String text;
            if (matched == 0) {
                text = "Seçili dönemde tahmin–ölçüm eşleşmesi bulunamadı.";
            } else {
                String corrDesc = corr >= 0.7 ? "güçlü pozitif"
                        : corr >= 0.4 ? "orta pozitif"
                        : corr >= 0.0 ? "zayıf pozitif" : "negatif";
                text = String.format(
                        "%d çift eşleştirildi. Model tahminleri gerçek doluluğa göre ortalama " +
                        "%%%s sapma gösterdi, korelasyon %.2f (%s). " +
                        "Fallback model için makul bir doğruluk seviyesi.",
                        matched, fmt1(maePct), corr, corrDesc);
            }
            return new AiAccuracyResponse(
                    Math.round(mae * 10000.0) / 10000.0,
                    maePct, corr, matched, text);
        } catch (Exception e) {
            log.warn("AI accuracy sorgusu başarısız: {}", e.getMessage());
            return new AiAccuracyResponse(0.0, 0.0, 0.0, 0L,
                    "Doğruluk hesabı şu an kullanılamıyor.");
        }
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Double d) return d;
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    /** range → [start, end] Instant çifti */
    private Instant[] primaryPeriod(String range) {
        Instant now = Instant.now();
        return switch (range) {
            case "THIS_MONTH" -> new Instant[]{
                    YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(), now
            };
            case "LAST_MONTH" -> {
                YearMonth lm = YearMonth.now().minusMonths(1);
                yield new Instant[]{
                        lm.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                };
            }
            case "LAST_7"  -> new Instant[]{ now.minus(7,  ChronoUnit.DAYS), now };
            case "LAST_30" -> new Instant[]{ now.minus(30, ChronoUnit.DAYS), now };
            default        -> new Instant[]{ now.minus(30, ChronoUnit.DAYS), now };
        };
    }

    /** Aynı süre kadar geriye gidilmiş karşılaştırma dönemi */
    private Instant[] comparisonPeriod(Instant pStart, Instant pEnd) {
        long secs = ChronoUnit.SECONDS.between(pStart, pEnd);
        return new Instant[]{ pStart.minusSeconds(secs), pStart };
    }

    private List<HourlyDataPoint> buildHourlyAvgPoints(Map<Integer, List<Float>> byHour) {
        List<HourlyDataPoint> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Float>> entry : byHour.entrySet()) {
            float avg = (float) entry.getValue().stream()
                    .mapToDouble(Float::doubleValue).average().orElse(0.0);
            result.add(HourlyDataPoint.of(entry.getKey(), avg));
        }
        result.sort(Comparator.comparingInt(HourlyDataPoint::hour));
        return result;
    }

    private double round1(double v)  { return Math.round(v * 10.0) / 10.0; }
    private String fmt1(double v)    { return String.format("%.1f", v); }
}
