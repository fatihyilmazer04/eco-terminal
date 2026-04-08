package com.ecoterminal.model.dto;

/**
 * Saatlik rapor veri noktası.
 * hour: 0-23 arası saat değeri
 * value: yoğunluk (0.0-1.0 arası) veya enerji (kWh)
 * label: "08:00", "09:00" gibi görüntüleme metni
 */
public record HourlyDataPoint(int hour, Float value, String label) {
    public static HourlyDataPoint of(int hour, Float value) {
        return new HourlyDataPoint(hour, value, String.format("%02d:00", hour));
    }
}
