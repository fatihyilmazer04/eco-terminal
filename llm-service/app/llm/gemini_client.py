"""Google Gemini API client.

Uses google-generativeai SDK. For production we'd want streaming,
retries with exponential backoff, and rate limiting. For step 3.3
we keep it simple: one call, one response, fail fast on errors.
"""
import asyncio
import logging
from typing import Optional

import google.generativeai as genai
from app.config import settings

logger = logging.getLogger(__name__)


class GeminiClient:
    """Thin async wrapper around the Gemini SDK."""

    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        self.api_key = api_key or settings.gemini_api_key
        self.model_name = model or settings.gemini_model
        self.timeout = settings.gemini_timeout_seconds

        if not self.api_key:
            logger.warning("gemini_api_key is empty — calls will fail")
        else:
            genai.configure(api_key=self.api_key)

        # SDK requires "models/..." prefix for v1beta; add if missing
        model_id = self.model_name if self.model_name.startswith("models/") else f"models/{self.model_name}"
        self._model = genai.GenerativeModel(model_id)

    async def generate(self, prompt: str) -> Optional[str]:
        """Send prompt to Gemini, return generated text.

        Returns None on any failure — caller should provide a fallback.
        """
        if not self.api_key:
            logger.error("gemini_no_api_key — cannot generate")
            return None

        try:
            # SDK is sync; run in thread to avoid blocking event loop
            response = await asyncio.wait_for(
                asyncio.to_thread(self._model.generate_content, prompt),
                timeout=self.timeout,
            )

            if not response or not response.text:
                logger.warning("gemini_empty_response")
                return None

            return response.text.strip()

        except asyncio.TimeoutError:
            logger.warning("gemini_timeout timeout=%.1fs", self.timeout)
            return None
        except Exception as e:
            logger.error("gemini_error type=%s msg=%s", type(e).__name__, str(e)[:200])
            return None

    def is_configured(self) -> bool:
        return bool(self.api_key)
