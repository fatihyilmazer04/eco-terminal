package com.ecoterminal.service;

import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.ZoneRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demo amaçlı doluluk simülatörü.
 * Her 5 dakikada bir tüm aktif bölgeler için sahte okuma üretir.
 * Belirli bölgeler (ALWAYS_BUSY_ZONES) her zaman yüksek yoğunlukta tutulur.
 * Bu sayede admin panelinde "Yönlendir" butonu ve kritik uyarılar test edilebilir.
 *
 * app.demo.fixed-heatmap=true olduğunda hem @PostConstruct hem @Scheduled devre dışı kalır;
 * doluluk değerleri DemoOccupancyProvider tarafından sabit olarak sunulur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccupancySimulatorService {

    private final ZoneRepository              zoneRepo;
    private final OccupancyReadingRepository  occupancyRepo;
    private final Random                      rng = new Random();

    @Value("${app.demo.fixed-heatmap:true}")
    private boolean demoMode;

    /**
     * Sabit yüksek dolulukta tutulacak bölgeler ve hedef density aralıkları.
     * key=zone adının başlangıcı, value=float[]{minDensity, maxDensity}
     */
    private static final Map<String, float[]> ZONE_DENSITY_OVERRIDES = Map.of(
        "Lounge-1",   new float[]{0.88f, 0.96f},   // FULL — her zaman kritik
        "Gate A1",    new float[]{0.82f, 0.92f},   // HIGH/FULL
        "Gate A2",    new float[]{0.72f, 0.83f},   // HIGH
        "Security-1", new float[]{0.64f, 0.76f}    // MEDIUM-HIGH
    );

    /** Genel bölgeler için varsayılan density aralığı */
    private static final float DEFAULT_MIN = 0.18f;
    private static final float DEFAULT_MAX = 0.50f;

    /**
     * Manuel görüntü analizi akışı kullanılıyor, otomatik simülasyon kapalı.
     * Başlangıç simülasyonu devre dışı bırakıldı: occupancy_readings tablosunu
     * sahte verilerle kirletmemek için @PostConstruct kaldırıldı.
     * Gerçek veriler yalnızca /api/zones/{id}/analyze-image üzerinden gelir.
     */
    // @PostConstruct  — otomatik simülasyon kapalı
    public void initOnStartup() {
        log.info("OccupancySimulatorService: simülasyon devre dışı (manuel görüntü analizi modu)");
    }

    /**
     * Manuel görüntü analizi akışı kullanılıyor, otomatik simülasyon kapalı.
     * 5 dakikada bir sahte okuma üretimi durduruldu: her zone için yalnızca
     * gerçek YOLOv8 görüntü analizinden (source='yolov8_live') gelen değerler
     * occupancy_readings tablosuna yazılacak.
     */
    // @Scheduled(fixedDelay = 300_000)  — otomatik simülasyon kapalı
    @Transactional
    public void scheduledGeneration() {
        // Manuel görüntü analizi akışı kullanılıyor, otomatik simülasyon kapalı.
        log.debug("OccupancySimulatorService.scheduledGeneration devre dışı — çağrılmamalıydı");
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private void generateReadings() {
        List<Zone> activeZones = zoneRepo.findByStatus(ZoneStatus.ACTIVE);
        if (activeZones.isEmpty()) {
            log.debug("OccupancySimulator: aktif zone bulunamadı, atlandı");
            return;
        }

        for (Zone zone : activeZones) {
            float density = computeDensity(zone.getZoneName());
            int capacity  = zone.getMaxCapacity() != null ? zone.getMaxCapacity() : 100;
            int people    = Math.round(density * capacity);
            people        = Math.max(0, Math.min(people, capacity));

            OccupancyReading reading = OccupancyReading.builder()
                    .zone(zone)
                    .peopleCount(people)
                    .densityPct(density)
                    .source("simulator")
                    .build();
            occupancyRepo.save(reading);
        }

        log.debug("OccupancySimulator: {} bölge için okuma üretildi", activeZones.size());
    }

    /** Bölge adına göre density belirle — override varsa onu kullan, yoksa varsayılan */
    private float computeDensity(String zoneName) {
        for (Map.Entry<String, float[]> entry : ZONE_DENSITY_OVERRIDES.entrySet()) {
            if (zoneName.startsWith(entry.getKey())) {
                float[] range = entry.getValue();
                return range[0] + rng.nextFloat() * (range[1] - range[0]);
            }
        }
        return DEFAULT_MIN + rng.nextFloat() * (DEFAULT_MAX - DEFAULT_MIN);
    }
}
