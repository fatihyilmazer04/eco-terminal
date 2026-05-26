package com.ecoterminal.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RateLimiterConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Lettuce tabanlı Redis ProxyManager — Bucket4j dağıtık rate limiting için.
     * Birden fazla backend instance çalışsa bile IP başına bucket Redis'te ortaklaşa tutulur.
     */
    @Bean
    public ProxyManager<byte[]> lettuceProxyManager() {
        RedisURI uri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        RedisClient client = RedisClient.create(uri);
        log.info("Redis rate limiter başlatıldı: {}:{}", redisHost, redisPort);
        return LettuceBasedProxyManager.builderFor(client)
                .build();
    }
}
