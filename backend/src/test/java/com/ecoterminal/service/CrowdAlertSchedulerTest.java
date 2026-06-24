package com.ecoterminal.service;

import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.DensityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrowdAlertScheduler Unit Tests")
class CrowdAlertSchedulerTest {

    @Mock private OccupancyService    occupancyService;
    @Mock private NotificationService notifService;

    @InjectMocks
    private CrowdAlertScheduler crowdAlertScheduler;

    // ── checkAndAlertCriticalZones Tests ──────────────────────────────────────

    @Test
    @DisplayName("checkAndAlertCriticalZones_withNoCriticalZones_sendsNoAlerts")
    void checkAndAlertCriticalZones_withNoCriticalZones_sendsNoAlerts() {
        // given — 1 zone, düşük yoğunluk (< 0.85)
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, 0.50f, DensityLevel.MEDIUM)
        ));

        // when
        crowdAlertScheduler.checkAndAlertCriticalZones();

        // then — bildirim gönderilmemeli
        verify(notifService, never()).triggerCrowdAlert(anyLong(), anyFloat());
    }

    @Test
    @DisplayName("checkAndAlertCriticalZones_withCriticalZone_sendsAlert")
    void checkAndAlertCriticalZones_withCriticalZone_sendsAlert() {
        // given — 1 zone, kritik yoğunluk (>= 0.85)
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, 0.90f, DensityLevel.CRITICAL)
        ));
        when(notifService.triggerCrowdAlert(1L, 0.90f)).thenReturn(3);

        // when
        crowdAlertScheduler.checkAndAlertCriticalZones();

        // then
        verify(notifService).triggerCrowdAlert(eq(1L), eq(0.90f));
    }

    @Test
    @DisplayName("checkAndAlertCriticalZones_withMultipleCriticalZones_alertsEach")
    void checkAndAlertCriticalZones_withMultipleCriticalZones_alertsEach() {
        // given — 2 kritik zone, 1 normal zone
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, 0.88f, DensityLevel.CRITICAL),
                buildZone(2L, 0.95f, DensityLevel.CRITICAL),
                buildZone(3L, 0.40f, DensityLevel.LOW)
        ));
        when(notifService.triggerCrowdAlert(anyLong(), anyFloat())).thenReturn(1);

        // when
        crowdAlertScheduler.checkAndAlertCriticalZones();

        // then — sadece 2 kritik zone için bildirim gönderilmeli
        verify(notifService, times(2)).triggerCrowdAlert(anyLong(), anyFloat());
        verify(notifService, never()).triggerCrowdAlert(eq(3L), anyFloat());
    }

    @Test
    @DisplayName("checkAndAlertCriticalZones_withExactlyThresholdDensity_sendsAlert")
    void checkAndAlertCriticalZones_withExactlyThresholdDensity_sendsAlert() {
        // given — tam eşik değerinde (0.85)
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, 0.85f, DensityLevel.HIGH)
        ));
        when(notifService.triggerCrowdAlert(1L, 0.85f)).thenReturn(0);

        // when
        crowdAlertScheduler.checkAndAlertCriticalZones();

        // then — 0.85 >= 0.85 koşulu sağlanıyor
        verify(notifService).triggerCrowdAlert(eq(1L), eq(0.85f));
    }

    @Test
    @DisplayName("checkAndAlertCriticalZones_withNullDensity_skipsZone")
    void checkAndAlertCriticalZones_withNullDensity_skipsZone() {
        // given — density null olan zone
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                new ZoneOccupancyResponse(1L, "Zone A", "GATE", 200, 0.85f,
                        0, null, DensityLevel.LOW, "#green", null)
        ));

        // when
        crowdAlertScheduler.checkAndAlertCriticalZones();

        // then — null density → filtre geçilmez
        verify(notifService, never()).triggerCrowdAlert(anyLong(), anyFloat());
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private ZoneOccupancyResponse buildZone(Long id, float density, DensityLevel level) {
        return new ZoneOccupancyResponse(
                id, "Zone " + id, "GATE", 200, 0.85f,
                (int)(density * 200), density, level,
                level.getColorCode(), null
        );
    }
}
