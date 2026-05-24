package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.EnergyResponse;
import com.ecoterminal.model.dto.EnergySettingRequest;
import com.ecoterminal.model.dto.EnergySettingResponse;
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

import org.springframework.jdbc.core.JdbcTemplate;

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
    private final JdbcTemplate                  jdbcTemplate;

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
                .collect(Collectors.toMap(
                        m -> m.getZone().getZoneId(), m -> m,
                        (existing, duplicate) -> existing));

        // Tek sorguda tüm son doluluklar
        Map<Long, Float> densityByZoneId = occupancyRepository.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getZone().getZoneId(),
                        OccupancyReading::getDensityPct,
                        (existing, duplicate) -> existing
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

    // ── Bölge Ayar Güncelleme ────────────────────────────────────────────

    /**
     * Bölgenin hedef sıcaklık ve aydınlatma seviyesini günceller.
     * Gerçek uygulamada IoT cihazlarına komut gönderilir; burada DB'ye yeni metrik kaydı eklenir.
     */
    @Transactional
    public EnergySettingResponse updateZoneSettings(Long zoneId, EnergySettingRequest request, Long actorId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        EnvironmentalMetric latest = metricRepository.findTopByZoneOrderByRecordedAtDesc(zone)
                .orElseThrow(() -> BusinessException.notFound("Enerji verisi"));

        Float prevTemp = latest.getTemp();
        Integer prevLux = latest.getLightingLux();

        // Güncel metriği klonlayıp yeni ayarlarla kaydet
        EnvironmentalMetric updated = EnvironmentalMetric.builder()
                .zone(zone)
                .energyKwh(latest.getEnergyKwh())
                .temp(request.targetTemp() != null ? request.targetTemp() : prevTemp)
                .lightingLux(request.targetLightingLux() != null ? request.targetLightingLux() : prevLux)
                .build();
        metricRepository.save(updated);

        // Audit log
        String oldVal = String.format("{\"temp\":%.1f,\"lighting_lux\":%d}", prevTemp != null ? prevTemp : 0f, prevLux != null ? prevLux : 0);
        String newVal = String.format("{\"temp\":%.1f,\"lighting_lux\":%d}",
                updated.getTemp() != null ? updated.getTemp() : 0f,
                updated.getLightingLux() != null ? updated.getLightingLux() : 0);
        jdbcTemplate.update(
                "INSERT INTO audit_logs (actor_id, action_type, target_table, target_id, old_value, new_value) " +
                "VALUES (?, 'ENERGY_SETTING', 'environmental_metrics', ?, ?::jsonb, ?::jsonb)",
                actorId, zoneId, oldVal, newVal);

        log.info("Enerji ayarı güncellendi: zone={}, sıcaklık {}→{}, aydınlatma {}→{}",
                zone.getZoneName(), prevTemp, updated.getTemp(), prevLux, updated.getLightingLux());

        String message = buildSettingMessage(prevTemp, updated.getTemp(), prevLux, updated.getLightingLux());

        return new EnergySettingResponse(
                zoneId, zone.getZoneName(),
                prevTemp, updated.getTemp(),
                prevLux, updated.getLightingLux(),
                message, Instant.now()
        );
    }

    private String buildSettingMessage(Float prevTemp, Float newTemp, Integer prevLux, Integer newLux) {
        List<String> changes = new ArrayList<>();
        if (newTemp != null && prevTemp != null && !newTemp.equals(prevTemp))
            changes.add(String.format("Sıcaklık %.1f°C → %.1f°C", prevTemp, newTemp));
        if (newLux != null && prevLux != null && !newLux.equals(prevLux))
            changes.add(String.format("Aydınlatma %d → %d lux", prevLux, newLux));
        return changes.isEmpty() ? "Değişiklik yok" : String.join(", ", changes) + " olarak güncellendi";
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
