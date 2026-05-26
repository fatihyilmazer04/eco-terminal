-- V19: Veri saklama sorgularını hızlandırmak için zaman bazlı index'ler
-- DataRetentionScheduler'ın DELETE sorgularını optimize eder

CREATE INDEX IF NOT EXISTS idx_occupancy_readings_recorded_at
    ON occupancy_readings (recorded_at);

CREATE INDEX IF NOT EXISTS idx_environmental_metrics_recorded_at
    ON environmental_metrics (recorded_at);

CREATE INDEX IF NOT EXISTS idx_ai_predictions_generated_at
    ON ai_predictions (generated_at);
