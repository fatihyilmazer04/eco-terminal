-- ============================================================
-- V9: Enerji ve doluluk verilerini sıfırdan doğru değerlerle doldur
-- Test kriterleri:
--   Lounge-1:   energy=25.0 kWh, density=0.15 → WASTEFUL
--   Gate A1:    energy=18.0 kWh, density=0.87 → EFFICIENT
--   Security-1: energy=22.0 kWh, density=0.65 → NORMAL
--   CheckIn-1:  energy=28.0 kWh, density=0.19 → WASTEFUL (0.19 < 0.20)
-- ============================================================

-- Eski verileri temizle (V8 seed'in üzerine yaz)
TRUNCATE TABLE environmental_metrics RESTART IDENTITY;
TRUNCATE TABLE occupancy_readings     RESTART IDENTITY CASCADE;

-- ─────────────────────────────────────────────────────────────
-- OCCUPANCY READINGS — Her bölge için son 6 saat, 10 dk aralık
-- 6 saat × 6 okuma/saat = 36 kayıt/bölge
-- ─────────────────────────────────────────────────────────────

-- Gate A1: density ≈ 0.87 (HIGH) — 300 kişi / cap 300 + variyasyon
INSERT INTO occupancy_readings (zone_id, people_count, density_pct, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1'),
    260 + (gs % 6) * 7,
    (260.0 + (gs % 6) * 7) / 300.0,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Security-1: density ≈ 0.65 (MEDIUM) — 325 kişi / cap 500
INSERT INTO occupancy_readings (zone_id, people_count, density_pct, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Security-1'),
    310 + (gs % 4) * 10,
    (310.0 + (gs % 4) * 10) / 500.0,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Lounge-1: density ≈ 0.15 (LOW) — 18 kişi / cap 120
INSERT INTO occupancy_readings (zone_id, people_count, density_pct, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Lounge-1'),
    16 + (gs % 3),
    (16.0 + (gs % 3)) / 120.0,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- CheckIn-1: density ≈ 0.19 (düşük) — 85 kişi / cap 450
INSERT INTO occupancy_readings (zone_id, people_count, density_pct, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'CheckIn-1'),
    83 + (gs % 3),
    (83.0 + (gs % 3)) / 450.0,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Gate B2: density ≈ 0.40 (LOW-MEDIUM)
INSERT INTO occupancy_readings (zone_id, people_count, density_pct, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate B2'),
    106 + (gs % 5) * 4,
    (106.0 + (gs % 5) * 4) / 280.0,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Gate C3: density ≈ 0.22 (LOW)
INSERT INTO occupancy_readings (zone_id, people_count, density_pct, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate C3'),
    68 + (gs % 4) * 3,
    (68.0 + (gs % 4) * 3) / 320.0,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- ─────────────────────────────────────────────────────────────
-- ENVIRONMENTAL METRICS — Her bölge için son 6 saat, 10 dk aralık
-- Değerler test kriterlerine göre: WASTEFUL ve EFFICIENT eşikleri
-- ─────────────────────────────────────────────────────────────

-- Gate A1: energy_kwh ≈ 18.0, density HIGH → EFFICIENT
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1'),
    17.5 + (gs % 4) * 0.25,
    23.5 + (gs % 3) * 0.5,
    430 + (gs % 3) * 10,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Security-1: energy_kwh ≈ 22.0, density MEDIUM → NORMAL
-- (density 0.65 >= 0.20, so not WASTEFUL even with kwh > 20)
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Security-1'),
    21.5 + (gs % 4) * 0.25,
    21.5 + (gs % 3) * 0.5,
    490 + (gs % 3) * 10,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Lounge-1: energy_kwh ≈ 25.0, density LOW (0.15) → WASTEFUL
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Lounge-1'),
    24.5 + (gs % 4) * 0.25,
    23.0 + (gs % 2) * 0.5,
    350 + (gs % 4) * 10,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- CheckIn-1: energy_kwh ≈ 28.0, density LOW (0.19) → WASTEFUL
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'CheckIn-1'),
    27.5 + (gs % 4) * 0.25,
    22.5 + (gs % 3) * 0.5,
    455 + (gs % 3) * 10,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Gate B2: energy_kwh ≈ 14.0, density 0.40 → NORMAL
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate B2'),
    13.5 + (gs % 4) * 0.25,
    22.0 + (gs % 4) * 0.5,
    405 + (gs % 3) * 10,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;

-- Gate C3: energy_kwh ≈ 12.0, density 0.22 → NORMAL (kWh <= 20)
INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, lighting_lux, recorded_at)
SELECT
    (SELECT zone_id FROM zones WHERE zone_name = 'Gate C3'),
    11.5 + (gs % 4) * 0.25,
    21.5 + (gs % 3) * 0.5,
    320 + (gs % 4) * 10,
    NOW() - (35 - gs) * INTERVAL '10 minutes'
FROM generate_series(0, 35) AS gs;
