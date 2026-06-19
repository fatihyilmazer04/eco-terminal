"""Ollama local LLM client.

Ollama REST API üzerinden yerel modeli çağırır.
Servis: http://localhost:11434 (varsayılan)
"""
import logging
from typing import Optional

import httpx
from app.config import settings

logger = logging.getLogger(__name__)


class OllamaClient:
    """Async HTTP wrapper for Ollama /api/generate endpoint."""

    def __init__(self):
        self.base_url  = settings.ollama_base_url.rstrip("/")
        self.model     = settings.ollama_model
        self.timeout   = settings.ollama_timeout_seconds

    async def generate(self, prompt: str) -> Optional[str]:
        """Prompt'u Ollama'ya gönderir, üretilen metni döner.

        Herhangi bir hata durumunda None döner — çağıran fallback'e geçmelidir.
        """
        url  = f"{self.base_url}/api/generate"
        body = {
            "model":  self.model,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.7,
                "num_predict": 300,   # max token
            },
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(url, json=body)
                resp.raise_for_status()
                data = resp.json()
                text = data.get("response", "").strip()
                if not text:
                    logger.warning("ollama_empty_response model=%s", self.model)
                    return None
                logger.debug("ollama_ok model=%s chars=%d", self.model, len(text))
                return text

        except httpx.TimeoutException:
            logger.warning("ollama_timeout timeout=%.1fs model=%s", self.timeout, self.model)
            return None
        except httpx.HTTPStatusError as e:
            logger.error("ollama_http_error status=%d msg=%s", e.response.status_code, str(e)[:200])
            return None
        except Exception as e:
            logger.error("ollama_error type=%s msg=%s", type(e).__name__, str(e)[:200])
            return None

    def is_configured(self) -> bool:
        """Ollama base URL ve model tanımlıysa True."""
        return bool(self.base_url and self.model)
