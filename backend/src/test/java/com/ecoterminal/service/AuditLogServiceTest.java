package com.ecoterminal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService Unit Tests")
class AuditLogServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AuditLogService auditLogService;

    // ── log Tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("log_withValidParams_executesJdbcUpdate")
    void log_withValidParams_executesJdbcUpdate() {
        // given
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        // when
        auditLogService.log(1L, "REDIRECT", "zones", 10L,
                "{\"from\":\"A1\"}", "{\"to\":\"B3\"}");

        // then
        verify(jdbcTemplate).update(anyString(),
                eq(1L), eq("REDIRECT"), eq("zones"), eq(10L),
                eq("{\"from\":\"A1\"}"), eq("{\"to\":\"B3\"}"));
    }

    @Test
    @DisplayName("log_withNullValues_executesWithoutException")
    void log_withNullValues_executesWithoutException() {
        // given
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        // when — oldValue ve newValue null
        auditLogService.log(1L, "ENERGY_SETTING", "environmental_metrics", 5L, null, null);

        // then — hata fırlatılmamalı
        verify(jdbcTemplate).update(anyString(),
                eq(1L), eq("ENERGY_SETTING"), eq("environmental_metrics"), eq(5L),
                isNull(), isNull());
    }

    @Test
    @DisplayName("log_whenJdbcThrowsException_doesNotPropagateError")
    void log_whenJdbcThrowsException_doesNotPropagateError() {
        // given — JDBC hata fırlatıyor
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB bağlantı hatası"));

        // when — hata dışarı çıkmamalı (audit log hatası uygulamayı durdurmamalı)
        auditLogService.log(1L, "REDIRECT", "zones", 10L, null, null);

        // then — exception propagate edilmemiş
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    // ── logSystem Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("logSystem_callsJdbcWithNullActorId")
    void logSystem_callsJdbcWithNullActorId() {
        // given
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        // when
        auditLogService.logSystem("DATA_RETENTION", "occupancy_readings",
                "{\"deleted_rows\":150}");

        // then — actor_id null olmalı
        verify(jdbcTemplate).update(anyString(),
                isNull(),                    // actor_id
                eq("DATA_RETENTION"),
                eq("occupancy_readings"),
                isNull(),                    // target_id
                isNull(),                    // old_value
                eq("{\"deleted_rows\":150}") // new_value / details
        );
    }

    @Test
    @DisplayName("logSystem_whenJdbcThrowsException_doesNotPropagateError")
    void logSystem_whenJdbcThrowsException_doesNotPropagateError() {
        // given
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("timeout"));

        // when — sistem log hatası uygulamayı durdurmamalı
        auditLogService.logSystem("SYSTEM_CLEANUP", "ai_predictions", null);

        // then
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
    }
}
