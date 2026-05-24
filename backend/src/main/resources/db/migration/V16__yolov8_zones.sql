-- =============================================================================
-- V16__yolov8_zones.sql
-- 1) zones.zone_name için UNIQUE constraint (ON CONFLICT desteği)
-- 2) occupancy_readings.source kolonu (yolov8_simulated / yolov8_live / sensor)
-- 3) Eksik zone'ları ekle (Gate A2-A3, B1-B2, C1-C3, Security-2, Lounge-2, CheckIn-2-3)
-- =============================================================================

-- ── 1. zones.zone_name UNIQUE constraint ─────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'zones_zone_name_key'
          AND conrelid = 'zones'::regclass
    ) THEN
        ALTER TABLE zones ADD CONSTRAINT zones_zone_name_key UNIQUE (zone_name);
    END IF;
END $$;

-- ── 2. occupancy_readings.source kolonu ──────────────────────────────────────
ALTER TABLE occupancy_readings
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) DEFAULT 'sensor';

CREATE INDEX IF NOT EXISTS idx_occupancy_source ON occupancy_readings(source);

-- ── 3. Yeni zone'ları ekle (mevcut zone_name çakışırsa atla) ─────────────────
INSERT INTO zones (zone_name, type, max_capacity, critical_threshold, geo_coords, floor_level, status)
VALUES
    ('Gate A1',   'GATE',     150, 0.85, '40.9823,29.0823', 2, 'ACTIVE'),
    ('Gate A2',   'GATE',     150, 0.85, '40.9825,29.0827', 2, 'ACTIVE'),
    ('Gate A3',   'GATE',     120, 0.85, '40.9827,29.0831', 2, 'ACTIVE'),
    ('Gate B1',   'GATE',     180, 0.85, '40.9820,29.0815', 2, 'ACTIVE'),
    ('Gate B2',   'GATE',     180, 0.85, '40.9822,29.0819', 2, 'ACTIVE'),
    ('Gate C1',   'GATE',     200, 0.85, '40.9818,29.0808', 2, 'ACTIVE'),
    ('Gate C2',   'GATE',     200, 0.85, '40.9816,29.0804', 2, 'ACTIVE'),
    ('Gate C3',   'GATE',     160, 0.85, '40.9814,29.0800', 2, 'ACTIVE'),
    ('Security-1','SECURITY',  80, 0.80, '40.9815,29.0810', 1, 'ACTIVE'),
    ('Security-2','SECURITY',  80, 0.80, '40.9813,29.0806', 1, 'ACTIVE'),
    ('Lounge-1',  'LOUNGE',   300, 0.90, '40.9830,29.0835', 2, 'ACTIVE'),
    ('Lounge-2',  'LOUNGE',   250, 0.90, '40.9832,29.0839', 2, 'ACTIVE'),
    ('CheckIn-1', 'CHECKIN',  100, 0.80, '40.9810,29.0800', 1, 'ACTIVE'),
    ('CheckIn-2', 'CHECKIN',  100, 0.80, '40.9808,29.0796', 1, 'ACTIVE'),
    ('CheckIn-3', 'CHECKIN',  100, 0.80, '40.9806,29.0792', 1, 'ACTIVE')
ON CONFLICT (zone_name) DO NOTHING;
