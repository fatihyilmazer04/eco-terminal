package com.ecoterminal.model.entity;

/**
 * Yoğunluk seviyesi sınıflandırması ve renk kodları.
 * Eşik değerleri OccupancyService.getDensityLevel() tarafından kullanılır.
 */
public enum DensityLevel {

    LOW      ("#2ECC71"),   // 0.00 – 0.59  yeşil
    MEDIUM   ("#F39C12"),   // 0.60 – 0.84  turuncu
    HIGH     ("#E67E22"),   // 0.85 – 0.94  koyu turuncu
    CRITICAL ("#E74C3C");   // 0.95 – 1.00  kırmızı

    private final String colorCode;

    DensityLevel(String colorCode) {
        this.colorCode = colorCode;
    }

    public String getColorCode() {
        return colorCode;
    }

    /** density_pct değerinden seviye hesapla */
    public static DensityLevel of(float pct) {
        if (pct >= 0.95f) return CRITICAL;
        if (pct >= 0.85f) return HIGH;
        if (pct >= 0.60f) return MEDIUM;
        return LOW;
    }
}
