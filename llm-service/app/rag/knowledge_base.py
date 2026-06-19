"""Static knowledge base — facts about the terminal.

In a production system this would be vector-indexed (sentence-transformers
+ FAISS), but the corpus is small enough (15 zones, ~30 facts) that
simple keyword matching gives near-perfect recall at zero latency.

Step 4 may upgrade this to embeddings if we add more long-form content.
"""
from dataclasses import dataclass
from typing import List, Dict


@dataclass(frozen=True)
class Fact:
    """A single retrievable fact."""
    topic: str           # e.g. "gate_a1", "lounge_1"
    category: str        # zone | service | policy
    text: str            # Human-readable description
    keywords: List[str]  # Trigger words for retrieval


class KnowledgeBase:
    """In-memory knowledge base — Turkish + English facts."""

    _FACTS: List[Fact] = [
        # ---- Zones ----
        Fact(
            topic="checkin_zones",
            category="zone",
            text=(
                "Eco-Terminal'de 3 check-in alanı vardır: CheckIn-1, CheckIn-2, "
                "CheckIn-3. Her birinin kapasitesi yaklaşık 450 yolcudur. "
                "Sol giriş kolonunda sıralanmıştır."
            ),
            keywords=["checkin", "check-in", "kayıt", "bagaj teslim"],
        ),
        Fact(
            topic="security_zones",
            category="zone",
            text=(
                "İki güvenlik kontrolü vardır: Security-1 (üst koridor) ve "
                "Security-2 (alt koridor). Security-2 alternatif rota için "
                "kullanılabilir; iki kontrol noktası birbirinin yedeğidir."
            ),
            keywords=["güvenlik", "security", "kontrol", "x-ray"],
        ),
        Fact(
            topic="lounges",
            category="zone",
            text=(
                "İki lounge mevcuttur: Lounge-1 (terminal üst bölüm, A "
                "konkursuna yakın) ve Lounge-2 (terminal alt bölüm, C "
                "konkursuna yakın). Her ikisi de tüm 8 gate'e bağlıdır."
            ),
            keywords=["lounge", "salon", "bekleme", "vip"],
        ),
        Fact(
            topic="gates_a",
            category="zone",
            text=(
                "A konkursu 3 gate içerir: Gate A1, A2, A3. Lounge-1'e "
                "yakındır (95-215 metre). En kısa yol genellikle "
                "Security-1 → Lounge-1 üzerindendir."
            ),
            keywords=["gate a", "a1", "a2", "a3", "a konkurs"],
        ),
        Fact(
            topic="gates_b",
            category="zone",
            text=(
                "B konkursu 2 gate içerir: Gate B1, B2. Terminal merkezinde, "
                "her iki lounge'dan eşit mesafede konumlanmıştır."
            ),
            keywords=["gate b", "b1", "b2", "b konkurs"],
        ),
        Fact(
            topic="gates_c",
            category="zone",
            text=(
                "C konkursu 3 gate içerir: Gate C1, C2, C3. Lounge-2'ye "
                "yakındır. En kısa yol genellikle Security-2 → Lounge-2 "
                "üzerindendir."
            ),
            keywords=["gate c", "c1", "c2", "c3", "c konkurs"],
        ),

        # ---- Services ----
        Fact(
            topic="moving_walkway",
            category="service",
            text=(
                "Bazı rotalarda yürüyen bant mevcuttur (özellikle CheckIn → "
                "Security ve Lounge → Gate bağlantılarında). Bandı "
                "kullanmak yürüyüş süresini %25-30 azaltır."
            ),
            keywords=["yürüyen bant", "moving walkway", "bant"],
        ),
        Fact(
            topic="escalator_elevator",
            category="service",
            text=(
                "Security → Lounge bağlantılarında yürüyen merdiven, "
                "çapraz rotalarda asansör mevcuttur. Engelli erişimi "
                "tüm rotalarda sağlanır."
            ),
            keywords=["asansör", "merdiven", "engelli", "elevator", "escalator"],
        ),

        # ---- Loyalty ----
        Fact(
            topic="eco_loyalty",
            category="service",
            text=(
                "Eco-cüzdan programı: Eco-dostu rota tamamladıkça puan "
                "kazanırsınız. 4 seviye vardır: Bronze, Silver, Gold, "
                "Platinum. Puanlarla lounge erişimi, indirim kuponları "
                "ve uçuş upgrade'leri alabilirsiniz."
            ),
            keywords=["puan", "loyalty", "ödül", "eco", "tier", "cüzdan"],
        ),

        # ---- Policy ----
        Fact(
            topic="route_recommendation",
            category="policy",
            text=(
                "Sistem her zaman 3 alternatif rota sunar: "
                "(1) En kısa süre, (2) En az kalabalık, (3) Dengeli. "
                "Yoğunluk verisi AI tahminlerinden gelir, 5 dakikada bir "
                "güncellenir."
            ),
            keywords=["rota", "öneri", "alternatif", "tahmin"],
        ),
    ]

    def search(
        self,
        query: str,
        intent: str = "",
        entities: dict = None,
        top_k: int = 3,
    ) -> List[Fact]:
        """Find relevant facts via keyword matching.

        Scoring:
            +3 per keyword match in query
            +2 per keyword match in entities (destination, etc.)
            +1 if category matches intent (zone for route_request, etc.)
        """
        if entities is None:
            entities = {}

        query_lower = query.lower()
        entity_text = " ".join(str(v).lower() for v in entities.values())

        scores: List[tuple] = []
        for fact in self._FACTS:
            score = 0
            for kw in fact.keywords:
                if kw.lower() in query_lower:
                    score += 3
                if kw.lower() in entity_text:
                    score += 2

            # Intent affinity bonus
            if intent == "route_request" and fact.category == "zone":
                score += 1
            elif intent == "loyalty_query" and "loyalty" in fact.topic:
                score += 2

            if score > 0:
                scores.append((score, fact))

        scores.sort(key=lambda x: x[0], reverse=True)
        return [fact for _, fact in scores[:top_k]]
