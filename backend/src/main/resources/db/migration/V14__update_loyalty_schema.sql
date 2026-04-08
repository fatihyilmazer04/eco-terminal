-- V14__update_loyalty_schema.sql
-- 1) reward_catalog tablosuna reward_type sütunu ekle
-- 2) transaction_history trans_type CHECK kısıtına 'SPEND' ekle

ALTER TABLE reward_catalog
    ADD COLUMN IF NOT EXISTS reward_type VARCHAR(30) DEFAULT 'DISCOUNT';

-- Mevcut CHECK kısıtını kaldırıp yenisini ekle (SPEND dahil)
ALTER TABLE transaction_history
    DROP CONSTRAINT IF EXISTS transaction_history_trans_type_check;

ALTER TABLE transaction_history
    ADD CONSTRAINT transaction_history_trans_type_check
        CHECK (trans_type IN ('EARN', 'REDEEM', 'EXPIRE', 'SPEND'));
