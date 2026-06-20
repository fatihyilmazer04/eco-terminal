"""Intent-aware prompt builder for local HuggingFace model.

Combines:
  1. System role + constraints
  2. User-specific context from Java backend (flights, eco points, zones)
  3. RAG-retrieved context (facts, route, heatmap)
  4. The actual question
"""
from typing import Optional, List, Dict, Any

from app.intent import IntentResult
from app.rag.retriever import RetrievedContext


_SYSTEM_PROMPT = """Sen Eco-Terminal havalimanı asistanısın. Yolcuların sorularını Türkçe yanıtlarsın.

KURALLAR:
- Kısa ve net cevap ver (maksimum 3-4 cümle).
- Sadece aşağıda verilen verileri kullan, uydurma.
- Veri yoksa "Bu bilgiye şu an erişemiyorum." de.
- Emoji KULLANMA.
- Telefon numarası veya iletişim bilgisi uydurma.
"""


class PromptBuilder:
    """Compose final prompt from intent + retrieved context + request context."""

    def build(
        self,
        intent_result: IntentResult,
        context: RetrievedContext,
        req=None,  # ChatRequest — kullanıcıya özgü veriler
    ) -> str:
        sections = [_SYSTEM_PROMPT.strip(), ""]

        # ── Kullanıcıya özgü veriler (Java backend'den) ────────────────────────
        user_section = self._build_user_section(req)
        if user_section:
            sections.append("KULLANICI BİLGİLERİ:")
            sections.append(user_section)
            sections.append("")

        # ── RAG context ────────────────────────────────────────────────────────
        rag_section = self._build_rag_section(context)
        if rag_section:
            sections.append("TERMINAL VERİLERİ:")
            sections.append(rag_section)
            sections.append("")

        # ── Soru ──────────────────────────────────────────────────────────────
        sections.append(f"YOLCUNUN SORUSU: {intent_result.raw_message}")
        sections.append("")
        sections.append("CEVAP:")

        return "\n".join(sections)

    # ── Kullanıcı verisi ───────────────────────────────────────────────────────

    def _build_user_section(self, req) -> str:
        if req is None:
            return ""

        lines = []

        # Eco puan
        if getattr(req, "eco_points", None) is not None:
            tier = getattr(req, "tier_level", "GREEN") or "GREEN"
            lines.append(f"  Eco-Puan: {req.eco_points} ({tier} üye)")

        # Okunmamış bildirim
        unread = getattr(req, "unread_notification_count", None)
        if unread:
            lines.append(f"  Okunmamış bildirim: {unread}")

        # Aktif uçuşlar
        flights = getattr(req, "user_flights", None)
        if flights:
            lines.append("  Aktif uçuşlar:")
            for f in flights:
                code = f.get("code", "?")
                dest = f.get("destination", "?")
                dep  = f.get("departure_time", "?")
                gate = f.get("gate", "Henüz atanmadı")
                stat = f.get("status", "?")
                lines.append(f"    - {code} → {dest} | Kalkış: {dep} | Kapı: {gate} | Durum: {stat}")
        else:
            lines.append("  Aktif uçuş bulunamadı.")

        # Yoğun bölgeler
        hot = getattr(req, "hot_zones", None)
        if hot:
            lines.append("  Yoğun bölgeler:")
            for z in hot[:3]:
                lines.append(f"    - {z.get('name','?')} (%{z.get('density_pct',0)} dolu)")

        # Sakin bölgeler
        quiet = getattr(req, "quiet_zones", None)
        if quiet:
            lines.append("  Sakin bölgeler:")
            for z in quiet[:3]:
                lines.append(f"    - {z.get('name','?')} (%{z.get('density_pct',0)} dolu)")

        # Ortalama doluluk
        avg = getattr(req, "avg_density_pct", None)
        if avg is not None and avg >= 0:
            lines.append(f"  Terminal ortalama doluluk: %{avg}")

        return "\n".join(lines)

    # ── RAG context ────────────────────────────────────────────────────────────

    def _build_rag_section(self, context: RetrievedContext) -> str:
        lines = []

        if context.facts:
            lines.append("  Bilgi tabanı:")
            for fact in context.facts[:5]:
                lines.append(f"    - {fact.text}")

        if context.route_data:
            lines.append("  Önerilen rota:")
            lines.append(self._format_route(context.route_data))

        if context.flight_data:
            lines.append("  Uçuş bilgisi:")
            lines.append(self._format_flight(context.flight_data))

        if context.zone_status:
            lines.append("  Zone durumu:")
            lines.append(self._format_zone(context.zone_status))

        if context.heatmap:
            lines.append("  Yoğunluk haritası:")
            lines.append(self._format_heatmap(context.heatmap))

        return "\n".join(lines)

    # ── Formatters ─────────────────────────────────────────────────────────────

    def _format_route(self, route_data: dict) -> str:
        alternatives = route_data.get("alternatives", [])
        if not alternatives:
            return "    (rota bulunamadı)"
        lines = []
        for alt in alternatives[:2]:
            strategy = alt.get("strategy", "?")
            walk_min = round(alt.get("totalWalkSeconds", 0) / 60.0, 1)
            steps = alt.get("steps", [])
            step_names = " → ".join(s.get("zoneName", "?") for s in steps)
            lines.append(f"    {strategy}: {step_names} ({walk_min} dk)")
        return "\n".join(lines)

    def _format_flight(self, flight: dict) -> str:
        code = flight.get("flightCode", "?")
        dest = flight.get("destination", "?")
        dep  = flight.get("departureTime", "?")
        gate = flight.get("gate", "?")
        status = flight.get("status", "?")
        return f"    {code} → {dest} | Kalkış: {dep} | Kapı: {gate} | Durum: {status}"

    def _format_zone(self, zone: dict) -> str:
        name    = zone.get("zoneName", "?")
        density = zone.get("densityPct", 0)
        level   = zone.get("densityLevel", "?")
        return f"    {name}: %{density} ({level})"

    def _format_heatmap(self, zones: list) -> str:
        lines = []
        for z in zones[:6]:
            lines.append(f"    - {z.get('zoneName','?')}: %{z.get('densityPct',0)}")
        return "\n".join(lines)
