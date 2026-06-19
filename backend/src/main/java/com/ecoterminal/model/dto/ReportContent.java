package com.ecoterminal.model.dto;

import java.util.List;

/**
 * Yazılı analiz raporu DTO.
 * sections → heading + paragraphs + dataPoints üçlüsünden oluşur;
 * bu yapı hem PDF render'a hem ilerleyen dönemde LLM prompt'a beslenmeye uygundur.
 */
public record ReportContent(
        String title,
        String period,
        String generatedAt,
        List<ReportSection> sections,
        String summaryText
) {
    public record ReportSection(
            String heading,
            List<String> paragraphs,
            List<DataPoint> dataPoints
    ) {}

    public record DataPoint(String label, String value) {}
}
