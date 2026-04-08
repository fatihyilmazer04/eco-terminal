-- =============================================================================
-- V4__seed_occupancy.sql — Son 2 saatlik simülasyon yoğunluk verisi
-- Her bölge için 12 kayıt, 10 dakika aralıklı.
-- Son kayıtlar:  Gate A1→0.87(HIGH), Security→0.65(MEDIUM),
--                Lounge→0.30(LOW), CheckIn→0.45(LOW)
-- =============================================================================

-- ── Gate A1 (kapasite 300) — yükselen trend, son değer 0.87 HIGH ──────────
INSERT INTO occupancy_readings (zone_id, source_device_id, people_count, density_pct, recorded_at)
SELECT
    z.zone_id,
    d.device_id,
    CAST(ROUND(300 * density) AS INTEGER),
    density,
    NOW() - (offset_min * INTERVAL '1 minute')
FROM (VALUES
    (110, 0.42::FLOAT),
    (100, 0.45::FLOAT),
    (90,  0.51::FLOAT),
    (80,  0.55::FLOAT),
    (70,  0.59::FLOAT),
    (60,  0.63::FLOAT),
    (50,  0.68::FLOAT),
    (40,  0.73::FLOAT),
    (30,  0.78::FLOAT),
    (20,  0.82::FLOAT),
    (10,  0.85::FLOAT),
    (0,   0.87::FLOAT)
) AS v(offset_min, density)
CROSS JOIN (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1') z
CROSS JOIN (SELECT device_id FROM iot_devices WHERE serial_number = 'CAM-GATE-A1-001') d;

-- ── Security-1 (kapasite 500) — orta seviye, son değer 0.65 MEDIUM ────────
INSERT INTO occupancy_readings (zone_id, source_device_id, people_count, density_pct, recorded_at)
SELECT
    z.zone_id,
    d.device_id,
    CAST(ROUND(500 * density) AS INTEGER),
    density,
    NOW() - (offset_min * INTERVAL '1 minute')
FROM (VALUES
    (110, 0.55::FLOAT),
    (100, 0.57::FLOAT),
    (90,  0.59::FLOAT),
    (80,  0.60::FLOAT),
    (70,  0.61::FLOAT),
    (60,  0.62::FLOAT),
    (50,  0.62::FLOAT),
    (40,  0.63::FLOAT),
    (30,  0.64::FLOAT),
    (20,  0.64::FLOAT),
    (10,  0.65::FLOAT),
    (0,   0.65::FLOAT)
) AS v(offset_min, density)
CROSS JOIN (SELECT zone_id FROM zones WHERE zone_name = 'Security-1') z
CROSS JOIN (SELECT device_id FROM iot_devices WHERE serial_number = 'CAM-SEC-1-001') d;

-- ── Lounge-1 (kapasite 120) — sakin bölge, son değer 0.30 LOW ─────────────
INSERT INTO occupancy_readings (zone_id, source_device_id, people_count, density_pct, recorded_at)
SELECT
    z.zone_id,
    d.device_id,
    CAST(ROUND(120 * density) AS INTEGER),
    density,
    NOW() - (offset_min * INTERVAL '1 minute')
FROM (VALUES
    (110, 0.35::FLOAT),
    (100, 0.33::FLOAT),
    (90,  0.32::FLOAT),
    (80,  0.34::FLOAT),
    (70,  0.31::FLOAT),
    (60,  0.30::FLOAT),
    (50,  0.32::FLOAT),
    (40,  0.29::FLOAT),
    (30,  0.31::FLOAT),
    (20,  0.30::FLOAT),
    (10,  0.29::FLOAT),
    (0,   0.30::FLOAT)
) AS v(offset_min, density)
CROSS JOIN (SELECT zone_id FROM zones WHERE zone_name = 'Lounge-1') z
CROSS JOIN (SELECT device_id FROM iot_devices WHERE serial_number = 'CAM-LOUNGE-1-001') d;

-- ── CheckIn-1 (kapasite 450) — düşen trend, son değer 0.45 LOW ───────────
INSERT INTO occupancy_readings (zone_id, source_device_id, people_count, density_pct, recorded_at)
SELECT
    z.zone_id,
    d.device_id,
    CAST(ROUND(450 * density) AS INTEGER),
    density,
    NOW() - (offset_min * INTERVAL '1 minute')
FROM (VALUES
    (110, 0.62::FLOAT),
    (100, 0.60::FLOAT),
    (90,  0.58::FLOAT),
    (80,  0.55::FLOAT),
    (70,  0.53::FLOAT),
    (60,  0.52::FLOAT),
    (50,  0.50::FLOAT),
    (40,  0.49::FLOAT),
    (30,  0.48::FLOAT),
    (20,  0.47::FLOAT),
    (10,  0.46::FLOAT),
    (0,   0.45::FLOAT)
) AS v(offset_min, density)
CROSS JOIN (SELECT zone_id FROM zones WHERE zone_name = 'CheckIn-1') z
CROSS JOIN (SELECT device_id FROM iot_devices WHERE serial_number = 'CAM-CHECKIN-1-001') d;
