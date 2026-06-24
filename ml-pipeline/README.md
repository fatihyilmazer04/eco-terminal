# ml-pipeline — Eco-Terminal ML Pipeline

10 yıllık (2016–2026) sentetik havalimanı verisi üretir ve XGBoost + Analog kNN modellerini eğitir.

## Klasör Yapısı

```
ml-pipeline/
├── generate_synthetic_data.py  ← FAZ 1: Sentetik veri üretimi
├── verify_data.py              ← FAZ 1: Veri kalite raporu
├── feature_engineering.py     ← Paylaşılan özellik mühendisliği modülü
├── train_xgboost.py            ← FAZ 2: XGBoost eğitimi (yoğunluk + enerji)
├── train_analog.py             ← FAZ 2: Analog kNN indexleme (15 zone × 2)
├── evaluate_models.py          ← FAZ 2: 4 modeli karşılaştır, rapor üret
├── predict.py                  ← FAZ 2/3: CLI tahmin aracı
├── requirements.txt
├── README.md
├── data/                       ← .gitignore'da — git'e push edilmez
│   ├── synthetic_terminal_data.parquet  (5.4M satır, ~180 MB)
│   ├── processed_features.parquet       (feature cache, ~250 MB)
│   ├── comparison_report.txt            (Türkçe karşılaştırma raporu)
│   └── *.png                            (grafikler)
└── models/                     ← .gitignore'da — git'e push edilmez
    ├── xgboost_density.pkl
    ├── xgboost_energy.pkl
    ├── analog_density_zone_1.pkl … analog_density_zone_15.pkl
    └── analog_energy_zone_1.pkl  … analog_energy_zone_15.pkl
```

## Kurulum

```bash
cd ml-pipeline
pip install -r requirements.txt
```

Python 3.10+ önerilir.

---

## FAZ 1 — Veri Üretimi

```bash
# Ham sentetik veriyi üret (~5–10 dk)
python generate_synthetic_data.py

# Opsiyonel: veri kalitesini doğrula
python verify_data.py
```

**Çıktı:** `ml-pipeline/data/synthetic_terminal_data.parquet` (~180 MB)

---

## FAZ 2 — Model Eğitimi (Sırasıyla Çalıştır)

```bash
# 1. XGBoost modelleri eğit (~10–20 dk)
python train_xgboost.py

# 2. Analog kNN modelleri hazırla (~5–15 dk)
python train_analog.py

# 3. 4 modeli karşılaştır ve rapor üret (~2–5 dk)
python evaluate_models.py

# 4. Tek nokta tahmin testi (opsiyonel)
python predict.py --zone 1 --datetime "2026-06-01 14:00" --target density
python predict.py --zone 10 --datetime "2026-03-15 08:30" --target energy --model both
python predict.py --zone 5 --datetime "2026-06-01 14:00" --target both --model both
```

**Not:** `feature_engineering.py` ilk çalıştırmada özellik mühendisliğini hesaplayıp
`data/processed_features.parquet` olarak önbelleğe alır. Sonraki script'ler önbellekten
okur (çok daha hızlı).

## Doğrulama

```bash
python verify_data.py
```

Şunları raporlar:
- Toplam kayıt sayısı ve NaN kontrolü
- Zone başına eşit kayıt dağılımı
- Saatlik doluluk profili (ASCII histogram)
- Mevsimsel karşılaştırma (yaz > kış kontrolü)
- Yıllık büyüme (COVID düşüşü 2020–2021)
- Enerji istatistikleri

`matplotlib` kuruluysa `data/validation_plots.png` grafik dosyası da üretilir.

## Üretilen Veri Özeti

| Özellik             | Değer                          |
|---------------------|-------------------------------|
| Tarih aralığı       | 2016-01-01 – 2026-05-29       |
| Aralık              | 15 dakika                     |
| Zone sayısı         | 15                            |
| Toplam kayıt        | ~5.4 milyon                   |
| Sıkıştırma          | Snappy (Parquet)              |
| Tahmini dosya boyutu| ~150–200 MB                   |
| Seed (tekrar üretim)| `np.random.default_rng(42)`   |

### Sütunlar

| Sütun           | Tür       | Açıklama                              |
|-----------------|-----------|---------------------------------------|
| `timestamp`     | datetime  | 15-dakikalık zaman damgası            |
| `zone_id`       | int16     | DB zone ID (1–15)                     |
| `zone_name`     | category  | Gate A1, Security-1 vb.              |
| `zone_type`     | category  | GATE / SECURITY / CHECKIN / LOUNGE    |
| `capacity`      | int16     | Bölge kapasitesi (kişi)               |
| `people_count`  | int32     | Anlık kişi sayısı                     |
| `density_pct`   | float64   | Doluluk oranı (0.0–1.0)              |
| `energy_kwh`    | float64   | 15 dakikalık enerji tüketimi (kWh)    |
| `temp_c`        | float64   | İç mekan sıcaklığı (°C)              |
| `lighting_lux`  | int32     | Aydınlatma yoğunluğu (lux)           |

### Modellenen Örüntüler

- **Saatlik:** Sabah piği (06–09), öğleden sonra (14–17), akşam (18–21), gece düşüş
- **Haftalık:** Cuma en yoğun, hafta sonu tatil seyahatleri
- **Mevsimsel:** Yaz %20–35 daha yoğun, Yılbaşı + Bayramlar pik
- **Yıllık büyüme:** Yıllık %3–4, COVID etkisi 2020–2021'de modellendi
- **Gürültü:** ±%7 standart sapma (doğal varyans, ML için gerekli)

## Parquet Dosyasını Okuma

```python
import pandas as pd

df = pd.read_parquet('data/synthetic_terminal_data.parquet')

# Sadece belirli zone'lar
gate_df = df[df['zone_type'] == 'GATE']

# Belirli tarih aralığı
df_2024 = df[(df['timestamp'] >= '2024-01-01') & (df['timestamp'] < '2025-01-01')]

# Belirli sütunlar (bellek optimizasyonu)
df_ml = pd.read_parquet(
    'data/synthetic_terminal_data.parquet',
    columns=['timestamp', 'zone_id', 'people_count', 'density_pct', 'energy_kwh']
)
```

## Notlar

- `data/` klasörü `.gitignore`'dadır — üretilen Parquet dosyaları git'e push edilmez.
- Aynı seed (`42`) ile her çalıştırmada birebir aynı veri üretilir.
- Docker'a eklenmemiştir — tek seferlik çalıştırma için tasarlanmıştır.
