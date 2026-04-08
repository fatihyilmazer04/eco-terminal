package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.EnergyResponse;
import com.ecoterminal.model.dto.EnergyTrendPoint;
import com.ecoterminal.model.dto.SavingSuggestion;
import com.ecoterminal.model.entity.EnvironmentalMetric;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneType;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnergyService {

    private final EnvironmentalMetricRepository metricRepository;
    private final OccupancyReadingRepository    occupancyRepository;
    private final ZoneRepository                zoneRepository;

    // ── Tek Bölge Enerji Durumu ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public EnergyResponse getEnergyByZone(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        EnvironmentalMetric metric = metricRepository
                .findTopByZoneOrderByRecordedAtDesc(zone)
                .orElseThrow(() -> BusinessException.notFound("Enerji verisi"));

        float density = occupancyRepository
                .findTopByZoneOrderByRecordedAtDesc(zone)
                .map(OccupancyReading::getDensityPct)
                .orElse(0f);

        return EnergyResponse.from(metric, density);
    }

    // ── Tüm Bölgeler Enerji Durumu (N+1 yok) ────────────────────────────

    @Transactional(readOnly = true)
    public List<EnergyResponse> getAllZonesEnergy() {
        // Tek sorguda tüm son metrikler
        Map<Long, EnvironmentalMetric> metricsByZoneId = metricRepository.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(m -> m.getZone().getZoneId(), m -> m));

        // Tek sorguda tüm son doluluklar
        Map<Long, Float> densityByZoneId = occupancyRepository.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getZone().getZoneId(),
                        OccupancyReading::getDensityPct
                ));

        return metricsByZoneId.values().stream()
                .map(m -> {
                    float density = densityByZoneId.getOrDefault(m.getZone().getZoneId(), 0f);
                    return EnergyResponse.from(m, density);
                })
                .sorted(Comparator.comparing(EnergyResponse::zoneId))
                .toList();
    }

    // ── Tasarruf Önerileri ───────────────────────────────────────────────

    /**
     * İş kuralı: doluluk < 0.20 VE energy_kwh > 20.0 → WASTEFUL → tasarruf öner.
     * Öneri metni zone tipine göre kişiselleştiriliyor.
     */
    @Transactional(readOnly = true)
    public List<SavingSuggestion> getSavingSuggestions() {
        return getAllZonesEnergy().stream()
                .filter(e -> "WASTEFUL".equals(e.efficiencyStatus()))
                .map(e -> buildSuggestion(e))
                .toList();
    }

    private SavingSuggestion buildSuggestion(EnergyResponse e) {
        String zoneName = e.zoneName();
        ZoneType type = zoneRepository.findById(e.zoneId())
                .map(Zone::getType).orElse(ZoneType.GATE);

        String suggestion = switch (type) {
            case LOUNGE   -> zoneName + " boş, aydınlatmayı %40 azaltın";
            case CHECKIN  -> zoneName + " düşük yoğunlukta, klimayı tasarruf moduna alın";
            case SECURITY -> zoneName + " sakin, yedek şeritler kapatılabilir";
            case GATE     -> zoneName + " doluluk düşük, ışıklandırmayı kısmayı değerlendirin";
            case RETAIL   -> zoneName + " boş, mağaza vitrin aydınlatmasını azaltın";
            case OTHER    -> zoneName + " düşük aktivite, enerji tasarruf modunu etkinleştirin";
        };

        // Tasarruf potansiyeli: boş bölge için kWh'nin %35'i
        int potentialSavingPct = (int) Math.round(
                (1.0 - (e.densityPct() / 0.20)) * 35
        );
        potentialSavingPct = Math.min(Math.max(potentialSavingPct, 20), 45);

        return new SavingSuggestion(
                e.zoneId(), zoneName,
                e.densityPct(), e.energyKwh(),
                suggestion, potentialSavingPct
        );
    }

    // ── Enerji Trendi ────────────────────────────────────────────────────

    /**
     * Son X saatin metriklerini nokta nokta döndürür — grafik için.
     */
    @Transactional(readOnly = true)
    public List<EnergyTrendPoint> getEnergyTrend(Long zoneId, int lastHours) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        Instant start = Instant.now().minus(lastHours, ChronoUnit.HOURS);
        Instant end   = Instant.now();

        return metricRepository
                .findByZoneAndRecordedAtBetweenOrderByRecordedAtAsc(zone, start, end)
                .stream()
                .map(m -> new EnergyTrendPoint(m.getRecordedAt(), m.getEnergyKwh(), m.getTemp()))

                .toList();
    }

    // ── Yardımcı ────────────────────────────────────────────────────────

    /** Toplam kWh — AdminDashboardService için */
    @Transactional(readOnly = true)
    public float getTotalEnergyKwh() {
        return (float) metricRepository.findLatestPerZone().stream()
                .mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0f)
                .sum();
    }
}
