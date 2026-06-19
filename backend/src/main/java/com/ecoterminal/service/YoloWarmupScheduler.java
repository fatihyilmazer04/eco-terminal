package com.ecoterminal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Render free plan'daki YOLO ve LLM servislerini periyodik ping ile uyanık tutar.
 * Servisler 15 dakika inaktiflikte uyuduğundan, 4 dakikada bir /health çağrısı yapılır.
 */
@Slf4j
@Service
public class YoloWarmupScheduler {

    @Qualifier("yoloRestTemplate")
    private final RestTemplate yoloRestTemplate;

    @Value("${yolov8-service.base-url:http://yolov8-service:5001}")
    private String yoloServiceUrl;

    @Value("${LLM_SERVICE_URL:http://llm-service:5002}")
    private String llmServiceUrl;

    public YoloWarmupScheduler(@Qualifier("yoloRestTemplate") RestTemplate yoloRestTemplate) {
        this.yoloRestTemplate = yoloRestTemplate;
    }

    /** Her 4 dakikada bir YOLO /health endpoint'ini ping eder (Render sleep önleme). */
    @Scheduled(fixedDelay = 240_000)
    public void keepYoloAwake() {
        String url = yoloServiceUrl + "/health";
        try {
            ResponseEntity<String> resp = yoloRestTemplate.getForEntity(url, String.class);
            log.debug("YOLO warm-up ping OK: {} → {}", url, resp.getStatusCode());
        } catch (Exception e) {
            // Soğuk başlatma sırasında 502 beklenir — sadece debug log
            log.debug("YOLO warm-up ping başarısız (soğuk başlatma olabilir): {}", e.getMessage());
        }
    }

    /** Her 4 dakikada bir LLM /health endpoint'ini ping eder (Render sleep önleme). */
    @Scheduled(fixedDelay = 240_000, initialDelay = 120_000)
    public void keepLlmAwake() {
        String url = llmServiceUrl + "/health";
        try {
            ResponseEntity<String> resp = yoloRestTemplate.getForEntity(url, String.class);
            log.debug("LLM warm-up ping OK: {} → {}", url, resp.getStatusCode());
        } catch (Exception e) {
            log.debug("LLM warm-up ping başarısız (soğuk başlatma olabilir): {}", e.getMessage());
        }
    }
}
