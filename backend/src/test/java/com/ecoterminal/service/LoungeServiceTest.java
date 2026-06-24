package com.ecoterminal.service;

import com.ecoterminal.model.dto.LoungeResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.DensityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoungeService Unit Tests")
class LoungeServiceTest {

    @Mock private OccupancyService occupancyService;

    @InjectMocks
    private LoungeService loungeService;

    // ── getQuietLounges Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("getQuietLounges_returnsOnlyLoungesWithDensityBelow50Pct")
    void getQuietLounges_returnsOnlyLoungesWithDensityBelow50Pct() {
        // given — 2 LOUNGE: biri < 0.50, diğeri > 0.50; 1 GATE (filtrelenmeli)
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Lounge-1", "LOUNGE", 0.30f, DensityLevel.LOW),
                buildZone(2L, "Lounge-2", "LOUNGE", 0.70f, DensityLevel.HIGH),
                buildZone(3L, "Gate A1",  "GATE",   0.20f, DensityLevel.LOW)
        ));

        // when
        List<LoungeResponse> result = loungeService.getQuietLounges();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).zoneName()).isEqualTo("Lounge-1");
    }

    @Test
    @DisplayName("getQuietLounges_sortedByDensityAscending")
    void getQuietLounges_sortedByDensityAscending() {
        // given — 2 quiet lounge
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Lounge-A", "LOUNGE", 0.40f, DensityLevel.LOW),
                buildZone(2L, "Lounge-B", "LOUNGE", 0.10f, DensityLevel.LOW)
        ));

        // when
        List<LoungeResponse> result = loungeService.getQuietLounges();

        // then — düşük yoğunluk önce
        assertThat(result).hasSize(2);
        assertThat(result.get(0).zoneName()).isEqualTo("Lounge-B");
        assertThat(result.get(1).zoneName()).isEqualTo("Lounge-A");
    }

    @Test
    @DisplayName("getQuietLounges_withAllDenseCrowded_returnsEmpty")
    void getQuietLounges_withAllDenseCrowded_returnsEmpty() {
        // given — tüm lounge'lar yoğun
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Lounge-X", "LOUNGE", 0.80f, DensityLevel.HIGH)
        ));

        // when
        List<LoungeResponse> result = loungeService.getQuietLounges();

        // then
        assertThat(result).isEmpty();
    }

    // ── getAllLounges Tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllLounges_returnsAllLoungeTypeZones")
    void getAllLounges_returnsAllLoungeTypeZones() {
        // given — 2 LOUNGE, 1 GATE
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Lounge-1", "LOUNGE", 0.30f, DensityLevel.LOW),
                buildZone(2L, "Lounge-2", "LOUNGE", 0.80f, DensityLevel.HIGH),
                buildZone(3L, "Gate A1",  "GATE",   0.50f, DensityLevel.MEDIUM)
        ));

        // when
        List<LoungeResponse> result = loungeService.getAllLounges();

        // then — sadece LOUNGE'lar, density filtresi yok
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(LoungeResponse::zoneName))
                .containsExactlyInAnyOrder("Lounge-1", "Lounge-2");
    }

    // ── getBestLounge Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("getBestLounge_returnsLowestDensityLounge")
    void getBestLounge_returnsLowestDensityLounge() {
        // given — 2 lounge, farklı yoğunluk
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Lounge-A", "LOUNGE", 0.70f, DensityLevel.HIGH),
                buildZone(2L, "Lounge-B", "LOUNGE", 0.15f, DensityLevel.LOW)
        ));

        // when
        Optional<LoungeResponse> result = loungeService.getBestLounge();

        // then — en az yoğun seçilmeli
        assertThat(result).isPresent();
        assertThat(result.get().zoneName()).isEqualTo("Lounge-B");
    }

    @Test
    @DisplayName("getBestLounge_withNoLounges_returnsEmpty")
    void getBestLounge_withNoLounges_returnsEmpty() {
        // given — sadece GATE bölgesi var
        when(occupancyService.getAllZonesWithOccupancy()).thenReturn(List.of(
                buildZone(1L, "Gate A1", "GATE", 0.50f, DensityLevel.MEDIUM)
        ));

        // when
        Optional<LoungeResponse> result = loungeService.getBestLounge();

        // then
        assertThat(result).isEmpty();
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private ZoneOccupancyResponse buildZone(Long id, String name, String type,
                                            float density, DensityLevel level) {
        return new ZoneOccupancyResponse(
                id, name, type, 200, 0.85f,
                (int)(density * 200), density, level,
                level.getColorCode(), null
        );
    }
}
