-- =============================================================================
-- V29__update_zone_capacities.sql
-- Tüm bölgelerin maksimum kapasitesini 70 olarak günceller.
-- =============================================================================

UPDATE zones
SET max_capacity = 70;
