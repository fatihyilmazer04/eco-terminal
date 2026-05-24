package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ServiceHealthResponse;
import com.ecoterminal.model.dto.SystemStatsResponse;
import com.ecoterminal.model.dto.ZoneSettingsResponse;
import com.ecoterminal.model.entity.DeviceStatus;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.IoTDeviceRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.UserRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final ZoneRepository             zoneRepo;
    private final UserRepository             userRepo;
    private final OccupancyReadingRepository occupancyRepo;
    private final IoTDeviceRepository        deviceRepo;
    private final JdbcTemplate               jdbcTemplate;
    private final RestTemplate               restTemplate;

    @Value("${ai-service.base-url:http://localhost:5000}")
    private String aiServiceUrl;

    // ── Sistem İstatistikleri ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SystemStatsResponse getSystemStats() {
        List<Zone> allZones    = zoneRepo.findAll();
        List<Zone> activeZones = zoneRepo.findByStatus(ZoneStatus.ACTIVE);
        long totalReadings     = occupancyRepo.count();
        long totalUsers        = userRepo.count();
        long totalDevices      = deviceRepo.count();
        long onlineDevices     = deviceRepo.findByStatus(DeviceStatus.ONLINE).size();

        return new SystemStatsResponse(
                allZones.size(),
                activeZones.size(),
                totalReadings,
                totalUsers,
                (int) totalDevices,
                (int) onlineDevices,
                "Spring Boot 3.2.5",
                System.getProperty("java.version", "21"),
                Instant.now()
        );
    }

    // ── Bölge Eşik Listesi ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ZoneSettingsResponse> getAllZoneSettings() {
        return zoneRepo.findByStatusOrderByZoneNameAsc(ZoneStatus.ACTIVE)
                .stream()
                .map(ZoneSettingsResponse::from)
                .toList();
    }

    // ── Bölge Eşiği Güncelle ─────────────────────────────────────────────────

    @Transactional
    public ZoneSettingsResponse updateZoneThreshold(Long zoneId, Float criticalThreshold,
                                                     Long actorId) {
        Zone zone = zoneRepo.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("Bölge"));

        float oldThreshold = zone.getCriticalThreshold() != null ? zone.getCriticalThreshold() : 0.85f;
        zone.setCriticalThreshold(criticalThreshold);
        zoneRepo.save(zone);

        // Audit log
        jdbcTemplate.update(
                "INSERT INTO audit_logs (actor_id, action_type, target_table, target_id, old_value, new_value) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)",
                actorId,
                "UPDATE_ZONE_THRESHOLD",
                "zones",
                zoneId,
                "{\"criticalThreshold\":" + oldThreshold + "}",
                "{\"criticalThreshold\":" + criticalThreshold + "}"
        );

        log.info("Zone {} threshold updated: {} → {} by user {}",
                zone.getZoneName(), oldThreshold, criticalThreshold, actorId);

        return ZoneSettingsResponse.from(zone);
    }

    // ── Servis Sağlık Kontrolü ────────────────────────────────────────────────

    public List<ServiceHealthResponse> checkServicesHealth() {
        return List.of(
                checkBackend(),
                checkAiService(),
                checkDatabase()
        );
    }

    private ServiceHealthResponse checkBackend() {
        // Backend'in kendisi — eğer bu metod çalışıyorsa backend UP
        return ServiceHealthResponse.up("Backend (Spring Boot)", 0);
    }

    private ServiceHealthResponse checkAiService() {
        long start = System.currentTimeMillis();
        try {
            restTemplate.getForObject(aiServiceUrl + "/health", String.class);
            return ServiceHealthResponse.up("AI Servisi (Flask)", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
            return ServiceHealthResponse.down("AI Servisi (Flask)", e.getMessage());
        }
    }

    private ServiceHealthResponse checkDatabase() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ServiceHealthResponse.up("PostgreSQL", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return ServiceHealthResponse.down("PostgreSQL", e.getMessage());
        }
    }
}
