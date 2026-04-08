-- ============================================================
-- V8: Çevresel metrikler seed
-- Her bölge için son 24 saatlik enerji/sıcaklık/nem verileri
-- Saat başı okumalar — toplam 24 kayıt x zone sayısı
-- Kolon adları V1 şemasına göre: temp, humidity_pct, lighting_lux
-- ============================================================

-- Gate A1 — yüksek yoğunluk → yüksek enerji tüketimi
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1'),
    CASE (gs % 6)
        WHEN 0 THEN 4.8
        WHEN 1 THEN 5.2
        WHEN 2 THEN 6.1
        WHEN 3 THEN 7.3
        WHEN 4 THEN 6.8
        ELSE 5.5
    END,
    22.5 + (gs % 4) * 0.5,
    58.0 + (gs % 5),
    420 + (gs % 3) * 15,
    NOW() - (23 - gs) * INTERVAL '1 hour'
FROM generate_series(0, 23) AS gs;

-- Security-1 — orta yoğunluk, güvenlik ekipmanları enerji tüketir
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Security-1'),
    CASE (gs % 6)
        WHEN 0 THEN 8.2
        WHEN 1 THEN 9.1
        WHEN 2 THEN 8.7
        WHEN 3 THEN 9.5
        WHEN 4 THEN 8.9
        ELSE 8.4
    END,
    21.0 + (gs % 3) * 0.5,
    55.0 + (gs % 4),
    500 + (gs % 3) * 10,
    NOW() - (23 - gs) * INTERVAL '1 hour'
FROM generate_series(0, 23) AS gs;

-- Lounge-1 — düşük yoğunluk, konfor için klima + ışık aktif
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Lounge-1'),
    CASE (gs % 6)
        WHEN 0 THEN 3.1
        WHEN 1 THEN 3.4
        WHEN 2 THEN 3.2
        WHEN 3 THEN 3.8
        WHEN 4 THEN 3.5
        ELSE 3.0
    END,
    23.0 + (gs % 2) * 0.5,
    50.0 + (gs % 3),
    350 + (gs % 4) * 10,
    NOW() - (23 - gs) * INTERVAL '1 hour'
FROM generate_series(0, 23) AS gs;

-- CheckIn-1 — düşük-orta yoğunluk, self check-in kiosklar enerji çeker
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'CheckIn-1'),
    CASE (gs % 6)
        WHEN 0 THEN 5.5
        WHEN 1 THEN 6.0
        WHEN 2 THEN 5.8
        WHEN 3 THEN 6.3
        WHEN 4 THEN 5.9
        ELSE 5.6
    END,
    22.0 + (gs % 3) * 0.5,
    57.0 + (gs % 4),
    460 + (gs % 3) * 10,
    NOW() - (23 - gs) * INTERVAL '1 hour'
FROM generate_series(0, 23) AS gs;

-- Gate B2 — orta yoğunluk
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate B2'),
    CASE (gs % 6)
        WHEN 0 THEN 4.2
        WHEN 1 THEN 4.6
        WHEN 2 THEN 5.0
        WHEN 3 THEN 5.4
        WHEN 4 THEN 4.9
        ELSE 4.3
    END,
    22.0 + (gs % 4) * 0.5,
    56.0 + (gs % 5),
    400 + (gs % 3) * 15,
    NOW() - (23 - gs) * INTERVAL '1 hour'
FROM generate_series(0, 23) AS gs;

-- Gate C3 — düşük yoğunluk, enerji tasarrufu modu
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate C3'),
    CASE (gs % 6)
        WHEN 0 THEN 2.8
        WHEN 1 THEN 3.0
        WHEN 2 THEN 2.9
        WHEN 3 THEN 3.2
        WHEN 4 THEN 3.1
        ELSE 2.7
    END,
    21.5 + (gs % 3) * 0.5,
    54.0 + (gs % 4),
    320 + (gs % 4) * 10,
    NOW() - (23 - gs) * INTERVAL '1 hour'
FROM generate_series(0, 23) AS gs;
