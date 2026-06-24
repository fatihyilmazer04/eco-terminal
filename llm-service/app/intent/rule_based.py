"""Rule-based intent classifier using keyword matching + regex.

This is the step 3.2 implementation. It works in both Turkish and
English, recognizes 5 active intents (+ unknown fallback), and extracts
simple entities like gate codes and zone names.

Performance characteristics:
    - Latency: ~1ms per classification (no model loading)
    - Accuracy: ~80% on clear queries, drops on ambiguous/colloquial ones
    - Goal: Provide a baseline that DistilBERT (step 4) will improve upon
"""
import logging
import re
from typing import Dict, List, Tuple

from app.intent.base import BaseClassifier, IntentLabel, IntentResult

logger = logging.getLogger(__name__)


class RuleBasedClassifier(BaseClassifier):
    """Keyword + regex-based classifier.

    Each intent has a list of trigger keywords (Turkish + English).
    The classifier scores each intent by counting matched keywords,
    weighted by specificity. Highest-scoring intent wins.
    """

    # ---- Keyword maps (Turkish + English mixed) ----
    # Format: (keyword, weight). Higher weight = stronger signal.

    _ROUTE_KEYWORDS: List[Tuple[str, float]] = [
        ("nasıl giderim", 3.0), ("nereden", 2.0), ("yol", 2.0),
        ("rota", 2.5), ("route", 2.5), ("yönlendir", 2.5),
        ("kısa yol", 3.0), ("shortest", 3.0), ("how do i get", 3.0),
        ("how to reach", 3.0), ("navigate", 2.5), ("directions", 2.5),
        ("gitmek istiyorum", 2.5), ("ulaşmak", 2.0),
    ]

    _FLIGHT_KEYWORDS: List[Tuple[str, float]] = [
        ("uçuş", 2.5), ("uçak", 2.5), ("flight", 2.5),
        ("kalkış", 2.5), ("kalkışı", 2.5), ("kalkıyor", 2.5), ("kalkacak", 2.5), ("kalktı", 2.5),
        ("departure", 2.5), ("varış", 2.0), ("varışı", 2.0),
        ("iniyor", 2.0), ("inecek", 2.0), ("indi", 2.0),
        ("kaçta", 2.0), ("saat kaç", 2.0), ("ne zaman", 1.5),
        ("when does", 2.5), ("what time", 2.5),
        ("kapı", 1.5), ("biniş", 2.0), ("boarding", 2.5),
        ("gecikme", 2.5), ("gecikti", 2.5), ("rötar", 2.5), ("delayed", 2.5),
    ]

    _CROWD_KEYWORDS: List[Tuple[str, float]] = [
        ("yoğun", 2.5), ("kalabalık", 3.0), ("crowded", 3.0),
        ("doluluk", 3.0), ("sıkışık", 2.5), ("busy", 2.5),
        ("ne durumda", 2.0), ("how busy", 3.0), ("density", 2.5),
        ("boş", 2.0), ("empty", 2.0), ("sakin", 2.0),
        # ASCII/accent-free variants
        ("yogun", 2.5), ("kalabalik", 3.0), ("doluluk", 3.0),
        ("sikisik", 2.5), ("en az yogun", 3.0), ("en yogun", 3.0),
        ("bos bolge", 2.5), ("tenha", 2.5),
    ]

    _LOYALTY_KEYWORDS: List[Tuple[str, float]] = [
        ("puan", 3.0), ("points", 3.0), ("ödül", 2.5),
        ("rewards", 2.5), ("cüzdan", 2.5), ("wallet", 2.5),
        ("eco wallet", 3.5), ("eco-cüzdan", 3.5), ("tier", 2.5),
        ("seviye", 2.0), ("bronze", 1.5), ("silver", 1.5),
        ("gold", 1.5), ("kazandı", 1.5), ("earn", 2.0),
    ]

    _GENERAL_KEYWORDS: List[Tuple[str, float]] = [
        ("lounge", 2.0), ("salon", 1.5), ("cafe", 1.5),
        ("restoran", 2.0), ("restaurant", 2.0), ("tuvalet", 2.5),
        ("toilet", 2.5), ("wifi", 2.5), ("wc", 2.5),
        ("nerede", 2.0), ("where is", 2.0), ("var mı", 1.5),
        ("is there", 1.5), ("info", 1.0), ("bilgi", 1.0),
    ]

    _NOTIFICATION_KEYWORDS: List[Tuple[str, float]] = [
        ("bildirim", 3.0), ("bildirimlerim", 3.5), ("bildirimim", 3.5),
        ("okunmamış", 3.5), ("okunmadı", 3.0), ("okunmamış bildirim", 4.0),
        ("uyarı var mı", 3.5), ("uyarılarım", 3.0), ("uyarım var", 3.5),
        ("notification", 3.0), ("notifications", 3.0), ("unread", 3.0),
        ("alert", 2.5), ("alerts", 2.5), ("mesaj var mı", 2.5),
        ("yeni mesaj", 2.5), ("kaç bildirim", 3.5),
    ]

    _TICKET_HELP_KEYWORDS: List[Tuple[str, float]] = [
        ("bilet ekle", 4.0), ("bilet eklemek", 4.0), ("bilet ekleme", 4.0),
        ("bilet nasıl", 3.5), ("bilet nasıl eklenir", 4.0),
        ("yeni bilet", 3.5), ("bilet satın", 3.5), ("bilet al", 3.0),
        ("uçuş ekle", 3.5), ("uçuş eklemek", 3.5), ("uçuş ekleme", 3.5),
        ("uçuşumu ekle", 4.0), ("uçuş nasıl eklenir", 4.0),
        ("add ticket", 3.5), ("new ticket", 3.5), ("book flight", 3.5),
        ("buy ticket", 3.5), ("purchase ticket", 3.5),
        ("how to add", 2.0), ("nasıl eklerim", 3.0), ("nasıl ekleyebilirim", 3.5),
        # ASCII/accent-free variants
        ("bilet nasil", 3.5), ("bilet nasil eklenir", 4.0),
        ("nasil eklerim", 3.0), ("nasil ekleyebilirim", 3.5),
        ("ucus ekle", 3.5), ("ucus eklemek", 3.5), ("ucus ekleme", 3.5),
        ("ucusumu ekle", 4.0), ("ucus nasil eklenir", 4.0),
        ("ucus eklemek istiyorum", 4.0), ("bilet eklemek istiyorum", 4.0),
    ]

    # ---- Entity extraction patterns ----

    # Matches: "Gate A1", "kapı A12", "A1", "Gate-B2", "B-3"
    _GATE_PATTERN = re.compile(
        r"\b(?:gate|kapı|kapi)?[\s\-]?([A-C])[\s\-]?(\d{1,2})\b",
        re.IGNORECASE,
    )

    # Matches: "Security-1", "Güvenlik 1", "security"
    _SECURITY_PATTERN = re.compile(
        r"\b(?:security|güvenlik|guvenlik)[\s\-]?(\d)?\b",
        re.IGNORECASE,
    )

    # Matches: "CheckIn-1", "Check-in 2", "checkin"
    _CHECKIN_PATTERN = re.compile(
        r"\b(?:check[\s\-]?in|checkin)[\s\-]?(\d)?\b",
        re.IGNORECASE,
    )

    # Matches: "Lounge-1", "Lounge 2"
    _LOUNGE_PATTERN = re.compile(
        r"\b(?:lounge|salon)[\s\-]?(\d)?\b",
        re.IGNORECASE,
    )

    # Matches flight code like "TK1922", "PC2345"
    _FLIGHT_CODE_PATTERN = re.compile(r"\b([A-Z]{2})\s?(\d{2,4})\b")

    # Preference signals
    _PREFERENCE_KEYWORDS: Dict[str, str] = {
        "kalabalıksız": "least_crowded",
        "sakin": "least_crowded",
        "en az kalabalık": "least_crowded",
        "least crowded": "least_crowded",
        "kısa": "shortest",
        "en kısa": "shortest",
        "shortest": "shortest",
        "hızlı": "shortest",
        "fastest": "shortest",
    }

    @property
    def name(self) -> str:
        return "rule-based-v1"

    def classify(self, message: str, locale: str = "tr-TR") -> IntentResult:
        """Score each intent, return highest-scoring."""
        if not message or not message.strip():
            return IntentResult(
                intent=IntentLabel.UNKNOWN,
                confidence=0.0,
                entities={},
                raw_message=message,
            )

        msg_lower = message.lower()

        scores = {
            IntentLabel.ROUTE_REQUEST:      self._score(msg_lower, self._ROUTE_KEYWORDS),
            IntentLabel.FLIGHT_INFO:        self._score(msg_lower, self._FLIGHT_KEYWORDS),
            IntentLabel.CROWD_QUERY:        self._score(msg_lower, self._CROWD_KEYWORDS),
            IntentLabel.LOYALTY_QUERY:      self._score(msg_lower, self._LOYALTY_KEYWORDS),
            IntentLabel.GENERAL_INFO:       self._score(msg_lower, self._GENERAL_KEYWORDS),
            IntentLabel.NOTIFICATION_QUERY: self._score(msg_lower, self._NOTIFICATION_KEYWORDS),
            IntentLabel.TICKET_HELP:        self._score(msg_lower, self._TICKET_HELP_KEYWORDS),
        }

        # Uçuş kodu tespit edilirse (XX1234 formatı) flight_info'ya +3.0 puan ekle
        if self._FLIGHT_CODE_PATTERN.search(message):
            scores[IntentLabel.FLIGHT_INFO] += 3.0

        # Pick highest score
        best_intent, best_score = max(scores.items(), key=lambda x: x[1])

        # If best score is too low, fall back to UNKNOWN
        if best_score < 1.5:
            intent = IntentLabel.UNKNOWN
            confidence = 0.3
        else:
            intent = best_intent
            # Confidence: normalize score to [0.5, 0.95] band
            # Rule-based never reaches 1.0 — leave room for DistilBERT
            confidence = min(0.5 + (best_score / 10.0), 0.95)

        entities = self._extract_entities(message)

        result = IntentResult(
            intent=intent,
            confidence=round(confidence, 3),
            entities=entities,
            raw_message=message,
        )

        logger.debug(
            "classified message='%s' -> %s (score=%.2f, conf=%.2f, entities=%s)",
            message[:60], intent.value, best_score, confidence, entities,
        )

        return result

    def _score(self, msg_lower: str, keywords: List[Tuple[str, float]]) -> float:
        """Sum weights of all matching keywords."""
        total = 0.0
        for kw, weight in keywords:
            if kw in msg_lower:
                total += weight
        return total

    def _extract_entities(self, message: str) -> Dict[str, str]:
        """Extract named entities (gates, zones, flight codes, preferences)."""
        entities: Dict[str, str] = {}

        # Gate detection
        m = self._GATE_PATTERN.search(message)
        if m:
            letter = m.group(1).upper()
            number = m.group(2)
            entities["destination"] = f"Gate {letter}{number}"
            entities["destination_type"] = "gate"

        # Security zone
        m = self._SECURITY_PATTERN.search(message)
        if m and "destination" not in entities:
            num = m.group(1) or "1"
            entities["destination"] = f"Security-{num}"
            entities["destination_type"] = "security"

        # CheckIn zone
        m = self._CHECKIN_PATTERN.search(message)
        if m and "destination" not in entities:
            num = m.group(1) or "1"
            entities["destination"] = f"CheckIn-{num}"
            entities["destination_type"] = "checkin"

        # Lounge zone
        m = self._LOUNGE_PATTERN.search(message)
        if m and "destination" not in entities:
            num = m.group(1) or "1"
            entities["destination"] = f"Lounge-{num}"
            entities["destination_type"] = "lounge"

        # Flight code
        m = self._FLIGHT_CODE_PATTERN.search(message)
        if m:
            entities["flight_code"] = f"{m.group(1)}{m.group(2)}"

        # Route preference
        msg_lower = message.lower()
        for kw, pref in self._PREFERENCE_KEYWORDS.items():
            if kw in msg_lower:
                entities["route_preference"] = pref
                break

        return entities
