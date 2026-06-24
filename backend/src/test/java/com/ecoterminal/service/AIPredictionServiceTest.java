package com.ecoterminal.service;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.AIPredictionResponse;
import com.ecoterminal.model.entity.AIPrediction;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneType;
import com.ecoterminal.repository.AIPredictionRepository;
import com.ecoterminal.repository.EnvironmentalMetricRepository;
import com.ecoterminal.repository.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIPredictionService Unit Tests")
class AIPredictionServiceTest {

    @Mock private AIPredictionClient          aiClient;
    @Mock private AIPredictionRepository      predRepository;
    @Mock private ZoneRepository              zoneRepository;
    @Mock private EnvironmentalMetricRepository metricRepo;

    @InjectMocks
    private AIPredictionService aiPredictionService;

    // ── getPredictionsForAdmin Tests ──────────────────────────────────────────

    @Test
    @DisplayName("getPredictionsForAdmin_mapsAllLatestPredictions")
    void getPredictionsForAdmin_mapsAllLatestPredictions() {
        // given
        Zone zone = buildZone(1L, "Gate A1");
        AIPrediction pred1 = buildPrediction(zone, 0.70f, "MEDIUM");
        AIPrediction pred2 = buildPrediction(zone, 0.90f, "HIGH");
        when(predRepository.findLatestPerZone()).thenReturn(List.of(pred1, pred2));

        // when
        List<AIPredictionResponse> result = aiPredictionService.getPredictionsForAdmin();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getPredictionsForAdmin_withNoPredictions_returnsEmpty")
    void getPredictionsForAdmin_withNoPredictions_returnsEmpty() {
        // given
        when(predRepository.findLatestPerZone()).thenReturn(List.of());

        // when
        List<AIPredictionResponse> result = aiPredictionService.getPredictionsForAdmin();

        // then
        assertThat(result).isEmpty();
    }

    // ── getHighRiskZones Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("getHighRiskZones_returnsOnlyHighRiskPredictions")
    void getHighRiskZones_returnsOnlyHighRiskPredictions() {
        // given — 1 HIGH, 1 MEDIUM, 1 LOW
        Zone zone = buildZone(1L, "Gate A1");
        AIPrediction high   = buildPrediction(zone, 0.90f, "HIGH");
        AIPrediction medium = buildPrediction(zone, 0.70f, "MEDIUM");
        AIPrediction low    = buildPrediction(zone, 0.30f, "LOW");
        when(predRepository.findLatestPerZone()).thenReturn(List.of(high, medium, low));

        // when
        List<AIPredictionResponse> result = aiPredictionService.getHighRiskZones();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).riskLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("getHighRiskZones_withNoHighRisk_returnsEmpty")
    void getHighRiskZones_withNoHighRisk_returnsEmpty() {
        // given
        Zone zone = buildZone(1L, "Gate A1");
        AIPrediction medium = buildPrediction(zone, 0.60f, "MEDIUM");
        when(predRepository.findLatestPerZone()).thenReturn(List.of(medium));

        // when
        List<AIPredictionResponse> result = aiPredictionService.getHighRiskZones();

        // then
        assertThat(result).isEmpty();
    }

    // ── getPredictionForZone Tests ────────────────────────────────────────────

    @Test
    @DisplayName("getPredictionForZone_withCachedData_returnsFromCache")
    void getPredictionForZone_withCachedData_returnsFromCache() {
        // given — son 5 dakika içinde tahmin var
        Zone zone = buildZone(1L, "Gate A1");
        AIPrediction cached = buildPrediction(zone, 0.65f, "MEDIUM");
        when(predRepository.findRecentByZoneId(eq(1L), any(Instant.class)))
                .thenReturn(List.of(cached));

        // when
        AIPredictionResponse result = aiPredictionService.getPredictionForZone(1L);

        // then — AI client çağrılmamalı
        verify(aiClient, never()).getPredictionForZone(anyLong());
        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("getPredictionForZone_withNoCache_callsAiClient")
    void getPredictionForZone_withNoCache_callsAiClient() {
        // given — cache boş
        when(predRepository.findRecentByZoneId(eq(1L), any(Instant.class)))
                .thenReturn(List.of());
        AIPredictionResponse fresh = buildResponse(1L, "Gate A1", 0.90f, "HIGH");
        when(aiClient.getPredictionForZone(1L)).thenReturn(fresh);
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(buildZone(1L, "Gate A1")));

        // when
        AIPredictionResponse result = aiPredictionService.getPredictionForZone(1L);

        // then — AI client çağrılmalı
        verify(aiClient).getPredictionForZone(1L);
        assertThat(result.riskLevel()).isEqualTo("HIGH");
    }

    // ── refreshPredictions Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("refreshPredictions_callsAiClientAndReturnsList")
    void refreshPredictions_callsAiClientAndReturnsList() {
        // given
        AIPredictionResponse r1 = buildResponse(1L, "Zone A", 0.50f, "MEDIUM");
        AIPredictionResponse r2 = buildResponse(2L, "Zone B", 0.30f, "LOW");
        when(aiClient.getAllPredictions()).thenReturn(List.of(r1, r2));
        when(zoneRepository.findAll()).thenReturn(List.of());

        // when
        List<AIPredictionResponse> result = aiPredictionService.refreshPredictions();

        // then
        verify(aiClient).getAllPredictions();
        assertThat(result).hasSize(2);
    }

    // ── getZoneForecast Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("getZoneForecast_withNonExistentZone_throwsNotFound")
    void getZoneForecast_withNonExistentZone_throwsNotFound() {
        // given
        when(zoneRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> aiPredictionService.getZoneForecast(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getZoneForecast_withValidZone_returnsForecastResponse")
    void getZoneForecast_withValidZone_returnsForecastResponse() {
        // given
        Zone zone = buildZone(1L, "Gate A1");
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone));
        AIPrediction cached = buildPrediction(zone, 0.50f, "MEDIUM");
        when(predRepository.findRecentByZoneId(eq(1L), any(Instant.class)))
                .thenReturn(List.of(cached));

        // when
        var result = aiPredictionService.getZoneForecast(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.zoneId()).isEqualTo(1L);
        assertThat(result.zoneName()).isEqualTo("Gate A1");
        assertThat(result.shortTerm()).isNotEmpty();
        assertThat(result.longTerm()).isNotEmpty();
    }

    // ── fetchAndStorePredictions Tests ────────────────────────────────────────

    @Test
    @DisplayName("fetchAndStorePredictions_whenAiServiceFails_doesNotPropagateException")
    void fetchAndStorePredictions_whenAiServiceFails_doesNotPropagateException() {
        // given — AI servisi hata fırlatıyor
        when(aiClient.getAllPredictions())
                .thenThrow(new AiServiceException("AI servisi erişilemez"));

        // when — hata yutulmalı, uygulama durmamalı
        aiPredictionService.fetchAndStorePredictions();

        // then — exception fırlatılmadı (test geçti)
        verify(predRepository, never()).saveAll(any());
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private Zone buildZone(Long id, String name) {
        return Zone.builder()
                .zoneId(id)
                .zoneName(name)
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .build();
    }

    private AIPrediction buildPrediction(Zone zone, float load, String riskLevel) {
        return AIPrediction.builder()
                .zone(zone)
                .predictedLoad(load)
                .densityPct(load)
                .riskLevel(riskLevel)
                .trend("STABLE")
                .confidence(0.80f)
                .forecastTime(Instant.now().plus(30, ChronoUnit.MINUTES))
                .generatedAt(Instant.now())
                .build();
    }

    private AIPredictionResponse buildResponse(Long zoneId, String name, float load, String risk) {
        return new AIPredictionResponse(
                zoneId, name,
                Instant.now().plus(30, ChronoUnit.MINUTES),
                load, load, risk, "STABLE", 0.80f, Instant.now()
        );
    }
}
