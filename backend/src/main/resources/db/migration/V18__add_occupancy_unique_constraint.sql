-- V18: occupancy_readings tablosuna unique constraint ekle
-- ON CONFLICT (zone_id, recorded_at) DO NOTHING çalışması için gerekli.
-- Mevcut duplicate veriler temizlendikten sonra çalıştırılır.

-- Önce kalan duplicate'leri temizle (idempotent)
DELETE FROM occupancy_readings
WHERE reading_id NOT IN (
    SELECT MIN(reading_id)
    FROM occupancy_readings
    GROUP BY zone_id, recorded_at
);

-- Unique constraint ekle
ALTER TABLE occupancy_readings
    ADD CONSTRAINT uq_occupancy_zone_recorded UNIQUE (zone_id, recorded_at);
