-- V20: Seed verilerini güncelle
--  1. Uçuş kalkış/varış saatlerini geleceğe taşı (now + X saat)
--  2. bob, fatihyilmazer04, yusuf için bilet ekle
--  3. alice, bob, fatihyilmazer04, yusuf için bildirim ekle
--  4. Eksik eco_wallet kayıtlarını oluştur

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

-- ─── 2. bob (user_id=4) için biletler ───────────────────────────────────────
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT 4, 3, '5A', 'ECONOMY',  true, NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (SELECT 1 FROM tickets WHERE user_id = 4 AND flight_id = 3);

INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT 4, 4, '2C', 'BUSINESS', true, NOW() - INTERVAL '3 days'
WHERE NOT EXISTS (SELECT 1 FROM tickets WHERE user_id = 4 AND flight_id = 4);

-- ─── 3. fatihyilmazer04 (user_id=5) için bilet ─────────────────────────────
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT 5, 2, '12B', 'ECONOMY', true, NOW() - INTERVAL '5 hours'
WHERE NOT EXISTS (SELECT 1 FROM tickets WHERE user_id = 5 AND flight_id = 2);

-- ─── 4. yusuf (user_id=6) için bilet ────────────────────────────────────────
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
SELECT 6, 5, '8D', 'ECONOMY', true, NOW() - INTERVAL '2 hours'
WHERE NOT EXISTS (SELECT 1 FROM tickets WHERE user_id = 6 AND flight_id = 5);

-- ─── 5. alice (user_id=3) için bildirimler ──────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 3, 6, 'Ucusunuz Basliyor', 'XQ9101 ucusunuz 6 saat icinde kalkiyor. Gate C3.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '30 minutes'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 3 AND type = 'FLIGHT_UPDATE');

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 3, 1, 'Gate A1 Yogunluk Uyarisi', 'Gate A1 yogun. Alternatif rota onerilir.', 'CROWD_ALERT', false, 'IN_APP', NOW() - INTERVAL '1 hour'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 3 AND type = 'CROWD_ALERT');

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 3, NULL, 'Eko-Rota Onerisi', 'Security-1 den CheckIn-1 e daha az yogun guzergah mevcut.', 'ROUTE_SUGGESTION', false, 'IN_APP', NOW() - INTERVAL '2 hours'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 3 AND type = 'ROUTE_SUGGESTION');

-- ─── 6. bob (user_id=4) için bildirimler ────────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 4, 6, 'Ucus Hatirlaticisi', 'XQ9101 Antalya ucusunuz 6 saat sonra. Gate C3.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '20 minutes'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 4 AND type = 'FLIGHT_UPDATE');

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 4, NULL, 'Gold Member Olmaya Az Kaldi!', '50 puan daha kazanirsan Gold Member olacaksin!', 'REWARD', true, 'IN_APP', NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 4 AND type = 'REWARD');

-- ─── 7. fatihyilmazer04 (user_id=5) için bildirimler ────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 5, 5, 'Ucus Kapisi: Gate B2', 'PC5678 icin kapi: Gate B2.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '10 minutes'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 5 AND type = 'FLIGHT_UPDATE');

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 5, 2, 'Security-1 Yogun', 'Guvenlik noktasi yogun. Erken gecis onerilir.', 'CROWD_ALERT', false, 'IN_APP', NOW() - INTERVAL '30 minutes'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 5 AND type = 'CROWD_ALERT');

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 5, NULL, 'Eco-Terminal Hos Geldiniz', 'Eko-puan kazanmaya baslayin. Check-in icin 25 puan!', 'SYSTEM', true, 'IN_APP', NOW() - INTERVAL '2 days'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 5 AND type = 'SYSTEM');

-- ─── 8. yusuf (user_id=6) için bildirimler ──────────────────────────────────
INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 6, 5, 'PC1357 Hazirlik', 'Amsterdam ucusunuz 3 saat sonra. Gate B2.', 'FLIGHT_UPDATE', false, 'IN_APP', NOW() - INTERVAL '5 minutes'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 6 AND type = 'FLIGHT_UPDATE');

INSERT INTO notifications (user_id, zone_id, title, body, type, is_read, sent_via, created_at)
SELECT 6, NULL, 'Eco-Terminal Hos Geldiniz', 'Eko-puan kazanarak oduller kazanabilirsiniz!', 'SYSTEM', true, 'IN_APP', NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 6 AND type = 'SYSTEM');

-- ─── 9. Eksik eco_wallet kayıtları ──────────────────────────────────────────
INSERT INTO eco_wallets (user_id, current_balance, tier_level, last_updated)
VALUES
    (5, 0, 'GREEN', NOW()),
    (6, 0, 'GREEN', NOW())
ON CONFLICT (user_id) DO NOTHING;
