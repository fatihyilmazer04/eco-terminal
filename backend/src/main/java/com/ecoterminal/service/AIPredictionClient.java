package com.ecoterminal.service;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.model.dto.AIPredictionResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class AIPredictionClient {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Value("${ai-service.base-url:http://localhost:5000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public AIPredictionClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Tek bölge için AI tahmin çağrısı.
     * Timeout veya erişim hatası → AiServiceException fırlatır.
     */
    public AIPredictionResponse getPredictionForZone(Long zoneId) {
        String url = aiServiceUrl + "/predict?zone_id=" + zoneId + "&next_minutes=30";
        try {
            ResponseEntity<AiRawPrediction> resp =
                    restTemplate.exchange(url, HttpMethod.GET, null, AiRawPrediction.class);
            AiRawPrediction raw = resp.getBody();
            if (raw == null) throw new AiServiceException("AI servisi boş yanıt döndü");
            return raw.toResponse();
        } catch (ResourceAccessException e) {
            throw new AiServiceException("AI servisi erişilemez: " + e.getMessage(), e);
        } catch (Exception e) {
            log.warn("AI single prediction failed for zone {}: {}", zoneId, e.getMessage());
            throw new AiServiceException("Tahmin alınamadı: " + e.getMessage(), e);
        }
    }

    /**
     * Tüm bölgeler için toplu tahmin.
     */
    public List<AIPredictionResponse> getAllPredictions() {
        String url = aiServiceUrl + "/predict/all?next_minutes=30";
        try {
            ResponseEntity<List<AiRawPrediction>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<AiRawPrediction>>() {}
            );
            List<AiRawPrediction> raws = resp.getBody();
            if (raws == null) return List.of();
            return raws.stream().map(AiRawPrediction::toResponse).toList();
        } catch (ResourceAccessException e) {
            throw new AiServiceException("AI servisi erişilemez: " + e.getMessage(), e);
        } catch (Exception e) {
            log.warn("AI all predictions failed: {}", e.getMessage());
            throw new AiServiceException("Toplu tahmin alınamadı: " + e.getMessage(), e);
        }
    }

    /**
     * AI servisinden dönen ham JSON → AIPredictionResponse dönüşümü.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiRawPrediction(
        @JsonProperty("zone_id")       Long    zoneId,
        @JsonProperty("zone_name")     String  zoneName,
        @JsonProperty("forecast_time") String  forecastTime,
        @JsonProperty("predicted_load") Float  predictedLoad,
        @JsonProperty("density_pct")   Float   densityPct,
        @JsonProperty("risk_level")    String  riskLevel,
        @JsonProperty("trend")         String  trend,
        @JsonProperty("confidence")    Float   confidence,
        @JsonProperty("generated_at")  String  generatedAt
    ) {
        AIPredictionResponse toResponse() {
            return new AIPredictionResponse(
                zoneId, zoneName,
                parseInstant(forecastTime),
                predictedLoad, densityPct,
                riskLevel, trend, confidence,
                parseInstant(generatedAt)
            );
        }

        private static Instant parseInstant(String s) {
            if (s == null) return Instant.now();
            return LocalDateTime.parse(s, DT_FMT).toInstant(ZoneOffset.UTC);
        }
    }
}
