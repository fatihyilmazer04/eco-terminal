-- =============================================================================
-- V2__seed_users.sql — Test kullanıcıları (BCrypt strength=12)
-- admin123 ve pass123 şifrelerinin BCrypt hash'leri
-- =============================================================================

-- Admin kullanıcısı
INSERT INTO users (email, password_hash, role, is_active, created_at)
VALUES (
    'admin@ecoterminal.com',
    '$2b$12$.pv7ALRSlHEBpc7rGnoP0.SMKLWgxfgVmTPpYQOLFJN6Ze5b974ha',
    'ADMIN',
    TRUE,
    NOW()
);

INSERT INTO user_profiles (user_id, full_name, phone, preferences_json)
VALUES (
    (SELECT user_id FROM users WHERE email = 'admin@ecoterminal.com'),
    'Sistem Yöneticisi',
    '+90-555-000-0001',
    '{"theme": "dark", "notifications": true}'
);

-- Yolcu kullanıcısı
INSERT INTO users (email, password_hash, role, is_active, created_at)
VALUES (
    'passenger@ecoterminal.com',
    '$2b$12$zIOsQm/q9iqr.njNcFIMKeWjJjnQSJ4rgSKGwgCYzFz.BCp9sRq/K',
    'USER',
    TRUE,
    NOW()
);

INSERT INTO user_profiles (user_id, full_name, phone, preferences_json)
VALUES (
    (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
    'Test Yolcu',
    '+90-555-000-0002',
    '{"seatPreference": "WINDOW", "notifications": true, "theme": "dark"}'
);

-- Ekstra test yolcuları
INSERT INTO users (email, password_hash, role, is_active, created_at)
VALUES
    ('alice@ecoterminal.com',
     '$2b$12$zIOsQm/q9iqr.njNcFIMKeWjJjnQSJ4rgSKGwgCYzFz.BCp9sRq/K',
     'USER', TRUE, NOW()),
    ('bob@ecoterminal.com',
     '$2b$12$zIOsQm/q9iqr.njNcFIMKeWjJjnQSJ4rgSKGwgCYzFz.BCp9sRq/K',
     'USER', TRUE, NOW());

INSERT INTO user_profiles (user_id, full_name)
VALUES
    ((SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com'), 'Alice Demir'),
    ((SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com'),   'Bob Yılmaz');

-- Eko cüzdanları oluştur
INSERT INTO eco_wallets (user_id, current_balance, tier_level)
SELECT user_id,
       CASE role WHEN 'ADMIN' THEN 0 ELSE 150 END,
       'GREEN'
FROM users;
