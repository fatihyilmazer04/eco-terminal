package com.ecoterminal.service;

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
@DisplayName("DataRetentionScheduler Unit Tests")
class DataRetentionSchedulerTest {

    @Mock private JdbcTemplate    jdbcTemplate;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private DataRetentionScheduler dataRetentionScheduler;

    // ── runRetention Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("runRetention_deletesOccupancyEnergryAndPredictionRecords")
    void runRetention_deletesOccupancyEnergryAndPredictionRecords() {
        // given — her tablo için silme işlemi başarılı
        when(jdbcTemplate.update(contains("occupancy_readings"), (Object[]) any())).thenReturn(150);
        when(jdbcTemplate.update(contains("environmental_metrics"), (Object[]) any())).thenReturn(80);
        when(jdbcTemplate.update(contains("ai_predictions"), (Object[]) any())).thenReturn(200);

        // when
        dataRetentionScheduler.runRetention();

        // then — 3 tablodan silme yapıldı
        verify(jdbcTemplate, times(3)).update(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("runRetention_logsSystemAuditWithSummary")
    void runRetention_logsSystemAuditWithSummary() {
        // given
        when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(50);

        // when
        dataRetentionScheduler.runRetention();

        // then — sistem audit logu yazılmalı
        verify(auditLogService).logSystem(
                eq("DATA_RETENTION"),
                contains("occupancy_readings"),
                anyString()
        );
    }

    @Test
    @DisplayName("runRetention_withNoOldData_deletesZeroRows")
    void runRetention_withNoOldData_deletesZeroRows() {
        // given — silinecek kayıt yok
        when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(0);

        // when
        dataRetentionScheduler.runRetention();

        // then — yine de loglanmalı (0 silme)
        verify(auditLogService).logSystem(anyString(), anyString(), contains("\"occupancy_deleted\":0"));
    }

    @Test
    @DisplayName("runRetention_whenJdbcFails_doesNotCatchException")
    void runRetention_whenJdbcFails_propagatesException() {
        // given — DB hatası
        when(jdbcTemplate.update(anyString(), (Object[]) any()))
                .thenThrow(new org.springframework.dao.DataAccessException("DB hatası") {});

        // when / then — exception propagate edilmeli (caller @Transactional yönetir)
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> dataRetentionScheduler.runRetention()
        );
    }
}
