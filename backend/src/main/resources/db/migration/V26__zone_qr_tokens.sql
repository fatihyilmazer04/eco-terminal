-- V26: Zone QR doğrulama token sistemi
-- Her zone'a benzersiz, sabit (deterministik) QR token atanır.
-- Token'lar admin panelinden QR kod olarak görüntülenip yazdırılabilir.
-- Kullanıcılar rota takibinde bu QR'ları okutarak fiziksel varlıklarını kanıtlar.

ALTER TABLE zones ADD COLUMN IF NOT EXISTS qr_token VARCHAR(50);

-- Gate'ler
UPDATE zones SET qr_token = 'GATE-A1-4F9E2C' WHERE zone_name = 'Gate A1';
UPDATE zones SET qr_token = 'GATE-A2-7B3D1A' WHERE zone_name = 'Gate A2';
UPDATE zones SET qr_token = 'GATE-A3-2E8F5B' WHERE zone_name = 'Gate A3';
UPDATE zones SET qr_token = 'GATE-B1-9C4A6D' WHERE zone_name = 'Gate B1';
UPDATE zones SET qr_token = 'GATE-B2-1F7E3B' WHERE zone_name = 'Gate B2';
UPDATE zones SET qr_token = 'GATE-B3-6A2C8E' WHERE zone_name = 'Gate B3';
UPDATE zones SET qr_token = 'GATE-C1-3D5F9A' WHERE zone_name = 'Gate C1';
UPDATE zones SET qr_token = 'GATE-C2-8B1E4C' WHERE zone_name = 'Gate C2';
UPDATE zones SET qr_token = 'GATE-C3-5E7A2F' WHERE zone_name = 'Gate C3';

-- Güvenlik
UPDATE zones SET qr_token = 'SEC1-A3F2B1' WHERE zone_name = 'Security-1';
UPDATE zones SET qr_token = 'SEC2-D8C4E7' WHERE zone_name = 'Security-2';

-- Check-In (büyük/küçük harf varyantlarını kapsıyoruz)
UPDATE zones SET qr_token = 'CI1-F6B3A9' WHERE zone_name ILIKE 'CheckIn-1' OR zone_name ILIKE 'Check-In-1';
UPDATE zones SET qr_token = 'CI2-2E9D5C' WHERE zone_name ILIKE 'CheckIn-2' OR zone_name ILIKE 'Check-In-2';
UPDATE zones SET qr_token = 'CI3-B4A7F1' WHERE zone_name ILIKE 'CheckIn-3' OR zone_name ILIKE 'Check-In-3';

-- Bekleme Salonları
UPDATE zones SET qr_token = 'LNG1-7C4E1B' WHERE zone_name = 'Lounge-1';
UPDATE zones SET qr_token = 'LNG2-9A3F6D' WHERE zone_name = 'Lounge-2';

-- Unique index: iki zone aynı token'a sahip olamaz
CREATE UNIQUE INDEX IF NOT EXISTS idx_zone_qr_token ON zones (qr_token) WHERE qr_token IS NOT NULL;

-- Log: kaç zone'a token atandı?
-- SELECT COUNT(*) FROM zones WHERE qr_token IS NOT NULL;
