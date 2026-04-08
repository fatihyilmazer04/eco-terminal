package com.ecoterminal.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK konfigürasyonu.
 *
 * Gerçek kullanım için:
 *   1. Firebase Console → Project Settings → Service Accounts
 *   2. "Generate New Private Key" ile JSON indir
 *   3. İndirilen JSON'ı src/main/resources/firebase-service-account.json dosyasına yapıştır
 *   4. Uygulamayı yeniden başlat — FCM push bildirimleri aktif olur
 *
 * firebase-service-account.json bulunamazsa veya geçersizse:
 *   - Uygulama FCM devre dışı modda çalışır
 *   - FcmService log atar, gerçek push göndermez (simülasyon modu)
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try {
            InputStream serviceAccount = getClass()
                    .getClassLoader()
                    .getResourceAsStream("firebase-service-account.json");

            if (serviceAccount == null) {
                log.warn("firebase-service-account.json bulunamadı — FCM devre dışı (simülasyon modu)");
                return null;
            }

            // Placeholder JSON kontrolü: gerçek key "YOUR_" ile başlıyorsa devre dışı
            byte[] bytes = serviceAccount.readAllBytes();
            String content = new String(bytes);
            if (content.contains("YOUR_PROJECT_ID") || content.contains("YOUR_PRIVATE_KEY_ID")) {
                log.warn("firebase-service-account.json placeholder içeriyor — FCM devre dışı (simülasyon modu)");
                log.warn("Gerçek push için Firebase Console'dan Service Account JSON indirip dosyaya yapıştırın.");
                return null;
            }

            // Gerçek credentials
            InputStream credStream = new java.io.ByteArrayInputStream(bytes);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credStream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK başlatıldı — FCM aktif");
            }

            return FirebaseMessaging.getInstance();

        } catch (IOException e) {
            log.warn("Firebase başlatılamadı: {} — FCM devre dışı (simülasyon modu)", e.getMessage());
            return null;
        }
    }
}
