-- V15__seed_loyalty.sql
-- Passenger cüzdanını 450 puana güncelle + işlem geçmişi + ödül kataloğu

-- Passenger bakiyesini 450'ye güncelle
UPDATE eco_wallets
SET current_balance = 450,
    tier_level      = 'GREEN',
    last_updated    = NOW()
WHERE user_id = (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com');

-- 5 işlem kaydı (3 EARN, 2 SPEND)
INSERT INTO transaction_history (wallet_id, amount, trans_type, description, created_at)
VALUES
    (
        (SELECT w.wallet_id FROM eco_wallets w
         JOIN users u ON w.user_id = u.user_id
         WHERE u.email = 'passenger@ecoterminal.com'),
        25, 'EARN', 'Check-in tamamlandı', NOW() - INTERVAL '7 days'
    ),
    (
        (SELECT w.wallet_id FROM eco_wallets w
         JOIN users u ON w.user_id = u.user_id
         WHERE u.email = 'passenger@ecoterminal.com'),
        15, 'EARN', 'Eco rota kullanıldı', NOW() - INTERVAL '5 days'
    ),
    (
        (SELECT w.wallet_id FROM eco_wallets w
         JOIN users u ON w.user_id = u.user_id
         WHERE u.email = 'passenger@ecoterminal.com'),
        100, 'SPEND', 'Kahve İkramı ödülü kullanıldı', NOW() - INTERVAL '4 days'
    ),
    (
        (SELECT w.wallet_id FROM eco_wallets w
         JOIN users u ON w.user_id = u.user_id
         WHERE u.email = 'passenger@ecoterminal.com'),
        10, 'EARN', 'Sakin alanda bekleme yapıldı', NOW() - INTERVAL '2 days'
    ),
    (
        (SELECT w.wallet_id FROM eco_wallets w
         JOIN users u ON w.user_id = u.user_id
         WHERE u.email = 'passenger@ecoterminal.com'),
        50, 'SPEND', '%10 Mağaza İndirimi ödülü kullanıldı', NOW() - INTERVAL '1 day'
    );

-- 4 ödül kataloğu kaydı
INSERT INTO reward_catalog (title, description, cost_points, reward_type, is_active)
VALUES
    ('Lounge Girişi',        'Premium lounge alanına ücretsiz giriş hakkı', 200, 'LOUNGE_ACCESS', TRUE),
    ('Kahve İkramı',         'Terminal kafelerinden ücretsiz 1 kahve',       100, 'COFFEE',        TRUE),
    ('%10 Mağaza İndirimi',  'Terminal mağazalarında %10 indirim kuponu',    150, 'DISCOUNT',       TRUE),
    ('Koltuk Yükseltme',     'Economy''den Business sınıfına koltuk yükseltme', 400, 'UPGRADE',   TRUE);
