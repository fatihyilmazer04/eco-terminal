# Eco-Terminal AI Service

Flask REST API — port 5000. Spring Boot backend tarafından her 5 dakikada bir çağrılır.

## Endpoint'ler

| Endpoint | Metot | Açıklama |
|----------|-------|----------|
| `/predict?zone_id=X&next_minutes=30` | GET | Tek zone tahmini |
| `/predict/all?next_minutes=30` | GET | Tüm aktif zone'lar |
| `/health` | GET | Servis + aktif model durumu |
| `/analyze/crowd` | GET | Kalabalık analizi |
| `/energy/recommendations` | GET | Enerji önerileri |

### Response örneği (`/predict`)

```json
{
  "zone_id": 1,
  "zone_name": "Gate A1",
  "forecast_time": "2026-05-29T16:36:00",
  "predicted_load": 0.4823,
  "density_pct": 0.4823,
  "risk_level": "MEDIUM",
  "trend": "STABLE",
  "confidence": 0.95,
  "generated_at": "2026-05-29T16:06:00"
}
```

### `/health` response

```json
{
  "status": "ok",
  "model": "xgboost",
  "active_model": "xgboost",
  "timestamp": "2026-05-29T16:06:00"
}
```

`active_model` değerleri:
- `"xgboost"` — Faz 2 modeli aktif (yüksek doğruluk)
- `"lstm"` — LSTM modeli eğitilmiş ve aktif
- `"fallback"` — Ağırlıklı ortalama (son 5 okuma) aktif

## Model Yükleme Mekanizması

### XGBoost (Faz 2)

`model/xgboost_predictor.py` modül yüklendiğinde singleton `xgb_predictor = XGBoostPredictor()` oluşturulur. `__init__` içinde:

1. `/app/models/xgboost_density.pkl` ve `/app/models/xgboost_energy.pkl` okunur.
2. Dosyalar varsa `is_available = True`, aksi halde `is_available = False`.
3. Log: `"XGBoost modelleri yüklendi: density (X.X MB) + energy (X.X MB)"`

Model dosyaları Docker volume mount ile container'a gelir:

```yaml
# docker-compose.yml — ai-service
volumes:
  - ./ml-pipeline/models:/app/models:ro
```

`:ro` = read-only; ai-service modelleri değiştiremez.

### Fallback Mekanizması

`routes/predict.py` içindeki `_build_prediction()` şu zinciri çalıştırır:

```
xgb_predictor.is_available == True?
  ├─ Evet → xgb_predictor.predict(zone_id, next_minutes)
  │          ├─ Başarılı → sonucu döndür
  │          └─ Exception → log + fallback'e düş
  └─ Hayır → fallback_prediction (LSTM / ağırlıklı ortalama)
```

Fallback `lstm_model.py::predictor.predict(history)` çağırır. LSTM eğitilmemişse (`is_trained=False`) son 5 okumanın ağırlıklı ortalaması + ±0.05 gürültü kullanılır.

## Feature Engineering

XGBoost feature satırı (27 sütun) her tahmin isteğinde anında hesaplanır:

- **Zaman:** year, month, day, hour, minute, dow, is_weekend, is_holiday
- **Döngüsel encoding:** hour_sin/cos, dow_sin/cos, month_sin/cos
- **Zone:** zone_id, capacity, zone_type one-hot (GATE/SECURITY/CHECKIN/LOUNGE)
- **Lag değerleri:** density_lag_{1,4,96,672}, energy_lag_{1,4,96} (density için)

Lag değerleri `occupancy_readings` ve `environmental_metrics` tablolarından canlı çekilir (en son 700 kayıt, DESC sıralı).

## Geliştirme

```bash
cd ai-service
pip install -r requirements.txt
python app.py   # http://localhost:5000
```

### Model olmadan test (fallback modu)

```bash
# ml-pipeline/models/ yoksa otomatik fallback aktif olur
curl http://localhost:5000/health
# {"active_model": "fallback", ...}
```

### XGBoost ile test

```bash
# Modeller ml-pipeline/models/ altında olmalı
# Yerel test için MODELS_DIR'i geçici olarak override et:
MODELS_PATH=/path/to/ml-pipeline/models python app.py
```
