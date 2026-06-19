# 🧠 LLM Pipeline Dokümantasyonu

> Bu dokümantasyon **bitirme savunması için en kritik** olan kısımdır. Projenin yapay zeka tarafının tüm detayları burada açıklanmıştır.

## İçindekiler
- [Genel Bakış](#genel-bakış)
- [DistilBERT Fine-Tuning](#distilbert-fine-tuning)
- [Hibrit Sınıflandırma Sistemi](#hibrit-sınıflandırma-sistemi)
- [RAG Pipeline](#rag-pipeline)
- [Gemini Entegrasyonu](#gemini-entegrasyonu)
- [Sonuçlar ve Metrikler](#sonuçlar-ve-metrikler)
- [Akademik Referanslar](#akademik-referanslar)

---

## Genel Bakış

Eco-Terminal'in **kendi LLM modeli** + **API tabanlı LLM**'i birleştiren **hibrit RAG pipeline**'ı vardır. Bu yaklaşım, kitap "Hands-On Large Language Models" (Jay Alammar & Maarten Grootendorst, 2024) Bölüm 8'de anlatılan RAG mimarisini referans alır.

### Pipeline Bileşenleri

```
Kullanıcı Sorusu
       │
       ▼
┌─────────────────────────────────────────┐
│  1. Hibrit Intent Classifier            │
│     ┌──────────────┐ ┌──────────────┐   │
│     │ DistilBERT   │ │ Rule-Based   │   │
│     │ (fine-tuned) │ │ (regex)      │   │
│     └──────┬───────┘ └──────┬───────┘   │
│            └────────┬────────┘           │
│                     │                    │
│              confidence >= 0.85?         │
│              ├─ EVET → DistilBERT       │
│              └─ HAYIR → Rule-Based      │
└─────────────────────┬──────────────────-┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│  2. RAG Retrieval                       │
│     ┌──────────────┐ ┌──────────────┐   │
│     │ Knowledge    │ │ Backend      │   │
│     │ Base (10     │ │ (Dijkstra,   │   │
│     │ facts)       │ │  Heatmap)    │   │
│     └──────────────┘ └──────────────┘   │
└─────────────────────┬──────────────────-┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│  3. Prompt Builder                      │
│     System Prompt + Context + Question  │
└─────────────────────┬──────────────────-┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│  4. Gemini API (cevap üretimi)          │
└─────────────────────┬──────────────────-┘
                      │
                      ▼
              Türkçe doğal dil cevap
```

---

## DistilBERT Fine-Tuning

### Model Seçimi

**Model:** `distilbert-base-multilingual-cased` (HuggingFace)

**Neden DistilBERT?**
- BERT'ten %40 daha hızlı, %97 performans korur (Sanh et al., 2019)
- 67M parametre — bitirme projesi için ideal
- Multilingual (Türkçe + İngilizce karışık dataset destekler)
- CPU inference 50-80ms (production-friendly)

### Dataset

**Boyut:** 422 etiketli cümle

**Dağılım:**
| Intent | Örnek Sayısı | Örnek Cümleler |
|--------|--------------|----------------|
| `route_request` | 79 | "A1 kapısına nasıl giderim", "en kısa yol gate C3" |
| `flight_info` | 65 | "TK1922 uçağım kaçta", "kalkış saatim ne" |
| `crowd_query` | 67 | "security yoğun mu", "lounge boş mu" |
| `loyalty_query` | 67 | "eco puanım kaç", "tier seviyem" |
| `general_info` | 74 | "lounge nerede", "wifi şifresi" |
| `unknown` | 73 | "merhaba", "nasılsın", "hava nasıl" |

**Veri hazırlama:**
- Dil: Türkçe + İngilizce karışık (%70 TR, %30 EN)
- Stratified split: %75 train, %10 validation, %15 test
- Random seed: 42 (reproducibility için)

### Eğitim Konfigürasyonu

```python
TrainingArguments(
    num_train_epochs=8,
    per_device_train_batch_size=16,
    per_device_eval_batch_size=16,
    learning_rate=5e-5,
    warmup_ratio=0.1,
    weight_decay=0.01,
    eval_strategy="epoch",
    save_strategy="epoch",
    load_best_model_at_end=True,
    metric_for_best_model="accuracy",
    fp16=True,  # Mixed precision (GPU)
    seed=42,
)
```

### Eğitim Donanımı

- **GPU:** NVIDIA GeForce RTX 4060 Laptop GPU
- **CUDA:** 12.1
- **Süre:** ~50 saniye (8 epoch)
- **Bellek:** ~2 GB VRAM peak

### Öğrenme Eğrisi

| Epoch | Train Loss | Val Accuracy |
|-------|-----------|--------------|
| 1 | 1.78 | %44.2 |
| 2 | 0.92 | %60.5 |
| 3 | 0.45 | %79.1 |
| 4 | 0.28 | %83.7 |
| **5** | **0.18** | **%83.7** ⭐ Best |
| 6 | 0.12 | %83.7 |
| 7 | 0.09 | %81.4 |
| 8 | 0.07 | %83.7 |

**Gözlem:** 5. epoch'tan sonra **plato** — dataset doygunluğa ulaştı. Daha fazla epoch fayda etmez; daha fazla veri gerekir.

### Test Sonuçları (Held-out Test Set, 64 örnek)

| Intent | Precision | Recall | F1 |
|--------|-----------|--------|-----|
| route_request | 0.750 | 1.000 | 0.857 |
| flight_info | 1.000 | 0.700 | 0.824 |
| crowd_query | 0.727 | 0.800 | 0.762 |
| loyalty_query | 0.909 | 1.000 | **0.952** |
| general_info | 0.889 | 0.727 | 0.800 |
| unknown | 1.000 | 0.909 | **0.952** |
| **TOPLAM** | | | **%85.9 accuracy** |

**Karşılaştırma:** Rastgele tahmin = %16.7 (6 sınıf). Model **5x üzerinde** iyileşme sağladı.

### Model Çıktısı

Eğitilmiş model şu dosyalarda saklanır:

```
intent-training/models/intent_classifier/final/
├── model.safetensors        (517 MB - fine-tuned ağırlıklar)
├── config.json              (6 sınıf, id2label mapping)
├── tokenizer.json
├── vocab.txt
├── special_tokens_map.json
└── training_metrics.json    (test_accuracy, hyperparameters)
```

---

## Hibrit Sınıflandırma Sistemi

### Tasarım Motivasyonu

DistilBERT %85.9 accuracy üretiyor — yani **%14.1 oranında yanlış** tahmin yapabilir. Üstelik **entity extraction** (Gate A12, TK1922 gibi varlık tanıma) için ekstra eğitim gerekir. Bunları çözmek için **hibrit yaklaşım**:

```python
if distilbert_confidence >= 0.85:
    intent = distilbert_prediction
    entities = rule_based_entities  # Her zaman rule-based'den
else:
    intent = rule_based_prediction
    entities = rule_based_entities
```

### Bileşenler

#### 1. RuleBasedClassifier (`rule_based.py`)

**Yaklaşım:** Ağırlıklı keyword matching + regex entity extraction.

```python
_ROUTE_KEYWORDS = [
    ("nasıl giderim", 3.0), ("rota", 2.5), ("en kısa yol", 3.0),
    ("how do I get", 3.0), ("shortest path", 3.0),
    ...
]

_GATE_PATTERN = re.compile(
    r"\b(?:gate|kapı|kapi)?[\s\-]?([A-C])[\s\-]?(\d{1,2})\b",
    re.IGNORECASE
)
```

**Avantajlar:**
- ✅ Deterministik (aynı girdi → aynı çıktı)
- ✅ 1ms latency (model loading yok)
- ✅ Açıklanabilir (hangi keyword tetikledi)
- ✅ Entity extraction güvenilir

**Dezavantajlar:**
- ❌ Paraphrase'leri kaçırır ("yürümeli kaç dakika" → bilemez)
- ❌ Keyword list maintenance gerekir

#### 2. DistilBertClassifier (`distilbert_classifier.py`)

**Yaklaşım:** Fine-tuned modelden semantic anlayış.

```python
def classify(self, message):
    inputs = tokenizer(message, return_tensors="pt", max_length=64)
    with torch.no_grad():
        logits = model(**inputs).logits
        probs = torch.softmax(logits, dim=-1)[0]
    
    top_idx = torch.argmax(probs).item()
    confidence = float(probs[top_idx])
    intent = id2label[top_idx]
    
    return IntentResult(intent, confidence, entities=...)
```

**Avantajlar:**
- ✅ Semantic anlayış
- ✅ Türkçe + İngilizce
- ✅ %85.9 accuracy

**Dezavantajlar:**
- ❌ 50-80ms inference (CPU)
- ❌ 500MB model dosyası
- ❌ Black-box (neden böyle dedi belli değil)

#### 3. HybridClassifier (`hybrid_classifier.py`)

**Karar mantığı:**

```python
HIGH_CONFIDENCE_THRESHOLD = 0.85

def classify(message):
    rb_result = rule_based.classify(message)
    db_result = distilbert.classify(message)
    
    if db_result.confidence >= HIGH_CONFIDENCE_THRESHOLD:
        return IntentResult(
            intent=db_result.intent,
            confidence=db_result.confidence,
            entities=rb_result.entities,  # Her zaman rule-based
        )
    else:
        return IntentResult(
            intent=rb_result.intent,
            confidence=rb_result.confidence,
            entities=rb_result.entities,
        )
```

**Threshold seçimi:** 0.85 deneysel olarak belirlendi. Test accuracy %85.9 olduğu için, confidence >= 0.85 olan tahminler "yüksek güvenli" sayılır.

### Hibrit Performansı (Gerçek Test, 6 Senaryo)

| Senaryo | Sonuç | Kullanılan | Neden? |
|---------|-------|------------|--------|
| 1. A1 rota | route_request (0.909) | DistilBERT | Conf yüksek |
| 2. B2 kalabalıksız | route_request (0.912) | DistilBERT | Conf yüksek |
| 3. C3 paraphrase | route_request (0.855) | DistilBERT | Conf marjinal |
| 4. Eco puanım | loyalty_query (0.86) | DistilBERT | Conf yüksek |
| 5. Lounge nerede | general_info (0.90) | **Rule-Based** | DistilBERT conf=0.37 (düşük) |
| 6. Hava nasıl | unknown (0.907) | DistilBERT | Conf yüksek |

**İstatistik:**
- DistilBERT kullanım: 5/6 = %83
- Rule-based fallback: 1/6 = %17

**Senaryo 5 analizi:** "Lounge nerede bulabilirim?" — DistilBERT crowd_query dedi (conf=0.37) ama rule-based "lounge" keyword'ünü gördü ve general_info (conf=0.90) dedi. Hibrit doğru karar verdi.

---

## RAG Pipeline

### RAG Nedir?

**Retrieval-Augmented Generation** = "Önce bilgi çek, sonra cevapla"

LLM'lere doğrudan soru sormak yerine:
1. Soru'yu analiz et
2. İlgili bilgileri **kendi knowledge base'inden** çek
3. Bu bilgilerle birlikte LLM'e gönder
4. LLM, sağlanan veriye dayalı cevap üretir

**Faydaları:**
- ✅ Hallüsinasyonu önler (LLM uydurmasını engeller)
- ✅ Up-to-date bilgi (LLM training cutoff'tan etkilenmez)
- ✅ Domain-specific (sadece havalimanı verisi)

### Bileşenler

#### 1. Knowledge Base (`rag/knowledge_base.py`)

10 etiketli "fact" — havalimanı hakkında temel bilgiler:

```python
Fact(
    topic="lounges",
    category="zone",
    text="İki lounge mevcuttur: Lounge-1 (üst, A konkursuna yakın) "
         "ve Lounge-2 (alt, C konkursuna yakın). Her ikisi de tüm "
         "8 gate'e bağlıdır.",
    keywords=["lounge", "salon", "bekleme", "vip"],
)
```

**Arama yöntemi:** Keyword matching + intent affinity bonus.

```python
def search(query, intent, entities, top_k=3):
    scores = []
    for fact in self._FACTS:
        score = 0
        for kw in fact.keywords:
            if kw in query.lower(): score += 3
            if kw in entity_text:    score += 2
        if intent == "route_request" and fact.category == "zone":
            score += 1
        scores.append((score, fact))
    return sorted(scores)[-top_k:]
```

**Not:** Daha büyük corpus için **sentence-transformers + FAISS** kullanılabilir. Şu an 10 fact için keyword match yeterli (kitap Bölüm 8'de tartışılan trade-off).

#### 2. Backend Client (`rag/backend_client.py`)

Spring Boot backend'e async HTTP çağrıları:

```python
class BackendClient:
    async def get_optimal_route(from_zone_id, to_zone_id):
        url = f"{base_url}/api/routes/optimal"
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.post(
                url,
                json={"fromZoneId": from_zone_id, "toZoneId": to_zone_id},
                headers={"X-Internal-Token": settings.backend_internal_token},
            )
            return resp.json().get("data")
```

Endpoint'ler:
- `POST /api/routes/optimal` — Dijkstra 3-alternatif
- `GET /api/heatmap/live` — tüm zone yoğunluk snapshot'ı
- `GET /api/occupancy/zone` — tek zone durumu
- `GET /api/flights/{code}` — uçuş detayı

#### 3. Retriever (`rag/retriever.py`)

Intent'e göre hangi veri çekileceğini belirler:

| Intent | Knowledge Base | Backend |
|--------|---------------|---------|
| route_request | ✅ Zone facts | ✅ Dijkstra |
| flight_info | ✅ Gate location | ✅ Flight details |
| crowd_query | ❌ | ✅ Heatmap/Zone status |
| loyalty_query | ✅ Loyalty policy | ❌ (gelecek: wallet) |
| general_info | ✅ Service facts | ❌ |
| unknown | ✅ Fallback facts | ❌ |

---

## Gemini Entegrasyonu

### Model

**Gemini 2.0 Flash** (`gemini-2.0-flash`)

**Neden Gemini?**
- Ücretsiz tier (15 RPM, 1500 RPD)
- Türkçe doğal cevap üretiminde güçlü
- Google AI Studio'dan kolay API key
- Streaming destek (ileride eklenebilir)

### Prompt Engineering

**System Prompt:**

```
Sen Eco-Terminal havalimanı asistanısın. Görevin yolcuların sorularını 
yanıtlamak: rota önerme, uçuş bilgisi, yoğunluk durumu, eco-puan, 
genel terminal bilgileri.

KURALLAR:
- Türkçe cevap ver, samimi ve profesyonel ol.
- 3-5 cümleden uzun yazma — yolcular kısa cevap ister.
- Sadece sağlanan veriyi kullan, uydurma.
- Eğer veri yetersizse "bu bilgiye şu an erişemiyorum" de.
- Asla iletişim bilgisi, telefon numarası uydurma.
- Emoji KULLANMA.
- Rota verirken adımları sırayla say: "1. ... 2. ..." formatında.
```

**Full Prompt Yapısı (örnek):**

```
[SYSTEM PROMPT]

BAĞLAM:
Terminal Bilgileri:
  1. A konkursu 3 gate içerir: Gate A1, A2, A3...
  2. İki lounge mevcuttur: Lounge-1 (üst)...

Önerilen Rota (Dijkstra hesaplaması):
  • SHORTEST: CheckIn-1 → Security-1 → Lounge-1 → Gate A1 
    (4 dk, 305m, ortalama yoğunluk %35)
  • LEAST_CROWDED: CheckIn-1 → Security-2 → Lounge-2 → Gate A1
    (5 dk, 380m, ortalama yoğunluk %18)

Tespit edilen niyet: route_request
Çıkarılan bilgiler: {'destination': 'Gate A1', 'route_preference': 'least_crowded'}

YOLCU SORUSU: A1 kapısına en kalabalıksız yoldan nasıl giderim?

CEVABIN:
```

### Fallback Stratejisi

Gemini başarısız olursa (kota dolu, network hatası, timeout):

```python
_FALLBACK_REPLIES = {
    "route_request": "Rota bilgilerinize ulaştım ancak şu an detaylı yanıt üretemiyorum...",
    "flight_info": "Uçuş sorgunuz alındı, ancak Gemini servisi şu an yanıt veremedi...",
    ...
}
```

Sistem **fallback'le bile çalışmaya devam eder** — kullanıcı en azından intent ve rota adımlarını alır.

---

## Sonuçlar ve Metrikler

### Model Performansı

| Metric | Değer | Yorum |
|--------|-------|-------|
| Test Accuracy | **%85.9** | 6-sınıf, baseline %16.7 |
| Avg Confidence | 0.88 | Yüksek güvenli tahminler |
| Inference Latency (CPU) | 50-80ms | Production-friendly |
| Model Size | 517 MB | Lazy-loaded, RAM ~500MB |

### Pipeline Performansı

| Metric | Değer |
|--------|-------|
| Cold start | 4.1 sn (model lazy-load) |
| Sıcak inference | 280-370 ms |
| Gemini latency | +2-4 sn (varsa) |
| End-to-end (Gemini sız) | < 1 sn |
| End-to-end (Gemini ile) | 2-5 sn |

### Hibrit Dağılımı (6 Senaryo Test)

```
distilbert ████████████████████████  5/6 (83%)
rule-based ████                       1/6 (17%)
```

### En Etkileyici Sonuç

**Senaryo 3: "C3 yürümeli kaç dakika"**
- "rota", "yol", "git", "nasıl" kelimelerinin **HİÇBİRİ** yok
- Rule-based: `unknown` (eşleşen keyword yok)
- DistilBERT: `route_request` (confidence 0.855)
- **Fine-tuning sayesinde semantic anlayış** çalışıyor

Bu, fine-tuning'in **rule-based üzerine değer kattığının** en güçlü kanıtıdır.

---

## Akademik Referanslar

### Kullanılan Kaynaklar

1. **Hands-On Large Language Models** — Jay Alammar & Maarten Grootendorst, O'Reilly 2024
   - Bölüm 6: Prompt Engineering
   - Bölüm 8: Semantic Search and RAG (ana referans)
   - Bölüm 11: Fine-Tuning Representation Models (DistilBERT için)

2. **DistilBERT, a distilled version of BERT** — Sanh et al., 2019
   - https://arxiv.org/abs/1910.01108
   - Model seçimi gerekçesi

3. **Attention Is All You Need** — Vaswani et al., 2017
   - Transformer mimarisinin temeli (BERT/DistilBERT base)

4. **Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks** — Lewis et al., 2020
   - RAG'ın orijinal makalesi
   - https://arxiv.org/abs/2005.11401

### Kullanılan Modeller

- `distilbert-base-multilingual-cased` (HuggingFace, base model)
- `gemini-2.0-flash` (Google Generative AI, response generation)

### Eğitim Reproducibility

```bash
cd intent-training
python -m venv venv
source venv/bin/activate  # veya venv\Scripts\activate (Windows)
pip install -r requirements.txt
python create_dataset.py  # 422 cümle CSV üretir
python train.py  # 8 epoch, ~50 sn (RTX 4060)
python evaluate.py  # Interaktif test
```

Tüm hyperparametreler `train.py` argparse default'larında. Random seed sabit (42).

---

## Sıkça Sorulan Sorular

### S: Niye sıfırdan model eğitmediniz?

**C:** Sıfırdan model eğitmek (pretraining) milyon dolarlık GPU çiftliği ve aylar gerektirir. Bunun yerine, hazır bir model'i (DistilBERT) **kendi domain veriniz ile fine-tune** etmek bitirme projesi seviyesinde gerçekçi ve akademik olarak geçerli yaklaşımdır.

### S: 422 cümle yeterli mi?

**C:** Bu boyut, basic intent classification için yeterli — test accuracy %85.9. Production için 5000+ cümle istenir. Dataset genişletme yolu açık (`create_dataset.py` ile).

### S: Niye sentence-transformers / FAISS kullanmadınız?

**C:** Knowledge base 10 fact içeriyor — bu boyutta keyword matching yeterli. Embedding tabanlı arama büyük corpus (100+ döküman) için anlamlıdır. Mimari gelecekte upgrade'e açık.

### S: Gemini neden gerekli, DistilBERT yeterli değil mi?

**C:** DistilBERT sadece **sınıflandırma** yapar. Doğal dil **cevap üretimi** için generative model (Gemini) gerekir. İki rol farklı: DistilBERT "ne soruyor", Gemini "nasıl cevaplayalım".

### S: Hibrit yerine sadece DistilBERT olsa olmaz mı?

**C:** Olur ama:
- Entity extraction zor (gate code, flight code regex daha güvenilir)
- Model fail olursa sistem çöker (resilience yok)
- Düşük confidence durumlarında ne yapacağı belirsiz

Hibrit yaklaşım bu üç sorunu çözer.
