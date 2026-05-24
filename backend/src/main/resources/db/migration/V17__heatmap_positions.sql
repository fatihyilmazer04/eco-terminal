-- =============================================================================
-- V17__heatmap_positions.sql
-- zone_map_positions tablosu: her zone'un harita üzerindeki konumu (0-100% normalize)
-- Bu tablo AirportHeatmap SVG bileşeni tarafından kullanılır.
-- =============================================================================

CREATE TABLE IF NOT EXISTS zone_map_positions (
    id            BIGSERIAL PRIMARY KEY,
    zone_id       BIGINT          NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    pos_x         DOUBLE PRECISION NOT NULL,   -- % (0-100), SVG x koordinatı
    pos_y         DOUBLE PRECISION NOT NULL,   -- % (0-100), SVG y koordinatı
    width         DOUBLE PRECISION NOT NULL,   -- % (0-100), rect genişliği
    height        DOUBLE PRECISION NOT NULL,   -- % (0-100), rect yüksekliği
    section       VARCHAR(50),                 -- CHECKIN, SECURITY, CONCOURSE_A/B/C, LOUNGE
    display_order INT              DEFAULT 0,
    UNIQUE(zone_id)
);

CREATE INDEX IF NOT EXISTS idx_zone_map_positions_zone ON zone_map_positions(zone_id);

-- ── Her zone'un koordinatlarını ekle (zone_name ile JOIN) ────────────────────
-- Koordinatlar 0-100 arası normalize — frontend SVG viewBox'a göre ölçekler
-- Layout: Üst=CheckIn+Security, Orta=Gate'ler (Concourse A/B/C), Alt=Lounge'lar
INSERT INTO zone_map_positions (zone_id, pos_x, pos_y, width, height, section, display_order)
SELECT z.zone_id, v.px, v.py, v.pw, v.ph, v.sec, v.ord
FROM zones z
JOIN (VALUES
    ('CheckIn-1',  5.0,  5.0, 12.0, 12.0, 'CHECKIN',      1),
    ('CheckIn-2', 19.0,  5.0, 12.0, 12.0, 'CHECKIN',      2),
    ('CheckIn-3', 33.0,  5.0, 12.0, 12.0, 'CHECKIN',      3),
    ('Security-1', 50.0,  5.0, 18.0, 12.0, 'SECURITY',    4),
    ('Security-2', 70.0,  5.0, 18.0, 12.0, 'SECURITY',    5),
    ('Gate A1',    5.0, 28.0, 10.0, 16.0, 'CONCOURSE_A',  6),
    ('Gate A2',   17.0, 28.0, 10.0, 16.0, 'CONCOURSE_A',  7),
    ('Gate A3',   29.0, 28.0, 10.0, 16.0, 'CONCOURSE_A',  8),
    ('Gate B1',   42.0, 28.0, 10.0, 16.0, 'CONCOURSE_B',  9),
    ('Gate B2',   54.0, 28.0, 10.0, 16.0, 'CONCOURSE_B', 10),
    ('Gate C1',   67.0, 28.0, 10.0, 16.0, 'CONCOURSE_C', 11),
    ('Gate C2',   79.0, 28.0, 10.0, 16.0, 'CONCOURSE_C', 12),
    ('Gate C3',   90.0, 28.0, 10.0, 16.0, 'CONCOURSE_C', 13),
    ('Lounge-1',   5.0, 55.0, 22.0, 14.0, 'LOUNGE',      14),
    ('Lounge-2',  30.0, 55.0, 22.0, 14.0, 'LOUNGE',      15)
) AS v(zname, px, py, pw, ph, sec, ord) ON z.zone_name = v.zname
ON CONFLICT (zone_id) DO NOTHING;
