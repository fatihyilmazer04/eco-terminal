package com.ecoterminal.service;

import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Her 1 dakikada bir kritik yoğunluk seviyesine ulaşan bölgeleri kontrol eder
 * ve ilgili kullanıcılara otomatik bildirim gönderir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrowdAlertScheduler {

    private static final float CRITICAL_THRESHOLD = 0.85f;

    private final OccupancyService     occupancyService;
    private final NotificationService  notifService;

    @Scheduled(fixedDelay = 60_000)   // 1 dakika
    public void checkAndAlertCriticalZones() {
        List<ZoneOccupancyResponse> zones = occupancyService.getAllZonesWithOccupancy();

        List<ZoneOccupancyResponse> critical = zones.stream()
                .filter(z -> z.densityPct() != null && z.densityPct() >= CRITICAL_THRESHOLD)
                .toList();

        int totalSent = 0;
        for (ZoneOccupancyResponse zone : critical) {
            int sent = notifService.triggerCrowdAlert(zone.zoneId(), zone.densityPct().floatValue());
            totalSent += sent;
        }

        log.info("Yoğunluk kontrolü tamamlandı. {} kritik bölge, {} bildirim gönderildi.",
                critical.size(), totalSent);
    }
}
