-- =============================================================================
-- V7__seed_tickets.sql — Test kullanıcısına 2 aktif bilet
-- passenger@ecoterminal.com:
--   Bilet 1: TK1234 — 45 dk sonra, Gate A1 — acil (BOARDING)
--   Bilet 2: PC5678 — 90 dk sonra, Gate B2
-- =============================================================================

INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
VALUES
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        (SELECT flight_id FROM flights WHERE flight_code = 'TK1234'),
        '14A',
        'ECONOMY',
        TRUE,
        NOW() - INTERVAL '2 days'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'passenger@ecoterminal.com'),
        (SELECT flight_id FROM flights WHERE flight_code = 'PC5678'),
        '7C',
        'BUSINESS',
        TRUE,
        NOW() - INTERVAL '1 day'
    );

-- alice kullanıcısına da bir bilet (Faz testleri için çoklu kullanıcı)
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, booked_at)
VALUES
    (
        (SELECT user_id FROM users WHERE email = 'alice@ecoterminal.com'),
        (SELECT flight_id FROM flights WHERE flight_code = 'XQ9101'),
        '22B',
        'ECONOMY',
        TRUE,
        NOW() - INTERVAL '3 days'
    );
