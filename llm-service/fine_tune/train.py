"""
Eco-Terminal Qwen2.5-0.5B Fine-Tuning Scripti (LoRA)
=====================================================
Kullanım:
    cd llm-service
    python fine_tune/train.py

Çıktı:
    fine_tune/output/   → LoRA ağırlıkları
    fine_tune/merged/   → Base + LoRA birleşik model (uvicorn için kullanılır)

Gereksinimler:
    pip install peft trl datasets
"""

import os
import json
from pathlib import Path

# datasets, torch'tan ÖNCE import edilmeli (Windows DLL sırası)
from datasets import Dataset
from transformers import (
    AutoTokenizer,
    AutoModelForCausalLM,
    TrainingArguments,
)
from peft import LoraConfig, get_peft_model, TaskType
from trl import SFTTrainer

import torch

# ── Yollar ────────────────────────────────────────────────────────────────────
BASE_DIR    = Path(__file__).parent
DATASET_PATH = BASE_DIR / "dataset.jsonl"
OUTPUT_DIR   = BASE_DIR / "output"
MERGED_DIR   = BASE_DIR / "merged"

# ── Konfigürasyon ─────────────────────────────────────────────────────────────
BASE_MODEL   = "Qwen/Qwen2.5-0.5B-Instruct"
MAX_SEQ_LEN  = 512
NUM_EPOCHS   = 4
BATCH_SIZE   = 2
GRAD_ACCUM   = 4          # Efektif batch = 2*4 = 8
LEARNING_RATE = 2e-4

LORA_R       = 16
LORA_ALPHA   = 32
LORA_DROPOUT = 0.05
LORA_TARGETS = ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]


def load_dataset_from_jsonl(path: Path) -> Dataset:
    """JSONL dosyasını yükle, Qwen chat formatına çevir."""
    records = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    print(f"[Dataset] {len(records)} örnek yüklendi.")
    return Dataset.from_list(records)


def format_messages(tokenizer, example):
    """messages listesini Qwen chat template ile tek string'e çevir."""
    text = tokenizer.apply_chat_template(
        example["messages"],
        tokenize=False,
        add_generation_prompt=False,
    )
    return {"text": text}


def main():
    # Önce local cache path'i bul (HF model ID yerine local path ile load)
    from huggingface_hub import snapshot_download
    local_model_path = snapshot_download(BASE_MODEL, local_files_only=True)

    print(f"[Config] Model: {BASE_MODEL}")
    print(f"[Config] Local path: {local_model_path}")
    print(f"[Config] Device: {'cuda' if torch.cuda.is_available() else 'cpu'}")
    print(f"[Config] Epochs: {NUM_EPOCHS}, Batch: {BATCH_SIZE}, LR: {LEARNING_RATE}")

    # ── Tokenizer ──────────────────────────────────────────────────────────────
    print("[1/5] Tokenizer yükleniyor...")
    tokenizer = AutoTokenizer.from_pretrained(local_model_path)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    # ── Dataset ────────────────────────────────────────────────────────────────
    print("[2/5] Dataset hazırlanıyor...")
    raw_dataset = load_dataset_from_jsonl(DATASET_PATH)
    dataset = raw_dataset.map(
        lambda ex: format_messages(tokenizer, ex),
        remove_columns=["messages"],
    )
    print(f"[Dataset] İlk örnek (kısaltılmış):\n{dataset[0]['text'][:300]}\n...")

    # ── Model ─────────────────────────────────────────────────────────────────
    print("[3/5] Model yükleniyor...")
    model = AutoModelForCausalLM.from_pretrained(
        local_model_path,
        torch_dtype=torch.float32,
        trust_remote_code=True,
        device_map="auto",
    )
    model.config.use_cache = False

    # ── LoRA ──────────────────────────────────────────────────────────────────
    print("[4/5] LoRA yapılandırılıyor...")
    lora_config = LoraConfig(
        r=LORA_R,
        lora_alpha=LORA_ALPHA,
        target_modules=LORA_TARGETS,
        lora_dropout=LORA_DROPOUT,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # ── Training ──────────────────────────────────────────────────────────────
    print("[5/5] Eğitim başlıyor...")
    training_args = TrainingArguments(
        output_dir=str(OUTPUT_DIR),
        num_train_epochs=NUM_EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        gradient_accumulation_steps=GRAD_ACCUM,
        learning_rate=LEARNING_RATE,
        lr_scheduler_type="cosine",
        warmup_ratio=0.05,
        logging_steps=5,
        save_steps=50,
        save_total_limit=2,
        fp16=False,
        dataloader_num_workers=0,
        report_to="none",
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        dataset_text_field="text",
        max_seq_length=MAX_SEQ_LEN,
        args=training_args,
    )

    trainer.train()

    # ── Kaydet ────────────────────────────────────────────────────────────────
    print(f"\n[Kaydet] LoRA ağırlıkları kaydediliyor → {OUTPUT_DIR}")
    trainer.save_model(str(OUTPUT_DIR))
    tokenizer.save_pretrained(str(OUTPUT_DIR))

    # ── Merge ─────────────────────────────────────────────────────────────────
    print(f"[Merge] LoRA + base model birleştiriliyor → {MERGED_DIR}")
    from peft import PeftModel
    base_model = AutoModelForCausalLM.from_pretrained(
        local_model_path,
        torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
        trust_remote_code=True,
    )
    merged_model = PeftModel.from_pretrained(base_model, str(OUTPUT_DIR))
    merged_model = merged_model.merge_and_unload()
    merged_model.save_pretrained(str(MERGED_DIR))
    tokenizer.save_pretrained(str(MERGED_DIR))

    print(f"\n[TAMAMLANDI] Fine-tuned model: {MERGED_DIR}")
    print("Sonraki adım: config.py dosyasında hf_model_id yolunu güncelle:")
    print(f'  hf_model_id: str = "{MERGED_DIR.resolve()}"')


if __name__ == "__main__":
    main()
