package com.ecoterminal.service;

import com.ecoterminal.model.dto.ReportContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportNarrativeService Unit Tests")
class ReportNarrativeServiceTest {

    @Mock private JdbcTemplate jdbc;

    @InjectMocks
    private ReportNarrativeService reportNarrativeService;

    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END   = LocalDate.of(2025, 1, 31);

    // ── generateReport Route Tests ────────────────────────────────────────────

    @Test
    @DisplayName("generateReport_withUnknownType_returnsErrorReport")
    void generateReport_withUnknownType_returnsErrorReport() {
        // when
        ReportContent result = reportNarrativeService.generateReport("UNKNOWN_TYPE", START, END);

        // then — hata raporu döner, exception fırlatılmaz
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Bilinmeyen Rapor Türü");
        assertThat(result.sections()).isNotEmpty();
        assertThat(result.sections().get(0).heading()).isEqualTo("Hata");
    }

    @Test
    @DisplayName("generateReport_withUserRegistrationType_doesNotThrow")
    void generateReport_withUserRegistrationType_doesNotThrow() {
        // given — JDBC sorguları dummy data döndürür
        when(jdbc.queryForMap(anyString())).thenReturn(
                Map.of("total", 100L, "admin_cnt", 5L, "pass_cnt", 95L, "verified_rate", 80.0)
        );
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(10L);
        when(jdbc.queryForList(anyString(), (Object[]) any())).thenReturn(List.of());

        // when — exception fırlatmamalı
        ReportContent result = reportNarrativeService.generateReport("USER_REGISTRATION", START, END);

        // then
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Kullanıcı Kayıt Analizi");
    }

    @Test
    @DisplayName("generateReport_whenJdbcFails_returnsErrorReport")
    void generateReport_whenJdbcFails_returnsErrorReport() {
        // given — JDBC hata fırlatıyor
        when(jdbc.queryForMap(anyString())).thenThrow(new RuntimeException("DB bağlantı hatası"));

        // when — exception yutulmalı, hata raporu dönmeli
        ReportContent result = reportNarrativeService.generateReport("USER_REGISTRATION", START, END);

        // then
        assertThat(result).isNotNull();
        assertThat(result.sections()).isNotEmpty();
    }

    @Test
    @DisplayName("generateReport_caseInsensitive_routesCorrectly")
    void generateReport_caseInsensitive_routesCorrectly() {
        // given
        when(jdbc.queryForMap(anyString())).thenReturn(
                Map.of("total", 0L, "admin_cnt", 0L, "pass_cnt", 0L, "verified_rate", 0.0)
        );
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
        when(jdbc.queryForList(anyString(), (Object[]) any())).thenReturn(List.of());

        // when — büyük harf + trim (baştaki/sondaki boşluklar temizlenmeli)
        ReportContent result = reportNarrativeService.generateReport("  USER_REGISTRATION  ", START, END);

        // then
        assertThat(result.title()).isEqualTo("Kullanıcı Kayıt Analizi");
    }

    @Test
    @DisplayName("generateReport_withEnergySavingsType_doesNotThrow")
    void generateReport_withEnergySavingsType_doesNotThrow() {
        // given — tasarruf adayı yok
        when(jdbc.queryForList(anyString(), any(), any(), any(), any())).thenReturn(List.of());

        // when
        ReportContent result = reportNarrativeService.generateReport("ENERGY_SAVINGS", START, END);

        // then
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Enerji Tasarruf Analizi");
    }

    @Test
    @DisplayName("generateReport_withOccupancyGeneralType_doesNotThrow")
    void generateReport_withOccupancyGeneralType_doesNotThrow() {
        // given — DB boş veri döndürür
        when(jdbc.queryForObject(anyString(), eq(Double.class), any())).thenReturn(0.0);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
        when(jdbc.queryForList(anyString(), (Object[]) any())).thenReturn(List.of());

        // when
        ReportContent result = reportNarrativeService.generateReport("OCCUPANCY_GENERAL", START, END);

        // then
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Yoğunluk Genel Raporu");
    }
}
