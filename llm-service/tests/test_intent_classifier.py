"""Smoke tests for the rule-based intent classifier."""
from app.intent import RuleBasedClassifier, IntentLabel


def test_route_request_with_gate():
    clf = RuleBasedClassifier()
    result = clf.classify("A12 kapısına nasıl giderim?")
    assert result.intent == IntentLabel.ROUTE_REQUEST
    assert result.entities.get("destination") == "Gate A12"
    assert result.confidence >= 0.7


def test_flight_info():
    clf = RuleBasedClassifier()
    result = clf.classify("TK1922 uçuşu kaçta kalkıyor?")
    assert result.intent == IntentLabel.FLIGHT_INFO
    assert result.entities.get("flight_code") == "TK1922"


def test_crowd_query():
    clf = RuleBasedClassifier()
    result = clf.classify("Güvenlik kontrolü çok yoğun mu şu an?")
    assert result.intent == IntentLabel.CROWD_QUERY


def test_loyalty_query():
    clf = RuleBasedClassifier()
    result = clf.classify("Eco puanlarım kaç oldu?")
    assert result.intent == IntentLabel.LOYALTY_QUERY


def test_unknown_fallback():
    clf = RuleBasedClassifier()
    result = clf.classify("merhaba nasılsın")
    assert result.intent == IntentLabel.UNKNOWN
    assert result.confidence < 0.5


def test_route_preference_least_crowded():
    clf = RuleBasedClassifier()
    result = clf.classify("A12'ye en kalabalıksız yoldan nasıl giderim?")
    assert result.intent == IntentLabel.ROUTE_REQUEST
    assert result.entities.get("route_preference") == "least_crowded"
    assert result.entities.get("destination") == "Gate A12"
