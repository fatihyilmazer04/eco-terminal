package com.ecoterminal.repository;

import com.ecoterminal.model.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /** En son gönderilen geçerli (tüketilmemiş, süresi dolmamış) kod */
    Optional<VerificationCode> findTopByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, String purpose, Instant now);

    /** Belirli email+code eşleşmesi — doğrulama için */
    Optional<VerificationCode> findTopByEmailAndCodeAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, String code, String purpose, Instant now);
}
