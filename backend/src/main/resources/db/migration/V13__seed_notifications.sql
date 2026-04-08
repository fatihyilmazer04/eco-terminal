-- V13__seed_notifications.sql
-- passenger kullanıcısı için 5 örnek bildirim
-- 3 okunmuş (is_read=TRUE), 2 okunmamış (is_read=FALSE)

INSERT INTO notifications (user_id, title, body, type, is_read, sent_via, zone_id, created_at)
VALUES
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        '⚠ Gate A1 Yoğunluk Uyarısı',
        'Doluluk oranı %92''ye ulaştı. Alternatif rotalar için uygulamayı açın.',
        'CROWD_ALERT',
        TRUE,
        'BOTH',
        (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1' LIMIT 1),
        NOW() - INTERVAL '3 hours'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        '✈ Uçuş Güncelleme',
        'TK-1234 uçuşunuz 20 dakika ertelenmiştir. Yeni kalkış: 14:35.',
        'FLIGHT_UPDATE',
        TRUE,
        'BOTH',
        NULL,
        NOW() - INTERVAL '2 hours'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        '🗺 Yeni Rota Önerisi',
        'Security-1 yoğun. Gate B2 üzerinden alternatif rota: -3 dakika.',
        'ROUTE_SUGGESTION',
        TRUE,
        'IN_APP',
        (SELECT zone_id FROM zones WHERE zone_name = 'Security-1' LIMIT 1),
        NOW() - INTERVAL '1 hour'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        '⚠ Security-1 Yoğunluk Uyarısı',
        'Doluluk oranı %68''e ulaştı. Alternatif rotalar için uygulamayı açın.',
        'CROWD_ALERT',
        FALSE,
        'IN_APP',
        (SELECT zone_id FROM zones WHERE zone_name = 'Security-1' LIMIT 1),
        NOW() - INTERVAL '15 minutes'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        '🔔 Sistem Bildirimi',
        'Eco-Terminal''e hoş geldiniz! Yoğunluk bildirimleriniz aktif.',
        'SYSTEM',
        FALSE,
        'IN_APP',
        NULL,
        NOW() - INTERVAL '5 minutes'
    );
