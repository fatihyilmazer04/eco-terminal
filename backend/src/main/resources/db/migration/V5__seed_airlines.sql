-- =============================================================================
-- V5__seed_airlines.sql — Havayolları + ek gate bölgeleri
-- =============================================================================

-- Havayolları
INSERT INTO airlines (name, iata_code)
VALUES
    ('Turkish Airlines', 'TK'),
    ('Pegasus Airlines', 'PC'),
    ('SunExpress',       'XQ');

-- 5 farklı uçuş için ek kapı bölgeleri (V3'e ek)
INSERT INTO zones (zone_name, type, max_capacity, critical_threshold, geo_coords, floor_level, status)
VALUES
    ('Gate B2', 'GATE', 280, 0.85, '40.9825,29.0828', 2, 'ACTIVE'),
    ('Gate C3', 'GATE', 320, 0.85, '40.9828,29.0832', 2, 'ACTIVE');

-- Yeni kapılar için IoT kameralar
INSERT INTO iot_devices (zone_id, serial_number, device_type, ip_address, firmware_ver, status, installed_at)
VALUES
    ((SELECT zone_id FROM zones WHERE zone_name = 'Gate B2'),
     'CAM-GATE-B2-001', 'CAMERA', '192.168.10.51', 'v2.4.1', 'ONLINE', NOW()),
    ((SELECT zone_id FROM zones WHERE zone_name = 'Gate C3'),
     'CAM-GATE-C3-001', 'CAMERA', '192.168.10.52', 'v2.4.1', 'ONLINE', NOW());

-- Yeni kapılar için simülasyon yoğunluk verileri (son 2 saat)
INSERT INTO occupancy_readings (zone_id, source_device_id, people_count, density_pct, recorded_at)
SELECT
    z.zone_id,
    d.device_id,
    CAST(ROUND(280 * density) AS INTEGER),
    density,
    NOW() - (offset_min * INTERVAL '1 minute')
FROM (VALUES
    (110, 0.20::FLOAT),(100, 0.22::FLOAT),(90, 0.25::FLOAT),(80, 0.28::FLOAT),
    (70,  0.30::FLOAT),(60,  0.32::FLOAT),(50, 0.35::FLOAT),(40, 0.38::FLOAT),
    (30,  0.40::FLOAT),(20,  0.42::FLOAT),(10, 0.43::FLOAT),(0,  0.40::FLOAT)
) AS v(offset_min, density)
CROSS JOIN (SELECT zone_id FROM zones WHERE zone_name = 'Gate B2') z
CROSS JOIN (SELECT device_id FROM iot_devices WHERE serial_number = 'CAM-GATE-B2-001') d;

INSERT INTO occupancy_readings (zone_id, source_device_id, people_count, density_pct, recorded_at)
SELECT
    z.zone_id,
    d.device_id,
    CAST(ROUND(320 * density) AS INTEGER),
    density,
    NOW() - (offset_min * INTERVAL '1 minute')
FROM (VALUES
    (110, 0.18::FLOAT),(100, 0.20::FLOAT),(90, 0.22::FLOAT),(80, 0.24::FLOAT),
    (70,  0.26::FLOAT),(60,  0.28::FLOAT),(50, 0.30::FLOAT),(40, 0.32::FLOAT),
    (30,  0.35::FLOAT),(20,  0.38::FLOAT),(10, 0.36::FLOAT),(0,  0.35::FLOAT)
) AS v(offset_min, density)
CROSS JOIN (SELECT zone_id FROM zones WHERE zone_name = 'Gate C3') z
CROSS JOIN (SELECT device_id FROM iot_devices WHERE serial_number = 'CAM-GATE-C3-001') d;
