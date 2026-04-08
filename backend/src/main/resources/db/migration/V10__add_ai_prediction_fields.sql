-- ============================================================
-- V10: ai_predictions tablosuna trend ve confidence kolonları ekle
-- Bu kolonlar Faz 5 (AI entegrasyon) için gerekli
-- ============================================================

ALTER TABLE ai_predictions
    ADD COLUMN IF NOT EXISTS trend      VARCHAR(15) DEFAULT 'STABLE',
    ADD COLUMN IF NOT EXISTS confidence FLOAT       DEFAULT 0.75;
