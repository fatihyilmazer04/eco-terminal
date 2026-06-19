-- V20: Seed verilerini güncelle
--  1. Uçuş kalkış/varış saatlerini geleceğe taşı (now + X saat)
--  2. Eksik kullanıcıları oluştur (fatihyilmazer04, yusuf)
--  3. bob, fatihyilmazer04, yusuf için bilet ekle
--  4. alice, bob, fatihyilmazer04, yusuf için bildirim ekle
--  5. Eksik eco_wallet kayıtlarını oluştur
--
-- DÜZELTİLDİ: Hardcoded user_id değerleri subquery ile değiştirildi.
-- Render gibi sıfır DB'de SERIAL ID'leri tahmin edilemez; email bazlı
-- subquery her ortamda doğru kullanıcıyı bulur.

-- ─── 1. Uçuş tarihlerini geleceğe taşı ─────────────────────────────────────
UPDATE flights SET
    departure_time = NOW() + INTERVAL '2 hours',
    arrival_time   = NOW() + INTERVAL '5 hours'
WHERE flight_id = 1;

UPDATE flights SET
    departure_time = NOW() + INTERVAL '4 hours',
    arrival_time   = NOW() + INTERVAL '6 hours 30 minutes'
WHERE flight_id = 2;

UPDATE flights SET
    departure_time = NOW() + INTERVAL '6 hours',
    arrival_time   = NOW() + INTERVAL '7 hours 30 minutes'
WHERE flight_id = 3;

UPDATE flights SET
    departure_time = NOW() + INTERVAL '10 hours',
    arrival_time   = NOW() + INTERVAL '21 hours'
WHERE flight_id = 4;

UPDATE flights SET
    departure_time = NOW() + INTERVAL '3 hours',
    arrival_time   = NOW() + INTERVAL '5 hours 30 minutes'
WHERE flight_id = 5;

-- ─── 2. Eksik kullanıcıları oluştur ─────────────────────────────────────────
-- (V2'de bulunmayan fatihyilmazer04 ve yusuf — ID'leri ortama göre değişir)
INSERT INTO users (email, password_hash, role, is_active, created_at)
VALUES ('fatihyilmazer04@ecoterminal.com',
        '$2b$12$zIOsQm/q9iqr.njNcFIMKeWjJjnQSJ4rgSKGwgCYzFz.BCp9sRq/K',
        'USER', TRUE, NOW())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_profiles (user_id, full_name)
VALUES ((SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com'), 'Fatih Yılmazer')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO users (email, password_hash, role, is_active, created_at)
VALUES ('yusuf@ecoterminal.com',
        '$2b$12$zIOsQm/q9iqr.njNcFIMKeWjJjnQSJ4rgSKGwgCYzFz.BCp9sRq/K',
        'USER', TRUE, NOW())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_profiles (user_id, full_name)
VALUES ((SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com'), 'Yusuf Kaya')
ON CONFLICT (user_id) DO NOTHING;

-- ─── 3. bob için biletler ───────────────────────────────────────────────────
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com'), 3, '5A', 'ECONOMY', true, NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM tickets
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com') AND flight_id = 3
);

INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com'), 4, '2C', 'BUSINESS', true, NOW() - INTERVAL '3 days'
WHERE NOT EXISTS (
    SELECT 1 FROM tickets
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com') AND flight_id = 4
);

-- ─── 4. fatihyilmazer04 için bilet ─────────────────────────────────────────
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com'), 2, '12B', 'ECONOMY', true, NOW() - INTERVAL '5 hours'
WHERE NOT EXISTS (
    SELECT 1 FROM tickets
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com') AND flight_id = 2
);

-- ─── 5. yusuf için bilet ────────────────────────────────────────────────────
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT (SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com'), 5, '8D', 'ECONOMY', true, NOW() - INTERVAL '2 hours'
WHERE NOT EXISTS (
    SELECT 1 FROM tickets
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com') AND flight_id = 5
);

-- ─── 6. alice için bildirimler ──────────────────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com'),
       6, 'Ucusunuz Basliyor', 'XQ9101 ucusunuz 6 saat icinde kalkiyor. Gate C3.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '30 minutes'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com') AND type = 'FLIGHT_UPDATE'
);

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com'),
       1, 'Gate A1 Yogunluk Uyarisi', 'Gate A1 yogun. Alternatif rota onerilir.', 'CROWD_ALERT', false, 'IN_APP', NOW() - INTERVAL '1 hour'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com') AND type = 'CROWD_ALERT'
);

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com'),
       NULL, 'Eko-Rota Onerisi', 'Security-1 den CheckIn-1 e daha az yogun guzergah mevcut.', 'ROUTE_SUGGESTION', false, 'IN_APP', NOW() - INTERVAL '2 hours'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com') AND type = 'ROUTE_SUGGESTION'
);

-- ─── 7. bob için bildirimler ────────────────────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com'),
       6, 'Ucus Hatirlaticisi', 'XQ9101 Antalya ucusunuz 6 saat sonra. Gate C3.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '20 minutes'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com') AND type = 'FLIGHT_UPDATE'
);

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com'),
       NULL, 'Gold Member Olmaya Az Kaldi!', '50 puan daha kazanirsan Gold Member olacaksin!', 'REWARD', true, 'IN_APP', NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'bob@ecoterminal.com') AND type = 'REWARD'
);

-- ─── 8. fatihyilmazer04 için bildirimler ────────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com'),
       5, 'Ucus Kapisi: Gate B2', 'PC5678 icin kapi: Gate B2.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '10 minutes'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com') AND type = 'FLIGHT_UPDATE'
);

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com'),
       2, 'Security-1 Yogun', 'Guvenlik noktasi yogun. Erken gecis onerilir.', 'CROWD_ALERT', false, 'IN_APP', NOW() - INTERVAL '30 minutes'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com') AND type = 'CROWD_ALERT'
);

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com'),
       NULL, 'Eco-Terminal Hos Geldiniz', 'Eko-puan kazanmaya baslayin. Check-in icin 25 puan!', 'SYSTEM', true, 'IN_APP', NOW() - INTERVAL '2 days'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'fatihyilmazer04@ecoterminal.com') AND type = 'SYSTEM'
);

-- ─── 9. yusuf için bildirimler ──────────────────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com'),
       5, 'PC1357 Hazirlik', 'Amsterdam ucusunuz 3 saat sonra. Gate B2.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '5 minutes'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com') AND type = 'FLIGHT_UPDATE'
);

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT (SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com'),
       NULL, 'Eco-Terminal Hos Geldiniz', 'Eko-puan kazanarak oduller kazanabilirsiniz!', 'SYSTEM', true, 'IN_APP', NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM notifications
    WHERE user_id = (SELECT user_id FROM users WHERE email = 'yusuf@ecoterminal.com') AND type = 'SYSTEM'
);

-- ─── 10. Eksik eco_wallet kayıtları ─────────────────────────────────────────
INSERT INTO eco_wallets (user_id, current_balance, tier_level, last_updated)
SELECT user_id, 0, 'GREEN', NOW()
FROM users
WHERE email IN ('fatihyilmazer04@ecoterminal.com', 'yusuf@ecoterminal.com')
ON CONFLICT (user_id) DO NOTHING;
