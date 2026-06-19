"""Hugging Face yerel model istemcisi.

Model startup sırasında bir kez yüklenir, sonraki istekler
aynı model üzerinden çalışır. Inference asyncio.to_thread ile
event loop'u bloklamadan çalıştırılır.
"""
import asyncio
import logging
from typing import Optional

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

from app.config import settings

logger = logging.getLogger(__name__)


class HuggingFaceClient:
    """Qwen2.5-3B-Instruct (veya yapılandırılmış başka model) için yerel inference."""

    def __init__(self):
        self.model_id = settings.hf_model_id
        self.timeout  = settings.hf_timeout_seconds
        self.device   = "cuda" if torch.cuda.is_available() else "cpu"
        self._model     = None
        self._tokenizer = None
        self._loaded    = False

    def load(self):
        """Modeli belleğe yükle. Uygulama başlangıcında bir kez çağrılır."""
        if self._loaded:
            return
        logger.info("hf_model_loading model=%s device=%s", self.model_id, self.device)
        dtype = torch.float16 if self.device == "cuda" else torch.float32

        self._tokenizer = AutoTokenizer.from_pretrained(
            self.model_id,
            trust_remote_code=True,
        )
        self._model = AutoModelForCausalLM.from_pretrained(
            self.model_id,
            torch_dtype=dtype,
            device_map="auto",          # GPU varsa otomatik yerleştir
            trust_remote_code=True,
        )
        self._model.eval()
        self._loaded = True
        logger.info("hf_model_loaded model=%s device=%s", self.model_id, self.device)

    def _run_inference(self, prompt: str) -> Optional[str]:
        """Senkron inference — to_thread içinde çalıştırılır."""
        messages = [
            {
                "role": "system",
                "content": (
                    "Sen Eco-Terminal havalimanı asistanısın. "
                    "Türkçe, kısa ve net cevaplar ver. "
                    "Emoji kullanma. "
                    "Maksimum 3-4 cümle yaz."
                ),
            },
            {"role": "user", "content": prompt},
        ]

        text = self._tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=True,
        )
        inputs = self._tokenizer(text, return_tensors="pt").to(self._model.device)

        with torch.no_grad():
            outputs = self._model.generate(
                **inputs,
                max_new_tokens=300,
                temperature=0.7,
                do_sample=True,
                top_p=0.9,
                pad_token_id=self._tokenizer.eos_token_id,
            )

        # Sadece üretilen kısmı al (input token'ları çıkar)
        generated = outputs[0][inputs["input_ids"].shape[1]:]
        result = self._tokenizer.decode(generated, skip_special_tokens=True).strip()
        return result if result else None

    async def generate(self, prompt: str) -> Optional[str]:
        """Prompt'u modele gönderir, cevabı döner. Hata durumunda None."""
        if not self._loaded:
            logger.error("hf_model_not_loaded — generate çağrıldı ama model yüklenmemiş")
            return None
        try:
            result = await asyncio.wait_for(
                asyncio.to_thread(self._run_inference, prompt),
                timeout=self.timeout,
            )
            logger.debug("hf_generate_ok chars=%d", len(result) if result else 0)
            return result
        except asyncio.TimeoutError:
            logger.warning("hf_timeout timeout=%.1fs", self.timeout)
            return None
        except Exception as e:
            logger.error("hf_error type=%s msg=%s", type(e).__name__, str(e)[:300])
            return None

    def is_configured(self) -> bool:
        return self._loaded
