package com.ecoterminal.controller;

import com.ecoterminal.exception.AiServiceException;
import com.ecoterminal.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Flask AI servisindeki /analyze/crowd endpoint'ini proxy eden controller.
 * Frontend /api/ai/crowd-analysis çağırır → backend Flask'a iletir.
 * Bu yaklaşım CORS sorunlarını önler ve auth merkezileştirir.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class AICrowdAnalysisController {

    @Value("${ai-service.base-url:http://localhost:5000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public AICrowdAnalysisController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * GET /api/ai/crowd-analysis
     * Flask /analyze/crowd endpoint'ini çağırır ve sonucu döndürür.
     * Tüm zone'ların son 1 saatlik verisini analiz eder.
     */
    @GetMapping("/crowd-analysis")
    public ResponseEntity<ApiResponse<Object>> getCrowdAnalysis() {
        String url = aiServiceUrl + "/analyze/crowd";
        try {
            Object result = restTemplate.getForObject(url, Object.class);
            log.debug("AI crowd-analysis proxy başarılı");
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (ResourceAccessException e) {
            log.warn("AI servisi erişilemiyor (crowd-analysis): {}", e.getMessage());
            throw new AiServiceException("AI kalabalık analiz servisi erişilemiyor: " + e.getMessage());
        }
    }
}
