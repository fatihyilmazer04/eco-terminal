package com.ecoterminal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * E-posta gönderim servisi — Resend API (https://resend.com) üzerinden çalışır.
 * MAIL_ENABLED=false veya RESEND_API_KEY boş ise demo moduna düşer:
 * kod backend log'una yazılır, gerçek mail gönderilmez.
 */
@Slf4j
@Service
public class EmailService {

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from:noreply@ecoterminal.com}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    private static final String RESEND_URL  = "https://api.resend.com/emails";
    private static final String SENDER_NAME = "Eco-Terminal";

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Public API ────────────────────────────────────────────────────────────

    public void sendVerificationCode(String toEmail, String code) {
        send(toEmail, code, "register");
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, code, "reset");
    }

    // ── Ortak gönderim ───────────────────────────────────────────────────────

    private void send(String toEmail, String code, String type) {
        if (!mailEnabled || apiKey.isBlank()) {
            if (mailEnabled && apiKey.isBlank()) {
                log.warn("[EMAIL] MAIL_ENABLED=true ama RESEND_API_KEY boş — demo moduna düşüldü");
            }
            logDemoCode(toEmail, code, type);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "from",    SENDER_NAME + " <" + fromAddress + ">",
                "to",      List.of(toEmail),
                "subject", subject(type),
                "html",    buildHtml(code, type)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(RESEND_URL, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[EMAIL] {} kodu gönderildi: {}", type, toEmail);
            } else {
                log.warn("[EMAIL] Resend API {} hatası — demo moduna düşüldü", response.getStatusCode());
                logDemoCode(toEmail, code, type);
            }
        } catch (Exception ex) {
            log.warn("[EMAIL] Resend API hatası ({}), demo moduna düşüldü: {}", ex.getMessage(), toEmail);
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
        log.info("║  (MAIL_ENABLED=true + RESEND_API_KEY ile gerçek mail gider)");
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
                        <tr>
                          <td style="padding:40px;">
                            <h2 style="color:#f1f5f9;font-size:20px;margin:0 0 12px 0;">%s</h2>
                            <p style="color:#94a3b8;font-size:14px;line-height:1.6;margin:0 0 28px 0;">%s</p>
                            <div style="background:#0f172a;border:2px solid #2ECC7150;border-radius:12px;
                                        padding:24px;text-align:center;margin-bottom:28px;">
                              <div style="color:#64748b;font-size:11px;text-transform:uppercase;
                                          letter-spacing:2px;margin-bottom:12px;">Doğrulama Kodu</div>
                              <div style="color:#2ECC71;font-size:40px;font-weight:700;
                                          letter-spacing:12px;font-family:monospace;">%s</div>
                              <div style="color:#64748b;font-size:12px;margin-top:12px;">
                                &#9201; 10 dakika geçerlidir
                              </div>
                            </div>
                            <div style="background:#f59e0b15;border:1px solid #f59e0b30;border-radius:8px;
                                        padding:12px 16px;margin-bottom:24px;">
                              <span style="color:#f59e0b;font-size:13px;">
                                &#128274; Bu kodu <strong>kimseyle paylaşmayın.</strong>
                                Eco-Terminal çalışanları sizden asla kod istemez.
                              </span>
                            </div>
                            <p style="color:#475569;font-size:13px;line-height:1.6;margin:0;">%s</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 40px;border-top:1px solid #1e293b;text-align:center;">
                            <div style="color:#334155;font-size:12px;">
                              &#169; 2026 Eco-Terminal &middot; Tüm hakları saklıdır
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
