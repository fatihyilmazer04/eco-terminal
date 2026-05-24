package com.ecoterminal.service;

import com.ecoterminal.model.dto.CameraStatusResponse;
import com.ecoterminal.model.dto.HourlyDataPoint;
import com.ecoterminal.model.entity.DeviceStatus;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.IoTDeviceRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AdminDashboard için istatistik servisi — 24s ziyaretçi, 24s enerji, kamera durumu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final OccupancyReadingRepository    occupancyRepo;
    private final EnvironmentalMetricRepository metricRepo;
    private final IoTDeviceRepository           deviceRepo;

    /**
     * Son 24 saatin saatlik ortalama yolcu sayısı.
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> get24hVisitorStats() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant until = Instant.now();

        var readings = occupancyRepo.findAllInRange(since, until);

        // Saat bazında grupla → kişi sayısı ortalaması
        Map<Integer, IntSummaryStatistics> byHour = readings.stream()
                .collect(Collectors.groupingBy(
                        r -> LocalDateTime.ofInstant(r.getRecordedAt(), ZoneOffset.UTC).getHour(),
                        Collectors.summarizingInt(r -> r.getPeopleCount() != null ? r.getPeopleCount() : 0)
                ));

        // 0–23 tüm saatleri doldur
        return buildHourlyPoints(byHour.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getAverage()
                )));
    }

    /**
     * Son 24 saatin saatlik toplam kWh tüketimi.
     */
    @Transactional(readOnly = true)
    public List<HourlyDataPoint> get24hEnergyStats() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant until = Instant.now();

        var metrics = metricRepo.findAllInRange(since, until);

        Map<Integer, DoubleSummaryStatistics> byHour = metrics.stream()
                .collect(Collectors.groupingBy(
                        m -> LocalDateTime.ofInstant(m.getRecordedAt(), ZoneOffset.UTC).getHour(),
                        Collectors.summarizingDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0.0)
                ));

        return buildHourlyPoints(byHour.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getSum()
                )));
    }

    /**
     * Tüm cihazların (kamera/sensör) anlık durumu.
     */
    @Transactional(readOnly = true)
    public List<CameraStatusResponse> getCameraStatuses() {
        return deviceRepo.findAll().stream()
                .map(d -> new CameraStatusResponse(
                        d.getDeviceId(),
                        d.getSerialNumber(),
                        d.getZone() != null ? d.getZone().getZoneName() : "—",
                        d.getDeviceType() != null ? d.getDeviceType().name() : "UNKNOWN",
                        d.getStatus() != null ? d.getStatus().name() : "UNKNOWN",
                        d.getFirmwareVer()
                ))
                .sorted(Comparator.comparing(CameraStatusResponse::status).reversed())
                .toList();
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private List<HourlyDataPoint> buildHourlyPoints(Map<Integer, Double> byHour) {
        List<HourlyDataPoint> result = new ArrayList<>();
        int currentHour = LocalDateTime.now(ZoneOffset.UTC).getHour();
        for (int h = 0; h < 24; h++) {
            int hour = (currentHour - 23 + h + 24) % 24;
            float value = byHour.getOrDefault(hour, 0.0).floatValue();
            result.add(HourlyDataPoint.of(hour, value));
        }
        return result;
    }
}
