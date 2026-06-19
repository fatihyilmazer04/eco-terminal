"""Build intent-aware Gemini prompts.

The prompt is the most important piece of RAG — it tells Gemini:
    1. Its role (helpful airport assistant)
    2. Constraints (Turkish, concise, no hallucination)
    3. Retrieved context (facts + live data)
    4. User's actual question
"""
from app.intent import IntentLabel, IntentResult
from app.rag.retriever import RetrievedContext


_SYSTEM_PROMPT = """Sen Eco-Terminal havalimanı asistanısın. Görevin yolcuların
sorularını yanıtlamak: rota önerme, uçuş bilgisi, yoğunluk durumu, eco-puan,
genel terminal bilgileri.

KURALLAR:
- Türkçe cevap ver, samimi ve profesyonel ol.
- 3-5 cümleden uzun yazma — yolcular kısa cevap ister.
- Sadece sağlanan veriyi kullan, uydurma.
- Eğer veri yetersizse "bu bilgiye şu an erişemiyorum" de.
- Asla iletişim bilgisi, telefon numarası uydurma.
- Emoji KULLANMA.
- Rota verirken adımları sırayla say: "1. ... 2. ..." formatında.
"""


class PromptBuilder:
    """Compose final prompt from intent + retrieved context."""

    def build(self, intent_result: IntentResult, context: RetrievedContext) -> str:
        """Returns full prompt ready to send to Gemini."""
        sections = [_SYSTEM_PROMPT, ""]

        # --- Retrieved context block ---
        sections.append("BAGLAN:")

        if context.facts:
            sections.append("Terminal Bilgileri:")
            for i, fact in enumerate(context.facts, 1):
                sections.append(f"  {i}. {fact.text}")
            sections.append("")

        if context.route_data:
            sections.append("Onerilen Rota (Dijkstra hesaplamasi):")
            sections.append(self._format_route(context.route_data))
            sections.append("")

        if context.flight_data:
            sections.append("Ucus Bilgisi:")
            sections.append(self._format_flight(context.flight_data))
            sections.append("")

        if context.zone_status:
            sections.append("Zone Durumu:")
            sections.append(self._format_zone(context.zone_status))
            sections.append("")

        if context.heatmap:
            sections.append("Genel Yogunluk Haritasi:")
            sections.append(self._format_heatmap(context.heatmap))
            sections.append("")

        # --- Intent + entities ---
        sections.append(f"Tespit edilen niyet: {intent_result.intent.value}")
        if intent_result.entities:
            sections.append(f"Cikarilan bilgiler: {intent_result.entities}")
        sections.append("")

        # --- The actual question ---
        sections.append(f"YOLCU SORUSU: {intent_result.raw_message}")
        sections.append("")
        sections.append("CEVABIN:")

        return "\n".join(sections)

    # ---- Formatters ----

    def _format_route(self, route_data: dict) -> str:
        """Format Dijkstra output as readable text."""
        alternatives = route_data.get("alternatives", [])
        if not alternatives:
            return "  (rota bulunamadi)"

        lines = []
        for alt in alternatives[:3]:
            strategy = alt.get("strategy", "?")
            walk_sec = alt.get("totalWalkSeconds", 0)
            walk_min = round(walk_sec / 60.0, 1)
            dist = alt.get("totalDistanceMeters", 0)
            density = alt.get("avgDensity", 0)
            steps = alt.get("steps", [])
            step_names = " -> ".join(s.get("zoneName", "?") for s in steps)

            lines.append(
                f"  * {strategy}: {step_names} "
                f"({walk_min} dk, {dist}m, ort. yogunluk %{int(density * 100)})"
            )
        return "\n".join(lines)

    def _format_flight(self, flight: dict) -> str:
        code = flight.get("flightCode", "?")
        dest = flight.get("destination", "?")
        dep = flight.get("departureTime", "?")
        gate = flight.get("gate", "?")
        status = flight.get("status", "?")
        return f"  * {code} -> {dest} | Kalkis: {dep} | Kapi: {gate} | Durum: {status}"

    def _format_zone(self, zone: dict) -> str:
        name = zone.get("zoneName", "?")
        density = zone.get("densityPct", 0)
        level = zone.get("densityLevel", "?")
        return f"  * {name}: %{density} doluluk ({level})"

    def _format_heatmap(self, zones: list) -> str:
        lines = []
        for z in zones[:8]:
            name = z.get("zoneName", "?")
            density = z.get("densityPct", 0)
            lines.append(f"  * {name}: %{density}")
        if len(zones) > 8:
            lines.append(f"  * ... ve {len(zones) - 8} zone daha")
        return "\n".join(lines)
