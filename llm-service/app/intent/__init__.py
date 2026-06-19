"""Intent classification module.

Architecture:
    - BaseClassifier (interface) -- defined in base.py
    - RuleBasedClassifier (keyword/regex) -- step 3.2
    - DistilBertClassifier (fine-tuned ML) -- step 4.2
    - HybridClassifier (production default) -- step 4.2

Public API exposes all four classes so tests can pick any concrete
implementation. Production (chat.py) uses HybridClassifier.
"""
from app.intent.base import BaseClassifier, IntentResult, IntentLabel
from app.intent.rule_based import RuleBasedClassifier
from app.intent.distilbert_classifier import DistilBertClassifier
from app.intent.hybrid_classifier import HybridClassifier

__all__ = [
    "BaseClassifier",
    "IntentResult",
    "IntentLabel",
    "RuleBasedClassifier",
    "DistilBertClassifier",
    "HybridClassifier",
]
