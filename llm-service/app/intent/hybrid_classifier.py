"""Hybrid classifier: DistilBERT first, rule-based fallback.

Strategy:
    1. Try DistilBERT inference
    2. If confidence >= HIGH_CONFIDENCE_THRESHOLD -> use DistilBERT result
    3. Else -> fall back to rule-based (also more reliable entities)
    4. If DistilBERT fails entirely -> rule-based only

This pattern combines:
    - DistilBERT's semantic understanding (paraphrases, typos)
    - Rule-based's deterministic entity extraction (gate codes, etc.)
    - Resilience (model failure doesn't break the service)
"""
import logging

from app.intent.base import BaseClassifier, IntentLabel, IntentResult
from app.intent.rule_based import RuleBasedClassifier
from app.intent.distilbert_classifier import DistilBertClassifier

logger = logging.getLogger(__name__)


class HybridClassifier(BaseClassifier):
    """Combines DistilBERT + rule-based for best of both worlds."""

    # If DistilBERT confidence >= this, trust it. Below this, use rule-based.
    # 0.85 chosen empirically: model test accuracy was ~86%, so confident
    # predictions are very likely correct, low-confidence ones often aren't.
    HIGH_CONFIDENCE_THRESHOLD = 0.85

    # Intents that DistilBERT was NOT trained on — rule-based takes absolute
    # priority whenever it detects one of these with reasonable confidence.
    _RULE_ONLY_INTENTS = {IntentLabel.NOTIFICATION_QUERY, IntentLabel.TICKET_HELP}
    _RULE_ONLY_MIN_CONF = 0.65  # minimum rule-based confidence to override DistilBERT

    def __init__(self):
        self._distilbert = DistilBertClassifier()
        self._rule_based = RuleBasedClassifier()

    @property
    def name(self) -> str:
        if self._distilbert.is_available:
            return f"hybrid(distilbert+rule-based, threshold={self.HIGH_CONFIDENCE_THRESHOLD})"
        return "hybrid(rule-based-only, distilbert-unavailable)"

    def classify(self, message: str, locale: str = "tr-TR") -> IntentResult:
        """Run hybrid classification.

        Output IntentResult.entities always comes from rule-based
        (regex extraction is more reliable than what a small fine-tuned
        model would give us).

        IntentResult.intent + confidence:
            1. Rule-based wins unconditionally for new intents DistilBERT
               doesn't know (notification_query, ticket_help).
            2. DistilBERT wins if its confidence >= HIGH_CONFIDENCE_THRESHOLD.
            3. Rule-based wins otherwise.
        """
        if not message or not message.strip():
            return IntentResult(
                intent=IntentLabel.UNKNOWN,
                confidence=0.0,
                entities={},
                raw_message=message,
            )

        # Always run rule-based (cheap, ~1ms, gives entities + fallback)
        rb_result = self._rule_based.classify(message, locale)

        # Priority 1: rule-based detected a new intent DistilBERT doesn't know
        if (rb_result.intent in self._RULE_ONLY_INTENTS
                and rb_result.confidence >= self._RULE_ONLY_MIN_CONF):
            logger.info(
                "hybrid_decision source=rule-based(new-intent-override) msg='%s' "
                "rb=%s(%.2f) chosen=%s",
                message[:50], rb_result.intent.value, rb_result.confidence,
                rb_result.intent.value,
            )
            return IntentResult(
                intent=rb_result.intent,
                confidence=rb_result.confidence,
                entities=rb_result.entities,
                raw_message=message,
            )

        # Try DistilBERT
        db_result = self._distilbert.classify(message, locale)

        # Priority 2: high-confidence DistilBERT
        if db_result.confidence >= self.HIGH_CONFIDENCE_THRESHOLD:
            chosen_intent = db_result.intent
            chosen_confidence = db_result.confidence
            source = "distilbert"
        else:
            # Priority 3: rule-based fallback
            chosen_intent = rb_result.intent
            chosen_confidence = rb_result.confidence
            source = "rule-based"

        logger.info(
            "hybrid_decision source=%s msg='%s' db=%s(%.2f) rb=%s(%.2f) chosen=%s",
            source,
            message[:50],
            db_result.intent.value, db_result.confidence,
            rb_result.intent.value, rb_result.confidence,
            chosen_intent.value,
        )

        return IntentResult(
            intent=chosen_intent,
            confidence=chosen_confidence,
            entities=rb_result.entities,  # always from rule-based
            raw_message=message,
        )
