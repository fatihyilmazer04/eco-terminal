-- =============================================================================
-- V30__historical_report_data.sql
-- Geçen ay ve bu ay raporlarının çalışması için gerçekçi tarihsel veri ekler.
-- Tüm aktif zone'lar için son 70 gün, 2 saatte bir okuma.
-- =============================================================================

-- ── OCCUPANCY READINGS — Son 70 gün, 2 saatte bir ───────────────────────────

INSERT INTO occupancy_readings (zone_id, people_count, density_pct, source, recorded_at)
SELECT
    z.zone_id,
    GREATEST(1, CAST(ROUND(70.0 * density) AS INTEGER)),
    density,
    'simulator',
    ts
FROM generate_series(
    NOW() - INTERVAL '70 days',
    NOW() - INTERVAL '2 hours',
    INTERVAL '2 hours'
) AS ts
CROSS JOIN (SELECT zone_id FROM zones WHERE status = 'ACTIVE') z
CROSS JOIN LATERAL (
    SELECT LEAST(0.95, GREATEST(0.05,
        -- Günlük ritim: öğleden önce ve öğleden sonra yoğun, gece sakin
        0.15
        + 0.55 * GREATEST(0.0, SIN((EXTRACT(HOUR FROM ts) - 6) * PI() / 12.0))
        -- Zone bazlı varyasyon (deterministik "rastgelelik")
        + 0.15 * ABS(SIN(CAST(EXTRACT(EPOCH FROM ts) AS FLOAT) / 3600.0
                         + z.zone_id * 1.7))
    )) AS density
) AS calc
ON CONFLICT DO NOTHING;

-- ── ENVIRONMENTAL METRICS — Son 70 gün, 2 saatte bir ────────────────────────

INSERT INTO environmental_metrics (zone_id, energy_kwh, temp, humidity_pct, lighting_lux, recorded_at)
SELECT
    z.zone_id,
    -- Enerji tüketimi: gündüz daha yüksek, gece düşük (5-17 kWh)
    ROUND(CAST(
        5.0
        + 10.0 * GREATEST(0.0, SIN((EXTRACT(HOUR FROM ts) - 6) * PI() / 12.0))
        + 2.0  * ABS(SIN(z.zone_id * 0.9 + EXTRACT(DOY FROM ts) * 0.1))
    AS NUMERIC), 2),
    -- Sıcaklık 20-25°C
    ROUND(CAST(
        22.0 + 2.5 * SIN(EXTRACT(HOUR FROM ts) * PI() / 12.0)
    AS NUMERIC), 1),
    -- Nem %42-58
    ROUND(CAST(
        50.0 + 7.0 * SIN(z.zone_id + EXTRACT(DOY FROM ts) * 0.07)
    AS NUMERIC), 1),
    -- Aydınlatma: gündüz 500-800 lux, gece 100-200 lux
    CAST(ROUND(
        150.0
        + 650.0 * GREATEST(0.0, SIN((EXTRACT(HOUR FROM ts) - 6) * PI() / 12.0))
    ) AS INTEGER),
    ts
FROM generate_series(
    NOW() - INTERVAL '70 days',
    NOW() - INTERVAL '2 hours',
    INTERVAL '2 hours'
) AS ts
CROSS JOIN (SELECT zone_id FROM zones WHERE status = 'ACTIVE') z
ON CONFLICT DO NOTHING;
