"""Abstract base for intent classifiers — both rule-based and ML."""
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, Any


class IntentLabel(str, Enum):
    """Supported intent categories.

    Adding a new intent here is a breaking change for the prompt builder
    (step 3.3). Keep this list small and well-defined.
    """
    ROUTE_REQUEST       = "route_request"       # "A12'ye nasıl giderim?"
    FLIGHT_INFO         = "flight_info"          # "Uçağım kaçta?"
    CROWD_QUERY         = "crowd_query"          # "Güvenlik yoğun mu?"
    LOYALTY_QUERY       = "loyalty_query"        # "Puanlarım kaç?"
    GENERAL_INFO        = "general_info"         # "Lounge nerede?"
    NOTIFICATION_QUERY  = "notification_query"   # "Okunmamış bildirimim var mı?"
    TICKET_HELP         = "ticket_help"          # "Bilet nasıl eklerim?"
    UNKNOWN             = "unknown"              # Fallback


@dataclass
class IntentResult:
    """Classifier output.

    Attributes:
        intent: Detected intent label (always set, defaults to UNKNOWN)
        confidence: 0.0-1.0 score (rule-based uses fixed bands)
        entities: Extracted named entities, e.g. {"destination": "Gate A12"}
        raw_message: Original user message (for logging/debugging)
    """
    intent: IntentLabel
    confidence: float
    entities: Dict[str, Any] = field(default_factory=dict)
    raw_message: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return {
            "intent": self.intent.value,
            "confidence": self.confidence,
            "entities": self.entities,
        }


class BaseClassifier(ABC):
    """Abstract classifier — rule-based and ML implementations conform."""

    @abstractmethod
    def classify(self, message: str, locale: str = "tr-TR") -> IntentResult:
        """Classify a user message into an intent.

        Args:
            message: User's natural language input
            locale: BCP-47 locale code (tr-TR or en-US for now)

        Returns:
            IntentResult with intent, confidence, and entities
        """
        raise NotImplementedError

    @property
    @abstractmethod
    def name(self) -> str:
        """Human-readable classifier name (for logging)."""
        raise NotImplementedError
