-- =============================================================================
-- V1__init.sql — Eco-Terminal veritabanı şeması (3NF)
-- PostgreSQL 15, UTF-8
-- =============================================================================

-- ── 1. IAM (Kimlik ve Güvenlik) ─────────────────────────────────────────────

CREATE TABLE users (
    user_id       BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login    TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE user_profiles (
    profile_id       BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    full_name        VARCHAR(255),
    phone            VARCHAR(20),
    preferences_json JSONB,
    avatar_url       VARCHAR(500),
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE audit_logs (
    log_id        BIGSERIAL PRIMARY KEY,
    actor_id      BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    action_type   VARCHAR(100) NOT NULL,
    target_table  VARCHAR(100),
    target_id     BIGINT,
    old_value     JSONB,
    new_value     JSONB,
    ip_address    VARCHAR(45),
    performed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_performed_at ON audit_logs(performed_at DESC);

-- ── 2. Loyalty & Gamification ────────────────────────────────────────────────

CREATE TABLE eco_wallets (
    wallet_id       BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    current_balance INT          NOT NULL DEFAULT 0,
    tier_level      VARCHAR(20)  NOT NULL DEFAULT 'GREEN' CHECK (tier_level IN ('GREEN', 'GOLD', 'PLATINUM')),
    last_updated    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE reward_catalog (
    reward_id    BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    cost_points  INT          NOT NULL CHECK (cost_points > 0),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE transaction_history (
    trans_id   BIGSERIAL PRIMARY KEY,
    wallet_id  BIGINT NOT NULL REFERENCES eco_wallets(wallet_id) ON DELETE CASCADE,
    reward_id  BIGINT REFERENCES reward_catalog(reward_id) ON DELETE SET NULL,
    amount     INT         NOT NULL,
    trans_type VARCHAR(20) NOT NULL CHECK (trans_type IN ('EARN', 'REDEEM', 'EXPIRE')),
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_history_wallet ON transaction_history(wallet_id);

-- ── 3. Airport Operations ────────────────────────────────────────────────────

CREATE TABLE zones (
    zone_id            BIGSERIAL PRIMARY KEY,
    zone_name          VARCHAR(100) NOT NULL,
    type               VARCHAR(50)  NOT NULL CHECK (type IN ('GATE', 'SECURITY', 'LOUNGE', 'CHECKIN', 'RETAIL', 'OTHER')),
    max_capacity       INT          NOT NULL CHECK (max_capacity > 0),
    critical_threshold FLOAT        NOT NULL DEFAULT 0.85 CHECK (critical_threshold BETWEEN 0 AND 1),
    geo_coords         VARCHAR(100),
    floor_level        INT          DEFAULT 1,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'))
);

CREATE TABLE airlines (
    airline_id BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    iata_code  VARCHAR(3)   NOT NULL UNIQUE
);

CREATE TABLE flights (
    flight_id      BIGSERIAL PRIMARY KEY,
    flight_code    VARCHAR(20)  NOT NULL,
    airline_id     BIGINT REFERENCES airlines(airline_id) ON DELETE SET NULL,
    destination    VARCHAR(100) NOT NULL,
    origin         VARCHAR(100),
    departure_time TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_time   TIMESTAMP WITH TIME ZONE,
    gate_id        BIGINT REFERENCES zones(zone_id) ON DELETE SET NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                   CHECK (status IN ('SCHEDULED', 'BOARDING', 'DEPARTED', 'DELAYED', 'CANCELLED'))
);

CREATE INDEX idx_flights_departure ON flights(departure_time);
CREATE INDEX idx_flights_status ON flights(status);

CREATE TABLE tickets (
    ticket_id     BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    flight_id     BIGINT NOT NULL REFERENCES flights(flight_id) ON DELETE CASCADE,
    seat_number   VARCHAR(10),
    class         VARCHAR(20) NOT NULL DEFAULT 'ECONOMY' CHECK (class IN ('ECONOMY', 'BUSINESS', 'FIRST')),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    check_in_time TIMESTAMP WITH TIME ZONE,
    booked_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tickets_user ON tickets(user_id);
CREATE INDEX idx_tickets_flight ON tickets(flight_id);

-- ── 4. IoT & Devices ─────────────────────────────────────────────────────────

CREATE TABLE iot_devices (
    device_id     BIGSERIAL PRIMARY KEY,
    zone_id       BIGINT REFERENCES zones(zone_id) ON DELETE SET NULL,
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    device_type   VARCHAR(50) NOT NULL CHECK (device_type IN ('CAMERA', 'SENSOR', 'DISPLAY', 'HVAC_CONTROLLER')),
    ip_address    VARCHAR(45),
    firmware_ver  VARCHAR(50),
    status        VARCHAR(20) NOT NULL DEFAULT 'ONLINE' CHECK (status IN ('ONLINE', 'OFFLINE', 'MAINTENANCE')),
    installed_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE maintenance_logs (
    log_id        BIGSERIAL PRIMARY KEY,
    device_id     BIGINT NOT NULL REFERENCES iot_devices(device_id) ON DELETE CASCADE,
    technician_id BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    description   TEXT NOT NULL,
    maint_type    VARCHAR(50) DEFAULT 'ROUTINE',
    maint_date    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ── 5. Analytics & AI ────────────────────────────────────────────────────────

CREATE TABLE occupancy_readings (
    reading_id      BIGSERIAL PRIMARY KEY,
    zone_id         BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    source_device_id BIGINT REFERENCES iot_devices(device_id) ON DELETE SET NULL,
    people_count    INT   NOT NULL CHECK (people_count >= 0),
    density_pct     FLOAT NOT NULL CHECK (density_pct BETWEEN 0 AND 1),
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Zaman serisi sorguları için kritik index
CREATE INDEX idx_occupancy_zone_time ON occupancy_readings(zone_id, recorded_at DESC);
CREATE INDEX idx_occupancy_recorded_at ON occupancy_readings(recorded_at DESC);

CREATE TABLE environmental_metrics (
    metric_id    BIGSERIAL PRIMARY KEY,
    zone_id      BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    energy_kwh   FLOAT NOT NULL CHECK (energy_kwh >= 0),
    temp         FLOAT,
    humidity_pct FLOAT CHECK (humidity_pct BETWEEN 0 AND 100),
    lighting_lux INT   CHECK (lighting_lux >= 0),
    recorded_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_env_metrics_zone_time ON environmental_metrics(zone_id, recorded_at DESC);

CREATE TABLE ai_predictions (
    pred_id        BIGSERIAL PRIMARY KEY,
    zone_id        BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    forecast_time  TIMESTAMP WITH TIME ZONE NOT NULL,
    predicted_load FLOAT NOT NULL CHECK (predicted_load BETWEEN 0 AND 1),
    density_pct    FLOAT NOT NULL CHECK (density_pct BETWEEN 0 AND 1),
    risk_level     VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    model_version  VARCHAR(50) DEFAULT 'v1.0',
    generated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_predictions_zone ON ai_predictions(zone_id, generated_at DESC);
CREATE INDEX idx_ai_predictions_risk ON ai_predictions(risk_level);

CREATE TABLE notifications (
    notif_id   BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    type       VARCHAR(50) NOT NULL CHECK (type IN ('CROWD_ALERT', 'FLIGHT_UPDATE', 'ROUTE_SUGGESTION', 'REWARD', 'SYSTEM')),
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    sent_via   VARCHAR(20) DEFAULT 'IN_APP' CHECK (sent_via IN ('IN_APP', 'FCM', 'BOTH')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
