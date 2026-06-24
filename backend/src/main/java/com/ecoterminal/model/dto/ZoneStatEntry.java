package com.ecoterminal.model.dto;

/** Rapor özeti için bölge bazlı istatistik (yoğunluk veya enerji). */
public record ZoneStatEntry(String zoneName, double value) {}
