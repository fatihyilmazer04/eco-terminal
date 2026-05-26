package com.ecoterminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Günlük veri saklama politikası uygulayıcısı.
 * 90 günden eski ham sensör verileri (occupancy_readings, environmental_metrics)
 * her gün saat 02:00'da silinir. AI tahminleri 180 gün saklanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private static final int OCCUPANCY_RETENTION_DAYS    = 90;
    private static final int ENERGY_RETENTION_DAYS       = 90;
    private static final int PREDICTION_RETENTION_DAYS   = 180;

    private final JdbcTemplate    jdbcTemplate;
    private final AuditLogService auditLogService;

    @Scheduled(cron = "0 0 2 * * *")   // Her gün saat 02:00
    @Transactional
    public void runRetention() {
        log.info("Veri saklama görevi başladı");

        int occupancyDeleted  = deleteOldOccupancyReadings();
        int energyDeleted     = deleteOldEnvironmentalMetrics();
        int predictionDeleted = deleteOldAiPredictions();

        String summary = String.format(
                "{\"occupancy_deleted\":%d,\"energy_deleted\":%d,\"prediction_deleted\":%d}",
                occupancyDeleted, energyDeleted, predictionDeleted);

        auditLogService.logSystem("DATA_RETENTION", "occupancy_readings,environmental_metrics,ai_predictions", summary);

        log.info("Veri saklama tamamlandı — occupancy={}, energy={}, prediction={}",
                occupancyDeleted, energyDeleted, predictionDeleted);
    }

    private int deleteOldOccupancyReadings() {
        Instant cutoff = Instant.now().minus(OCCUPANCY_RETENTION_DAYS, ChronoUnit.DAYS);
        int count = jdbcTemplate.update(
                "DELETE FROM occupancy_readings WHERE recorded_at < ?",
                java.sql.Timestamp.from(cutoff));
        log.debug("occupancy_readings: {} kayıt silindi (cutoff={})", count, cutoff);
        return count;
    }

    private int deleteOldEnvironmentalMetrics() {
        Instant cutoff = Instant.now().minus(ENERGY_RETENTION_DAYS, ChronoUnit.DAYS);
        int count = jdbcTemplate.update(
                "DELETE FROM environmental_metrics WHERE recorded_at < ?",
                java.sql.Timestamp.from(cutoff));
        log.debug("environmental_metrics: {} kayıt silindi (cutoff={})", count, cutoff);
        return count;
    }

    private int deleteOldAiPredictions() {
        Instant cutoff = Instant.now().minus(PREDICTION_RETENTION_DAYS, ChronoUnit.DAYS);
        int count = jdbcTemplate.update(
                "DELETE FROM ai_predictions WHERE generated_at < ?",
                java.sql.Timestamp.from(cutoff));
        log.debug("ai_predictions: {} kayıt silindi (cutoff={})", count, cutoff);
        return count;
    }
}
