package com.ecoterminal.service;

import com.ecoterminal.model.dto.HourlyDataPoint;
import com.ecoterminal.model.entity.EnvironmentalMetric;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OccupancyReadingRepository    occupancyRepository;
    private final EnvironmentalMetricRepository metricRepository;

    /**
     * Belirli bir tarihin saatlik yoğunluk ortalamaları (tüm bölge geneli).
     * Dönüş: 0-23 arası saat → avg densityPct
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> getOccupancyReport(LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<OccupancyReading> readings = occupancyRepository
                .findAllInRange(start, end);

        // Saat bazında gruplama ve ortalama hesaplama
        Map<Integer, List<Float>> byHour = new LinkedHashMap<>();
        for (OccupancyReading r : readings) {
            int hour = r.getRecordedAt().atZone(ZoneOffset.UTC).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(r.getDensityPct());
        }

        return buildHourlyPoints(byHour);
    }

    /**
     * Belirli bir tarihin saatlik enerji tüketimleri (tüm bölge toplamı).
     * Dönüş: 0-23 arası saat → toplam kWh
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> getEnergyReport(LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<EnvironmentalMetric> metrics = metricRepository.findAllInRange(start, end);

        // Saat bazında gruplama ve toplam hesaplama
        Map<Integer, List<Float>> byHour = new LinkedHashMap<>();
        for (EnvironmentalMetric m : metrics) {
            int hour = m.getRecordedAt().atZone(ZoneOffset.UTC).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(m.getEnergyKwh());
        }

        // Enerji için toplam (ortalama değil) kullanıyoruz
        List<HourlyDataPoint> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Float>> entry : byHour.entrySet()) {
            float total = entry.getValue().stream().reduce(0f, Float::sum);
            result.add(HourlyDataPoint.of(entry.getKey(), total));
        }
        result.sort(Comparator.comparingInt(HourlyDataPoint::hour));
        return result;
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────

    private List<HourlyDataPoint> buildHourlyPoints(Map<Integer, List<Float>> byHour) {
        List<HourlyDataPoint> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Float>> entry : byHour.entrySet()) {
            float avg = (float) entry.getValue().stream()
                    .mapToDouble(Float::doubleValue).average().orElse(0.0);
            result.add(HourlyDataPoint.of(entry.getKey(), avg));
        }
        result.sort(Comparator.comparingInt(HourlyDataPoint::hour));
        return result;
    }
}
