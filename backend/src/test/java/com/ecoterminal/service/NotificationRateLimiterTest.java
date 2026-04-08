package com.ecoterminal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRateLimiter Unit Tests")
class NotificationRateLimiterTest {

    private NotificationRateLimiter rateLimiter;
    private ConcurrentHashMap<String, Instant> lastSentMap;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rateLimiter = new NotificationRateLimiter();
        // Internal map'e erişim — test için ReflectionTestUtils
        lastSentMap = (ConcurrentHashMap<String, Instant>)
                ReflectionTestUtils.getField(rateLimiter, "lastSentMap");
    }

    @Test
    @DisplayName("canSend_firstRequest_returnsTrue")
    void canSend_firstRequest_returnsTrue() {
        // Hiç gönderilmemiş → ilk istek izin alır
        assertThat(rateLimiter.canSend(1L, 10L)).isTrue();
    }

    @Test
    @DisplayName("canSend_secondRequestWithin5Min_returnsFalse")
    void canSend_secondRequestWithin5Min_returnsFalse() {
        // İlk istek → kayıt oluşturur
        rateLimiter.canSend(1L, 10L);

        // 5 dakika dolmadan ikinci istek → reddedilir
        assertThat(rateLimiter.canSend(1L, 10L)).isFalse();
    }

    @Test
    @DisplayName("canSend_requestAfter5Min_returnsTrue")
    void canSend_requestAfter5Min_returnsTrue() {
        // 5 dakikadan eski timestamp manuel set et
        String key = "userId_1_zone_10";
        Instant sixMinutesAgo = Instant.now().minus(6, ChronoUnit.MINUTES);
        lastSentMap.put(key, sixMinutesAgo);

        // Süresi geçmiş kayıt → yeni bildirime izin verilir
        assertThat(rateLimiter.canSend(1L, 10L)).isTrue();
    }

    @Test
    @DisplayName("canSend_differentZoneSameUser_returnsTrue")
    void canSend_differentZoneSameUser_returnsTrue() {
        // Zone 10 için rate limit dolu
        rateLimiter.canSend(1L, 10L);

        // Aynı kullanıcı, farklı zone 20 → farklı key → izin verilir
        assertThat(rateLimiter.canSend(1L, 20L)).isTrue();
    }

    @Test
    @DisplayName("canSend_differentUserSameZone_returnsTrue")
    void canSend_differentUserSameZone_returnsTrue() {
        // Kullanıcı 1, zone 10 rate limit dolu
        rateLimiter.canSend(1L, 10L);

        // Farklı kullanıcı 2, aynı zone 10 → farklı key → izin verilir
        assertThat(rateLimiter.canSend(2L, 10L)).isTrue();
    }

    @Test
    @DisplayName("cleanup_removesExpiredEntries")
    void cleanup_removesExpiredEntries() {
        // 2 eski kayıt ekle
        lastSentMap.put("userId_1_zone_1", Instant.now().minus(10, ChronoUnit.MINUTES));
        lastSentMap.put("userId_2_zone_2", Instant.now().minus(8, ChronoUnit.MINUTES));
        // 1 geçerli kayıt
        lastSentMap.put("userId_3_zone_3", Instant.now());

        rateLimiter.cleanup();

        assertThat(lastSentMap).hasSize(1);
        assertThat(lastSentMap).containsKey("userId_3_zone_3");
    }
}
