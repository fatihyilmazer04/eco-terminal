package com.ecoterminal.service;

import com.ecoterminal.model.entity.VerificationCode;
import com.ecoterminal.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final int CODE_LENGTH      = 6;
    private static final int EXPIRY_MINUTES   = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final VerificationCodeRepository codeRepository;
    private final EmailService emailService;

    private final SecureRandom random = new SecureRandom();

    /**
     * 6 haneli kod üret, DB'ye kaydet, email ile gönder.
     * Aynı email için son 60 saniye içinde kod gönderildiyse tekrar gönderilmez
     * (spam koruması). True dönerse "gönderildi", false dönerse "cooldown".
     */
    @Transactional
    public boolean generateAndSend(String email, String purpose) {
        // Spam koruması: son 60 sn içinde aktif kod var mı?
        Instant cooldownCutoff = Instant.now().minus(RESEND_COOLDOWN_SECONDS, ChronoUnit.SECONDS);
        Optional<VerificationCode> recent =
                codeRepository.findTopByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        email, purpose, cooldownCutoff);

        if (recent.isPresent() &&
                recent.get().getCreatedAt().isAfter(cooldownCutoff)) {
            log.debug("[VERIF] Cooldown aktif, kod tekrar gönderilmedi: {}", email);
            return false;
        }

        String code = generateCode();
        Instant expiresAt = Instant.now().plus(EXPIRY_MINUTES, ChronoUnit.MINUTES);

        VerificationCode entity = VerificationCode.builder()
                .email(email)
                .code(code)
                .purpose(purpose)
                .expiresAt(expiresAt)
                .build();
        codeRepository.save(entity);

        emailService.sendVerificationCode(email, code);
        log.info("[VERIF] Kod oluşturuldu ve gönderildi: email={}, purpose={}", email, purpose);
        return true;
    }

    /**
     * Girilen kodu doğrular.
     * Başarılıysa kaydı consumed=true yapar ve true döner.
     * Hatalı/süresi dolmuş ise false döner.
     */
    @Transactional
    public boolean verify(String email, String code, String purpose) {
        Optional<VerificationCode> opt =
                codeRepository.findTopByEmailAndCodeAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        email, code, purpose, Instant.now());

        if (opt.isEmpty()) {
            log.warn("[VERIF] Doğrulama başarısız: email={}, code={}", email, code);
            return false;
        }

        VerificationCode vc = opt.get();
        vc.setConsumed(true);
        codeRepository.save(vc);
        log.info("[VERIF] Doğrulama başarılı: email={}", email);
        return true;
    }

    // ── Yardımcı ───────────────────────────────────────────────────────────────

    private String generateCode() {
        int n = random.nextInt(1_000_000);
        return String.format("%06d", n);
    }
}
