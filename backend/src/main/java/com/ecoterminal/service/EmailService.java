package com.ecoterminal.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    private static final String SENDER_NAME = "Eco-Terminal";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Email doğrulama kodu gönderir (kayıt akışı).
     */
    public void sendVerificationCode(String toEmail, String code) {
        send(toEmail, code, "register");
    }

    /**
     * Şifre sıfırlama kodu gönderir.
     */
    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, code, "reset");
    }

    // ── Ortak gönderim ───────────────────────────────────────────────────────

    private void send(String toEmail, String code, String type) {
        if (!mailEnabled) {
            logDemoCode(toEmail, code, type);
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            // Gönderen: "Eco-Terminal <your@gmail.com>"
            helper.setFrom(new InternetAddress(fromAddress, SENDER_NAME));
            helper.setTo(toEmail);
            helper.setSubject(subject(type));
            helper.setText(buildHtml(code, type), true); // true = HTML

            mailSender.send(mime);
            log.info("[EMAIL] {} kodu gönderildi: {}", type, toEmail);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn("[EMAIL] SMTP hatası ({}), DEMO MODU devreye girdi: {}", ex.getMessage(), toEmail);
            logDemoCode(toEmail, code, type);
        }
    }

    private String subject(String type) {
        return "reset".equals(type)
                ? "Eco-Terminal — Şifre Sıfırlama Kodunuz"
                : "Eco-Terminal — E-posta Doğrulama Kodunuz";
    }

    // ── Demo log ─────────────────────────────────────────────────────────────

    private void logDemoCode(String email, String code, String type) {
        String label = "reset".equals(type) ? "ŞİFRE SIFIRLAMA" : "KAYIT DOĞRULAMA";
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║  DEMO MODU — {} KOD", label);
        log.info("║  E-posta : {}", email);
        log.info("║  Kod     : {}", code);
        log.info("║  (MAIL_ENABLED=true yapınca gerçek mail gider)   ║");
        log.info("╚══════════════════════════════════════════════════╝");
    }

    // ── HTML içerik ──────────────────────────────────────────────────────────

    private String buildHtml(String code, String type) {
        boolean isReset = "reset".equals(type);
        String heading = isReset ? "Şifre Sıfırlama" : "E-posta Doğrulama";
        String desc    = isReset
                ? "Şifrenizi sıfırlamak için aşağıdaki doğrulama kodunu kullanın."
                : "Eco-Terminal hesabınızı doğrulamak için aşağıdaki kodu kullanın.";
        String note    = isReset
                ? "Bu isteği siz başlatmadıysanız şifreniz güvende — bu e-postayı yoksayın."
                : "Bu hesabı siz oluşturmadıysanız bu e-postayı yoksayın.";

        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#0f172a;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;padding:40px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#1e293b;border-radius:16px;border:1px solid #334155;overflow:hidden;">

                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#1a3a2a,#0f2d1f);
                                     padding:32px 40px;text-align:center;border-bottom:1px solid #2ECC7130;">
                            <div style="font-size:28px;margin-bottom:8px;">🌿</div>
                            <div style="color:#2ECC71;font-size:22px;font-weight:700;letter-spacing:1px;">
                              Eco-Terminal
                            </div>
                            <div style="color:#94a3b8;font-size:12px;margin-top:4px;">
                              Akıllı Havalimanı Yönetim Sistemi
                            </div>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:40px;">
                            <h2 style="color:#f1f5f9;font-size:20px;margin:0 0 12px 0;">%s</h2>
                            <p style="color:#94a3b8;font-size:14px;line-height:1.6;margin:0 0 28px 0;">%s</p>

                            <!-- Kod kutusu -->
                            <div style="background:#0f172a;border:2px solid #2ECC7150;border-radius:12px;
                                        padding:24px;text-align:center;margin-bottom:28px;">
                              <div style="color:#64748b;font-size:11px;text-transform:uppercase;
                                          letter-spacing:2px;margin-bottom:12px;">Doğrulama Kodu</div>
                              <div style="color:#2ECC71;font-size:40px;font-weight:700;
                                          letter-spacing:12px;font-family:monospace;">%s</div>
                              <div style="color:#64748b;font-size:12px;margin-top:12px;">
                                ⏱ 10 dakika geçerlidir
                              </div>
                            </div>

                            <!-- Uyarı -->
                            <div style="background:#f59e0b15;border:1px solid #f59e0b30;border-radius:8px;
                                        padding:12px 16px;margin-bottom:24px;">
                              <span style="color:#f59e0b;font-size:13px;">
                                🔒 Bu kodu <strong>kimseyle paylaşmayın.</strong>
                                Eco-Terminal çalışanları sizden asla kod istemez.
                              </span>
                            </div>

                            <p style="color:#475569;font-size:13px;line-height:1.6;margin:0;">%s</p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="padding:20px 40px;border-top:1px solid #1e293b;text-align:center;">
                            <div style="color:#334155;font-size:12px;">
                              © 2026 Eco-Terminal · Tüm hakları saklıdır
                            </div>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, desc, code, note);
    }
}
