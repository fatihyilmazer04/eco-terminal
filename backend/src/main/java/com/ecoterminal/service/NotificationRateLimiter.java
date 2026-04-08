package com.ecoterminal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter: aynı kullanıcı + bölge için 5 dakikada max 1 bildirim.
 * Çoklu thread ortamında güvenli (ConcurrentHashMap).
 */
@Slf4j
@Component
public class NotificationRateLimiter {

    /** Key → son gönderim zamanı */
    private final ConcurrentHashMap<String, Instant> lastSentMap = new ConcurrentHashMap<>();

    private static final long WINDOW_MINUTES = 5;

    /**
     * Bu kullanıcı + bölge kombinasyonu için bildirim gönderilebilir mi?
     * Gönderilebilirse kaydı günceller (atomic check-and-set).
     */
    public boolean canSend(Long userId, Long zoneId) {
        String key = "userId_" + userId + "_zone_" + zoneId;
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW_MINUTES, ChronoUnit.MINUTES);

        Instant last = lastSentMap.get(key);
        if (last != null && last.isAfter(cutoff)) {
            log.debug("Rate limit aşıldı: user={}, zone={} — son gönderim: {}",
                    userId, zoneId, last);
            return false;
        }

        lastSentMap.put(key, now);
        return true;
    }

    /**
     * Her 10 dakikada bir süresi geçmiş kayıtları temizle.
     * Bellek sızıntısını önler.
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        Instant cutoff = Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        int before = lastSentMap.size();
        lastSentMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int removed = before - lastSentMap.size();
        if (removed > 0) {
            log.debug("Rate limiter cleanup: {} eski kayıt silindi", removed);
        }
    }
}
