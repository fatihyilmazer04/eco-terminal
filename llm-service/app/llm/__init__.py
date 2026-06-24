"""LLM module — HuggingFace yerel model ve prompt building."""
# torch/transformers opsiyonel bağımlılıktır.
# Yüklü değilse HuggingFaceClient None olur; chat.py template fallback'e geçer.
try:
    from app.llm.huggingface_client import HuggingFaceClient
except ImportError:
    HuggingFaceClient = None  # type: ignore[assignment,misc]

from app.llm.prompt_builder import PromptBuilder

__all__ = ["HuggingFaceClient", "PromptBuilder"]
