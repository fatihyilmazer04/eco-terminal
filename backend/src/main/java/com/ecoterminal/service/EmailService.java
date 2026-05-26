package com.ecoterminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    /**
     * Doğrulama kodu gönderir.
     *
     * <p>MAIL_ENABLED=true → gerçek Gmail SMTP ile gönderilir.
     * <p>MAIL_ENABLED=false veya gönderim hatası → DEMO MODU: kod log'a yazılır,
     * exception fırlatılmaz (sistem her zaman çalışır).
     */
    public void sendVerificationCode(String toEmail, String code) {
        if (!mailEnabled) {
            logDemoCode(toEmail, code);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Eco-Terminal — E-posta Doğrulama Kodunuz");
            message.setText(buildEmailText(code));
            mailSender.send(message);
            log.info("[EMAIL] Doğrulama kodu gönderildi: {}", toEmail);
        } catch (MailException ex) {
            log.warn("[EMAIL] SMTP gönderimi başarısız ({}), DEMO MODU devreye girdi: {}",
                    ex.getMessage(), toEmail);
            logDemoCode(toEmail, code);
        }
    }

    private void logDemoCode(String email, String code) {
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║  DEMO MODU — Gerçek mail gönderilmedi            ║");
        log.info("║  E-posta : {}",  email);
        log.info("║  Kod     : {}                                    ║", code);
        log.info("║  (MAIL_ENABLED=true yapınca gerçek mail gider)   ║");
        log.info("╚══════════════════════════════════════════════════╝");
    }

    private String buildEmailText(String code) {
        return """
                Merhaba,

                Eco-Terminal hesabınızı doğrulamak için aşağıdaki 6 haneli kodu kullanın:

                    %s

                Bu kod 10 dakika geçerlidir. Kodu kimseyle paylaşmayın.

                Eğer bu işlemi siz başlatmadıysanız bu e-postayı dikkate almayın.

                Eco-Terminal Ekibi
                """.formatted(code);
    }
}
