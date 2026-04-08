package com.ecoterminal.config;

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
}
