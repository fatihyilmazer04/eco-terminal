package com.ecoterminal.service;

import com.ecoterminal.model.entity.VerificationCode;
import com.ecoterminal.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationService Unit Tests")
class VerificationServiceTest {

    @Mock private VerificationCodeRepository codeRepository;
    @Mock private EmailService               emailService;

    @InjectMocks
    private VerificationService verificationService;

    private VerificationCode validCode;

    @BeforeEach
    void setUp() {
        validCode = VerificationCode.builder()
                .id(1L)
                .email("user@eco.com")
                .code("123456")
                .purpose("REGISTER")
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .consumed(false)
                .createdAt(Instant.now().minus(30, ChronoUnit.SECONDS))
                .build();
    }

    // ── generateAndSend Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("generateAndSend_withNoCooldown_savesCodeAndSendsEmail")
    void generateAndSend_withNoCooldown_savesCodeAndSendsEmail() {
        // given — son 60 sn'de kod yok
        when(codeRepository
                .findTopByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq("user@eco.com"), eq("REGISTER"), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(codeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = verificationService.generateAndSend("user@eco.com", "REGISTER");

        // then
        assertThat(result).isTrue();
        verify(codeRepository).save(any(VerificationCode.class));
        verify(emailService).sendVerificationCode(eq("user@eco.com"), anyString());
    }

    @Test
    @DisplayName("generateAndSend_withinCooldown_returnsFalse")
    void generateAndSend_withinCooldown_returnsFalse() {
        // given — son 30 sn'de gönderilmiş (cooldown 60 sn)
        VerificationCode recentCode = VerificationCode.builder()
                .email("user@eco.com")
                .code("654321")
                .purpose("REGISTER")
                .consumed(false)
                .expiresAt(Instant.now().plus(9, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(30, ChronoUnit.SECONDS)) // 30 sn önce
                .build();

        when(codeRepository
                .findTopByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq("user@eco.com"), eq("REGISTER"), any(Instant.class)))
                .thenReturn(Optional.of(recentCode));

        // when
        boolean result = verificationService.generateAndSend("user@eco.com", "REGISTER");

        // then
        assertThat(result).isFalse();
        verify(codeRepository, never()).save(any());
        verify(emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("generateAndSend_forPasswordReset_callsPasswordResetEmail")
    void generateAndSend_forPasswordReset_callsPasswordResetEmail() {
        // given
        when(codeRepository
                .findTopByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());
        when(codeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        verificationService.generateAndSend("user@eco.com", "PASSWORD_RESET");

        // then — password reset için farklı email metodu çağrılmalı
        verify(emailService).sendPasswordResetCode(eq("user@eco.com"), anyString());
        verify(emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("generateAndSend_generatesExactly6DigitCode")
    void generateAndSend_generatesExactly6DigitCode() {
        // given
        when(codeRepository
                .findTopByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());
        when(codeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);

        // when
        verificationService.generateAndSend("user@eco.com", "REGISTER");

        // then
        verify(codeRepository).save(captor.capture());
        String code = captor.getValue().getCode();
        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
    }

    // ── verify Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("verify_withValidCode_returnsTrue_andMarksConsumed")
    void verify_withValidCode_returnsTrue_andMarksConsumed() {
        // given
        when(codeRepository
                .findTopByEmailAndCodeAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq("user@eco.com"), eq("123456"), eq("REGISTER"), any(Instant.class)))
                .thenReturn(Optional.of(validCode));
        when(codeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = verificationService.verify("user@eco.com", "123456", "REGISTER");

        // then
        assertThat(result).isTrue();
        assertThat(validCode.getConsumed()).isTrue();
        verify(codeRepository).save(validCode);
    }

    @Test
    @DisplayName("verify_withWrongCode_returnsFalse")
    void verify_withWrongCode_returnsFalse() {
        // given
        when(codeRepository
                .findTopByEmailAndCodeAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq("user@eco.com"), eq("000000"), eq("REGISTER"), any(Instant.class)))
                .thenReturn(Optional.empty());

        // when
        boolean result = verificationService.verify("user@eco.com", "000000", "REGISTER");

        // then
        assertThat(result).isFalse();
        verify(codeRepository, never()).save(any());
    }

    @Test
    @DisplayName("verify_withExpiredCode_returnsFalse")
    void verify_withExpiredCode_returnsFalse() {
        // given — repository süresi dolmuş kodu döndürmez (expiresAt filtresi)
        when(codeRepository
                .findTopByEmailAndCodeAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        // when
        boolean result = verificationService.verify("user@eco.com", "123456", "REGISTER");

        // then
        assertThat(result).isFalse();
    }
}
