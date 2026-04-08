package com.ecoterminal.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Firebase Cloud Messaging push bildirim servisi.
 *
 * FCM devre dışı modda (firebaseMessaging == null):
 *   Gerçek push gönderilmez — sadece log atılır ve true döndürülür.
 *   Bu sayede geliştirme ortamında Firebase kurulumu olmadan sistem çalışır.
 */
@Slf4j
@Service
public class FcmService {

    private final FirebaseMessaging firebaseMessaging;

    @Autowired
    public FcmService(@Autowired(required = false) FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
        if (firebaseMessaging == null) {
            log.info("FcmService: Simülasyon modunda çalışıyor (Firebase devre dışı)");
        }
    }

    /**
     * Belirli bir cihaz token'ına push bildirim gönderir.
     * @return true → başarılı veya simülasyon modu; false → FCM hatası
     */
    public boolean sendToToken(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("FCM token boş — bildirim atlandı: '{}'", title);
            return false;
        }

        if (firebaseMessaging == null) {
            log.info("[FCM SIM] Token={} | Başlık='{}' | Mesaj='{}'",
                    fcmToken.substring(0, Math.min(10, fcmToken.length())) + "...", title, body);
            return true;
        }

        try {
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setToken(fcmToken)
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("FCM başarılı: {} → {}", response, title);
            return true;

        } catch (FirebaseMessagingException e) {
            log.error("FCM gönderim hatası (token): {} — {}", title, e.getMessage());
            return false;
        }
    }

    /**
     * Bir topic'e abone tüm cihazlara push bildirim gönderir.
     * Topic format: "zone_{zoneId}_alerts"
     * @return true → başarılı veya simülasyon modu; false → FCM hatası
     */
    public boolean sendToTopic(String topic, String title, String body) {
        if (firebaseMessaging == null) {
            log.info("[FCM SIM] Topic={} | Başlık='{}' | Mesaj='{}'", topic, title, body);
            return true;
        }

        try {
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setTopic(topic)
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("FCM topic başarılı: {} → {}", response, title);
            return true;

        } catch (FirebaseMessagingException e) {
            log.error("FCM gönderim hatası (topic {}): {} — {}", topic, title, e.getMessage());
            return false;
        }
    }
}
