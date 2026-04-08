-- V11__add_notification_zone.sql
-- notifications tablosuna zone_id (nullable FK) eklenir
-- Bildirim hangi bölgeyle ilişkili olduğunu takip etmek için

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS zone_id BIGINT REFERENCES zones(zone_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_zone ON notifications(zone_id) WHERE zone_id IS NOT NULL;
