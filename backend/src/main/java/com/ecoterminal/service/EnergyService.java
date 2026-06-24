package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.EnergyResponse;
import com.ecoterminal.model.dto.EnergySettingRequest;
import com.ecoterminal.model.dto.EnergySettingResponse;
import com.ecoterminal.model.dto.SavingSuggestion;
import com.ecoterminal.model.entity.EnvironmentalMetric;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
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
    private final AuditLogService               auditLogService;

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
        // environmental_metrics kaydı olan zone'ların son metrikleri (zone başına 1 satır)
        Map<Long, EnvironmentalMetric> metricsByZoneId = metricRepository.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        m -> m.getZone().getZoneId(), m -> m,
                        (existing, duplicate) -> existing));

        // Tüm son doluluklar
        Map<Long, Float> densityByZoneId = occupancyRepository.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getZone().getZoneId(),
                        OccupancyReading::getDensityPct,
                        (existing, duplicate) -> existing));

        // Tüm aktif zone'lar üzerinden iterate et.
        // Gerçek metriği olan zone'lar: from(metric, density)
        // Metriği olmayan zone'lar: doluluktan türetilmiş kWh/temp tahmini
        return zoneRepository.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)
                .stream()
                .map(zone -> {
                    EnvironmentalMetric metric = metricsByZoneId.get(zone.getZoneId());
                    float density = densityByZoneId.getOrDefault(zone.getZoneId(), 0f);
                    if (metric != null) {
                        return EnergyResponse.from(metric, density);
                    }
                    return EnergyResponse.fromZone(zone, density, deriveKwh(density), deriveTemp(density));
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
        auditLogService.log(actorId, "ENERGY_SETTING", "environmental_metrics", zoneId, oldVal, newVal);

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

    /**
     * Doluluk oranından türetilmiş enerji sabitleri.
     * environmental_metrics kaydı olmayan zone'lar için fallback hesaplamada kullanılır.
     *
     * kWh  = BASE_KWH  + densityPct × PEAK_FACTOR   (densityPct: 0.0–1.0)
     * temp = BASE_TEMP + densityPct × HEAT_FACTOR
     *
     * Örnek: %30 dolulukt → kWh = 5.0 + 0.30×15.0 = 9.5 kWh, temp = 20.0 + 0.30×8.0 = 22.4°C
     */
    private static final float BASE_KWH    = 5.0f;
    private static final float PEAK_FACTOR = 15.0f;
    private static final float BASE_TEMP   = 20.0f;
    private static final float HEAT_FACTOR = 8.0f;

    /** Doluluktan türetilen kWh tahmini (sensör verisi olmayan zone'lar için). */
    private static float deriveKwh(float densityPct) {
        return BASE_KWH + densityPct * PEAK_FACTOR;
    }

    /** Doluluktan türetilen sıcaklık tahmini (sensör verisi olmayan zone'lar için). */
    private static float deriveTemp(float densityPct) {
        return BASE_TEMP + densityPct * HEAT_FACTOR;
    }

    /** Toplam kWh — AdminDashboardService için */
    @Transactional(readOnly = true)
    public float getTotalEnergyKwh() {
        return (float) metricRepository.findLatestPerZone().stream()
                .mapToDouble(m -> m.getEnergyKwh() != null ? m.getEnergyKwh() : 0f)
                .sum();
    }
}
