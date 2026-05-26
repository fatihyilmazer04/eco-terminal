-- =============================================================================
-- V22__email_verification.sql
-- Email doğrulama altyapısı:
--   1. users.email_verified kolonu
--   2. verification_codes tablosu
--   3. Mevcut seed kullanıcılarını doğrulanmış işaretle
-- =============================================================================

-- 1. users tablosuna email_verified ekle
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Doğrulama kodları tablosu
CREATE TABLE IF NOT EXISTS verification_codes (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255)  NOT NULL,
    code        VARCHAR(6)    NOT NULL,
    purpose     VARCHAR(20)   NOT NULL DEFAULT 'REGISTER',
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_verif_email_code ON verification_codes(email, code);
CREATE INDEX IF NOT EXISTS idx_verif_email_created ON verification_codes(email, created_at DESC);

-- 3. Mevcut seed kullanıcıları doğrulanmış say (girişleri bozulmasın)
UPDATE users
SET email_verified = TRUE
WHERE email IN (
    'admin@ecoterminal.com',
    'passenger@ecoterminal.com',
    'alice@ecoterminal.com',
    'bob@ecoterminal.com'
);
