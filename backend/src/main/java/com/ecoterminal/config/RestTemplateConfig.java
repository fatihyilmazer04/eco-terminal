package com.ecoterminal.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * AI servisi için RestTemplate.
     * Bağlantı timeout: 5s, okuma timeout: 10s.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 saniye
        factory.setReadTimeout(10_000);     // 10 saniye
        return new RestTemplate(factory);
    }

    /**
     * YOLOv8 servisi için RestTemplate.
     * Görüntü analizi daha uzun sürebileceğinden okuma timeout 60s.
     */
    @Bean
    @Qualifier("yoloRestTemplate")
    public RestTemplate yoloRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 saniye
        factory.setReadTimeout(60_000);     // 60 saniye (büyük görseller için)
        return new RestTemplate(factory);
    }
}
