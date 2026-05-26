-- =============================================================================
-- V23__route_checkins.sql
-- Rota adım bazlı check-in kayıtları (rota tamamlama doğrulama altyapısı)
-- =============================================================================

CREATE TABLE IF NOT EXISTS route_checkins (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    flight_id     BIGINT NOT NULL REFERENCES flights(flight_id) ON DELETE CASCADE,
    step_number   INT    NOT NULL,
    zone_name     VARCHAR(100) NOT NULL,
    total_steps   INT    NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Bir kullanıcı + uçuş + adım kombinasyonu unique: aynı adımı 2 kez check-in yapılamaz
CREATE UNIQUE INDEX IF NOT EXISTS idx_route_checkin_unique
    ON route_checkins(user_id, flight_id, step_number);

-- Tamamlanma sorgusu için
CREATE INDEX IF NOT EXISTS idx_route_checkin_user_flight
    ON route_checkins(user_id, flight_id);
