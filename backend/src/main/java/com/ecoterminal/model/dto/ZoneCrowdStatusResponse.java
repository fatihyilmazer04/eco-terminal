package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.AIPrediction;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneMapPosition;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Tek bir zone'un anlık kalabalık durum özeti.
 * CrowdStatusController → /api/crowd/status (temel)
 * HeatmapController → /api/heatmap/live (pozisyon ve riskLevel dahil)
 */
@Data
@Builder
public class ZoneCrowdStatusResponse {

    private Long    zoneId;
    private String  zoneName;
    private String  zoneType;

    // ── Terminal harita bölümü (null → CrowdStatus endpoint'i, dolu → Heatmap endpoint'i)
    private String  section;    // CHECKIN, SECURITY, CONCOURSE_A/B/C, LOUNGE

    /** Anlık doluluk oranı (0.0–1.0) */
    private Float   currentDensity;
    private Integer peopleCount;
    private Integer capacity;

    /** EMPTY | MODERATE | BUSY | FULL */
    private String  status;

    /** INCREASING | DECREASING | STABLE (AI tahmininden) */
    private String  trend;

    /** AI tahmini: 30 dk sonraki doluluk */
    private Float   predictedLoad;

    /** LOW | MEDIUM | HIGH (AI'dan) */
    private String  riskLevel;

    // ── SVG harita koordinatları (0-100% normalize, null → harita kullanılmıyor)
    private Double  posX;
    private Double  posY;
    private Double  width;
    private Double  height;

    private Instant lastUpdated;

    // ── Fabrikalar ──────────────────────────────────────────────────────────────

    /** Temel fabrika: okuma + AI tahmini (harita koordinatı yok) */
    public static ZoneCrowdStatusResponse from(OccupancyReading reading,
                                               AIPrediction prediction) {
        Zone zone = reading.getZone();
        float density = reading.getDensityPct();

        return ZoneCrowdStatusResponse.builder()
                .zoneId(zone.getZoneId())
                .zoneName(zone.getZoneName())
                .zoneType(zone.getType().name())
                .currentDensity(density)
                .peopleCount(reading.getPeopleCount())
                .capacity(zone.getMaxCapacity())
                .status(resolveStatus(density))
                .trend(prediction != null ? prediction.getTrend() : "STABLE")
                .predictedLoad(prediction != null ? prediction.getPredictedLoad() : density)
                .riskLevel(prediction != null ? prediction.getRiskLevel() : resolveRisk(density))
                .lastUpdated(reading.getRecordedAt())
                .build();
    }

    /** Genişletilmiş fabrika: harita pozisyonu dahil (HeatmapController için) */
    public static ZoneCrowdStatusResponse from(OccupancyReading reading,
                                               AIPrediction prediction,
                                               ZoneMapPosition position) {
        ZoneCrowdStatusResponse response = from(reading, prediction);
        if (position != null) {
            response.setSection(position.getSection());
            response.setPosX(position.getPosX());
            response.setPosY(position.getPosY());
            response.setWidth(position.getWidth());
            response.setHeight(position.getHeight());
        }
        return response;
    }

    /** density_pct → durum etiketi */
    public static String resolveStatus(float density) {
        if (density >= 0.85f) return "FULL";
        if (density >= 0.65f) return "BUSY";
        if (density >= 0.40f) return "MODERATE";
        return "EMPTY";
    }

    /** density_pct → risk seviyesi */
    public static String resolveRisk(float density) {
        if (density >= 0.85f) return "HIGH";
        if (density >= 0.60f) return "MEDIUM";
        return "LOW";
    }
}
