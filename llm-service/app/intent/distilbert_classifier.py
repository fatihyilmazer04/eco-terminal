"""DistilBERT-based intent classifier.

Loads the fine-tuned model from disk and provides classification.
This is the ML-based counterpart to RuleBasedClassifier.

Performance:
    - Cold start: 2-5 seconds (model load)
    - Inference: 30-80ms per query on CPU
    - Memory: ~500 MB RAM

Hybrid usage: Use HybridClassifier in production, not this directly.
This class is exposed for testing and fallback scenarios.
"""
import logging
from pathlib import Path
from typing import Optional

try:
    import torch
    from transformers import AutoTokenizer, AutoModelForSequenceClassification
    _TORCH_AVAILABLE = True
except ImportError:
    _TORCH_AVAILABLE = False

from app.intent.base import BaseClassifier, IntentLabel, IntentResult
from app.intent.rule_based import RuleBasedClassifier

logger = logging.getLogger(__name__)


class DistilBertClassifier(BaseClassifier):
    """Fine-tuned DistilBERT classifier for intent detection.

    The model classifies into the same 6 intents as RuleBasedClassifier,
    but with semantic understanding (handles paraphrases, typos better).

    Entity extraction is DELEGATED to RuleBasedClassifier — the regex
    patterns there are more reliable than what we'd get from a small
    fine-tuned model.
    """

    _MODEL_DIR = Path(__file__).parent / "models" / "distilbert"

    def __init__(self, model_dir: Optional[Path] = None):
        self.model_dir = Path(model_dir) if model_dir else self._MODEL_DIR
        self._tokenizer = None
        self._model = None
        self._loaded = False

        # Reuse rule-based entity extraction
        # (the model only predicts intent, not entities)
        self._entity_extractor = RuleBasedClassifier()

    def _ensure_loaded(self) -> bool:
        """Lazy load: model loads on first classify() call.

        Returns False if loading failed (model files missing/corrupt).
        Caller should fall back to rule-based.
        """
        if self._loaded:
            return True

        if not _TORCH_AVAILABLE:
            logger.warning("distilbert_unavailable: torch/transformers not installed — using rule-based fallback")
            return False

        if not self.model_dir.exists():
            logger.error("distilbert_model_not_found path=%s", self.model_dir)
            return False

        try:
            logger.info("Loading DistilBERT from %s...", self.model_dir)
            self._tokenizer = AutoTokenizer.from_pretrained(str(self.model_dir))
            self._model = AutoModelForSequenceClassification.from_pretrained(str(self.model_dir))
            self._model.eval()
            self._loaded = True
            logger.info(
                "DistilBERT loaded: %d labels, device=%s",
                self._model.config.num_labels,
                next(self._model.parameters()).device,
            )
            return True
        except Exception as e:
            logger.error("distilbert_load_failed err=%s", str(e)[:200])
            return False

    @property
    def name(self) -> str:
        return "distilbert-multilingual-v1"

    @property
    def is_available(self) -> bool:
        """Check if model is loaded and ready (without forcing load)."""
        return self._loaded

    def classify(self, message: str, locale: str = "tr-TR") -> IntentResult:
        """Run DistilBERT inference + reuse rule-based for entity extraction.

        If model fails to load, raises IntentResult with UNKNOWN and
        confidence=0. Caller (HybridClassifier) handles fallback.
        """
        if not message or not message.strip():
            return IntentResult(
                intent=IntentLabel.UNKNOWN,
                confidence=0.0,
                entities={},
                raw_message=message,
            )

        if not self._ensure_loaded():
            # Loading failed — return zero-confidence so hybrid falls back
            return IntentResult(
                intent=IntentLabel.UNKNOWN,
                confidence=0.0,
                entities={},
                raw_message=message,
            )

        # Tokenize + inference
        try:
            inputs = self._tokenizer(
                message, return_tensors="pt", truncation=True, max_length=64
            )
            with torch.no_grad():
                logits = self._model(**inputs).logits
                probs = torch.softmax(logits, dim=-1)[0]

            # Top prediction
            top_idx = int(torch.argmax(probs).item())
            top_prob = float(probs[top_idx].item())

            # Map id → label string → IntentLabel enum
            id2label = self._model.config.id2label
            label_str = id2label[top_idx]
            try:
                intent = IntentLabel(label_str)
            except ValueError:
                logger.warning("unknown_label_from_model label=%s", label_str)
                intent = IntentLabel.UNKNOWN

            # Extract entities using rule-based regex (more reliable)
            rb_result = self._entity_extractor.classify(message, locale)

            result = IntentResult(
                intent=intent,
                confidence=round(top_prob, 3),
                entities=rb_result.entities,
                raw_message=message,
            )

            logger.debug(
                "distilbert_classified msg='%s' -> %s (conf=%.3f, entities=%s)",
                message[:60], intent.value, top_prob, rb_result.entities,
            )

            return result

        except Exception as e:
            logger.error("distilbert_inference_failed err=%s", str(e)[:200])
            return IntentResult(
                intent=IntentLabel.UNKNOWN,
                confidence=0.0,
                entities={},
                raw_message=message,
            )
