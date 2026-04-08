-- =============================================================================
-- V3__seed_zones.sql — Terminal bölgeleri + IoT cihazları
-- =============================================================================

INSERT INTO zones (zone_name, type, max_capacity, critical_threshold, geo_coords, floor_level, status)
VALUES
    ('Gate A1',   'GATE',     300, 0.85, '40.9823,29.0823', 2, 'ACTIVE'),
    ('Security-1','SECURITY', 500, 0.80, '40.9815,29.0810', 1, 'ACTIVE'),
    ('Lounge-1',  'LOUNGE',   120, 0.75, '40.9830,29.0835', 2, 'ACTIVE'),
    ('CheckIn-1', 'CHECKIN',  450, 0.82, '40.9810,29.0800', 1, 'ACTIVE');

-- Her bölge için bir kamera cihazı
INSERT INTO iot_devices (zone_id, serial_number, device_type, ip_address, firmware_ver, status, installed_at)
VALUES
    ((SELECT zone_id FROM zones WHERE zone_name = 'Gate A1'),
     'CAM-GATE-A1-001', 'CAMERA', '192.168.10.11', 'v2.4.1', 'ONLINE', NOW()),

    ((SELECT zone_id FROM zones WHERE zone_name = 'Security-1'),
     'CAM-SEC-1-001', 'CAMERA', '192.168.10.21', 'v2.4.1', 'ONLINE', NOW()),

    ((SELECT zone_id FROM zones WHERE zone_name = 'Lounge-1'),
     'CAM-LOUNGE-1-001', 'CAMERA', '192.168.10.31', 'v2.4.1', 'ONLINE', NOW()),

    ((SELECT zone_id FROM zones WHERE zone_name = 'CheckIn-1'),
     'CAM-CHECKIN-1-001', 'CAMERA', '192.168.10.41', 'v2.4.1', 'ONLINE', NOW());
