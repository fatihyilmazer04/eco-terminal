-- =============================================================================
-- V21__realistic_terminal_layout.sql
-- zone_map_positions: İstanbul Havalimanı gerçekçi terminal yerleşimi
--
-- Yolcu akışı: Check-in (sol) → Güvenlik → Merkezi Terminal → Gate Konkorsleri (sağ)
-- Koordinat sistemi: posX/posY 0-100 (%), SVG viewBox 1000×600 px ile eşleşir.
--
-- Sadece UPDATE — mevcut satırları günceller, yeni satır eklemez.
-- Idempotent: tekrar çalıştırılabilir.
-- =============================================================================

UPDATE zone_map_positions zmp
SET
    pos_x         = v.px,
    pos_y         = v.py,
    width         = v.pw,
    height        = v.ph,
    section       = v.sec,
    display_order = v.ord
FROM zones z
JOIN (VALUES
    -- ── CHECK-IN ALANI (en sol, posX 3-17) ──────────────────────────────────
    -- 3 kontur, üstten alta sıralı — giriş kapısından doğrudan ulaşılır
    ('CheckIn-1',  3.0, 20.0, 14.0, 18.0, 'Check-in',    1),
    ('CheckIn-2',  3.0, 42.0, 14.0, 18.0, 'Check-in',    2),
    ('CheckIn-3',  3.0, 64.0, 14.0, 18.0, 'Check-in',    3),

    -- ── GÜVENLİK (posX 23-37) ───────────────────────────────────────────────
    -- Security-1 = Ana Güvenlik (üst), Security-2 = VIP Güvenlik (alt)
    ('Security-1', 23.0, 25.0, 14.0, 20.0, 'Security',    4),
    ('Security-2', 23.0, 55.0, 14.0, 20.0, 'Security',    5),

    -- ── MERKEZİ TERMİNAL / LOUNGE (orta, posX 43-57) ───────────────────────
    -- Lounge-1 üst (VIP), Lounge-2 alt (Genel) — aralarında atrium koridoru
    ('Lounge-1',   43.0, 15.0, 14.0, 20.0, 'Terminal',    6),
    ('Lounge-2',   43.0, 62.0, 14.0, 20.0, 'Terminal',    7),

    -- ── A KONKORS (üst sıra, posY 8) ────────────────────────────────────────
    ('Gate A1',    63.0,  8.0, 10.0, 17.0, 'A Concourse',  8),
    ('Gate A2',    75.0,  8.0, 10.0, 17.0, 'A Concourse',  9),
    ('Gate A3',    87.0,  8.0, 10.0, 17.0, 'A Concourse', 10),

    -- ── B KONKORS (orta sıra, posY 40) ──────────────────────────────────────
    ('Gate B1',    63.0, 40.0, 10.0, 17.0, 'B Concourse', 11),
    ('Gate B2',    75.0, 40.0, 10.0, 17.0, 'B Concourse', 12),

    -- ── C KONKORS (alt sıra, posY 72) ───────────────────────────────────────
    ('Gate C1',    63.0, 72.0, 10.0, 17.0, 'C Concourse', 13),
    ('Gate C2',    75.0, 72.0, 10.0, 17.0, 'C Concourse', 14),
    ('Gate C3',    87.0, 72.0, 10.0, 17.0, 'C Concourse', 15)

) AS v(zname, px, py, pw, ph, sec, ord) ON z.zone_name = v.zname
WHERE zmp.zone_id = z.zone_id;
