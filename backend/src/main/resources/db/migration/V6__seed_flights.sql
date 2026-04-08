-- =============================================================================
-- V6__seed_flights.sql — 5 simülasyon uçuşu
-- NOW() + interval ile gerçekçi kalkış saatleri (migration anından itibaren)
-- =============================================================================

INSERT INTO flights (flight_code, airline_id, destination, origin, departure_time, arrival_time, gate_id, status)
VALUES
    -- TK1234: 45 dakika sonra, Gate A1 — acil uçuş (turuncu renk)
    (
        'TK1234',
        (SELECT airline_id FROM airlines WHERE iata_code = 'TK'),
        'London Heathrow (LHR)',
        'Istanbul (IST)',
        NOW() + INTERVAL '45 minutes',
        NOW() + INTERVAL '3 hours 45 minutes',
        (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1'),
        'BOARDING'
    ),
    -- PC5678: 90 dakika sonra, Gate B2
    (
        'PC5678',
        (SELECT airline_id FROM airlines WHERE iata_code = 'PC'),
        'Berlin Brandenburg (BER)',
        'Istanbul (IST)',
        NOW() + INTERVAL '90 minutes',
        NOW() + INTERVAL '4 hours',
        (SELECT zone_id FROM zones WHERE zone_name = 'Gate B2'),
        'SCHEDULED'
    ),
    -- XQ9101: 3 saat sonra, Gate C3
    (
        'XQ9101',
        (SELECT airline_id FROM airlines WHERE iata_code = 'XQ'),
        'Antalya (AYT)',
        'Istanbul (IST)',
        NOW() + INTERVAL '3 hours',
        NOW() + INTERVAL '4 hours 30 minutes',
        (SELECT zone_id FROM zones WHERE zone_name = 'Gate C3'),
        'SCHEDULED'
    ),
    -- TK2468: 5 saat sonra, Gate A1
    (
        'TK2468',
        (SELECT airline_id FROM airlines WHERE iata_code = 'TK'),
        'New York JFK (JFK)',
        'Istanbul (IST)',
        NOW() + INTERVAL '5 hours',
        NOW() + INTERVAL '16 hours',
        (SELECT zone_id FROM zones WHERE zone_name = 'Gate A1'),
        'SCHEDULED'
    ),
    -- PC1357: Gecikmeli, Gate B2
    (
        'PC1357',
        (SELECT airline_id FROM airlines WHERE iata_code = 'PC'),
        'Amsterdam Schiphol (AMS)',
        'Istanbul (IST)',
        NOW() + INTERVAL '2 hours 30 minutes',
        NOW() + INTERVAL '5 hours',
        (SELECT zone_id FROM zones WHERE zone_name = 'Gate B2'),
        'DELAYED'
    );
