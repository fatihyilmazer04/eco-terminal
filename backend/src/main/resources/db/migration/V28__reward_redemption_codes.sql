-- V28: Ödül kullanım kodları
-- Her ödül kullanımında benzersiz bir kod üretilip transaction_history'ye kaydedilir.
ALTER TABLE transaction_history ADD COLUMN IF NOT EXISTS redemption_code VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_tx_redemption_code ON transaction_history(redemption_code)
    WHERE redemption_code IS NOT NULL;
