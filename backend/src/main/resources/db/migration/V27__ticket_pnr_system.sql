-- V27: PNR tabanlı bilet sistemi
-- Yolcular PNR koduyla biletlerini hesaplarına ekler.
-- Admin bilet oluşturur → PNR üretilir → yolcu claim eder.

-- 1. user_id'yi nullable yap (claim edilmemiş biletler user'sız olabilir)
ALTER TABLE tickets ALTER COLUMN user_id DROP NOT NULL;

-- 2. Yeni kolonlar ekle
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS pnr_code       VARCHAR(10);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS passenger_name VARCHAR(100);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS ticket_status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- 3. Unique + index
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_ticket_pnr_code'
  ) THEN
    ALTER TABLE tickets ADD CONSTRAINT uq_ticket_pnr_code UNIQUE (pnr_code);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tickets_pnr ON tickets(pnr_code);

-- 4. Mevcut biletlere PNR ata
UPDATE tickets SET
  pnr_code       = 'TK-A3F2B1',
  passenger_name = 'Fatih Yılmazer',
  ticket_status  = 'ACTIVE'
WHERE ticket_id = 1;

UPDATE tickets SET
  pnr_code      = 'PC-X7B2M4',
  ticket_status = 'ACTIVE'
WHERE ticket_id = 2;

-- 5. Demo: claim edilmemiş biletler (user_id = NULL)
INSERT INTO tickets (user_id, flight_id, seat_number, class, is_active, ticket_status, pnr_code, passenger_name)
VALUES
  (NULL, 3, '8A',  'BUSINESS', true, 'ACTIVE', 'TK-X7K2M9', NULL),
  (NULL, 4, '22C', 'ECONOMY',  true, 'ACTIVE', 'PC-M4N5P6', NULL),
  (NULL, 5, '15D', 'ECONOMY',  true, 'ACTIVE', 'TK-R8S9T0', NULL);

-- 6. Kontrol
-- SELECT ticket_id, pnr_code, passenger_name, ticket_status, user_id FROM tickets ORDER BY ticket_id;
