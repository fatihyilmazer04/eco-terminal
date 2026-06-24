-- V24: Rota tamamlama ödülü tekrar kontrolü için tablo
-- Her (user_id, flight_id) çifti için ROUTE_COMPLETION puanı yalnızca bir kez verilebilir.

CREATE TABLE IF NOT EXISTS route_completions (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(user_id)     ON DELETE CASCADE,
    flight_id     BIGINT NOT NULL REFERENCES flights(flight_id) ON DELETE CASCADE,
    points_earned INT    NOT NULL DEFAULT 50,
    completed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_route_completion UNIQUE (user_id, flight_id)
);

CREATE INDEX IF NOT EXISTS idx_route_completions_user
    ON route_completions (user_id);
