"""Akıllı şablon motoru — Gemini olmadan da doğal cevaplar üretir.

Her intent için elindeki gerçek veriyi (Dijkstra rotası, Eco-puan,
uçuş bilgisi, zone yoğunluğu, KB gerçekleri) kullanarak akıcı
Türkçe cümleler kurar.

Sadece Gemini başarısız olduğunda (veya yapılandırılmadığında)
devreye girer. Gemini çalışırsa bu modül atlanır.
"""
from __future__ import annotations

import logging
import random
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


# ── Rota şablonları ───────────────────────────────────────────────────────────

def _format_route_reply(route_data: Dict[str, Any]) -> Optional[str]:
    """
    Dijkstra rotasını doğal bir cümleye dönüştürür.
    route_data = {"alternatives": [{"steps": [...], ...}]}
    Her adım: {"stepNumber": N, "zoneName": "...", "estimatedWalkMinutes": N}
    """
    alternatives = route_data.get("alternatives", [])
    if not alternatives:
        return None

    best = alternatives[0]
    steps: List[Dict] = best.get("steps", [])
    if not steps:
        return None

    zone_names = [s.get("zoneName", "") for s in steps if s.get("zoneName")]
    if not zone_names:
        return None

    total_mins = sum(s.get("estimatedWalkMinutes", 0) for s in steps)
    destination = zone_names[-1]

    if len(zone_names) == 1:
        return f"{destination}'e zaten yakınsınız, doğrudan geçebilirsiniz."

    if len(zone_names) == 2:
        return (
            f"{zone_names[0]}'den {destination}'e doğrudan geçebilirsiniz. "
            f"Tahmini süre: {total_mins} dakika."
        )

    middle = " → ".join(zone_names[1:-1])
    variants = [
        f"{zone_names[0]}'den başlayıp {middle} üzerinden {destination}'e "
        f"yaklaşık {total_mins} dakikada ulaşabilirsiniz.",

        f"Önerilen rota: {' → '.join(zone_names)}. "
        f"Toplam yürüyüş süresi yaklaşık {total_mins} dakika.",

        f"{destination}'e gitmek için {middle} güzergahını kullanabilirsiniz. "
        f"Bu rota yaklaşık {total_mins} dakika sürer.",
    ]
    return random.choice(variants)


def generate_route_reply(route_data: Optional[Dict[str, Any]]) -> str:
    if not route_data:
        return (
            "Rota hesaplamak için hedef bölgeyi belirtmeniz gerekiyor. "
            "Örneğin: 'Gate A1'e nasıl giderim?'"
        )
    reply = _format_route_reply(route_data)
    if reply:
        return reply
    return (
        "Rotanızı hesapladım ancak adım bilgilerini çıkaramadım. "
        "Lütfen 'Rota Öner' sayfasından deneyin."
    )


# ── Loyalty şablonları ────────────────────────────────────────────────────────

_TIER_TR = {
    "BRONZE":   "Bronze",
    "SILVER":   "Silver",
    "GOLD":     "Gold",
    "PLATINUM": "Platinum",
    "GREEN":    "Yeşil Üye",
}

_TIER_NEXT = {
    "BRONZE":   ("Silver", 500),
    "SILVER":   ("Gold", 1500),
    "GOLD":     ("Platinum", 3000),
    "PLATINUM": (None, 0),
    "GREEN":    ("Bronze", 200),
}


def generate_loyalty_reply(
    eco_points: Optional[int],
    tier_level: Optional[str],
) -> str:
    if eco_points is None:
        return (
            "Eco-puan bilgilerinize ulaşamadım. "
            "Lütfen 'Ödüller' sayfasından cüzdanınıza bakabilirsiniz."
        )

    tier_key  = (tier_level or "GREEN").upper()
    tier_name = _TIER_TR.get(tier_key, tier_key.capitalize())
    next_tier, threshold = _TIER_NEXT.get(tier_key, (None, 0))

    if next_tier is None:
        # En üst seviye
        variants = [
            f"Şu anda {eco_points} Eco-Puanınız var ve en yüksek seviye olan Platinum üyesisiniz! 🏆",
            f"Tebrikler! Platinum seviyesindesiniz ve {eco_points} Eco-Puanınız mevcut.",
        ]
    elif eco_points >= threshold:
        # Yeterli puan var, bir sonraki tier'a geçebilir
        variants = [
            f"Şu anda {eco_points} Eco-Puanınız var ve {tier_name} üyesisiniz. "
            f"{next_tier} seviyesine geçmeye yetecek puanınız var!",
        ]
    else:
        remaining = threshold - eco_points
        variants = [
            f"Şu anda {eco_points} Eco-Puanınız var ve {tier_name} üyesisiniz. "
            f"{next_tier} seviyesine {remaining} puan kaldı.",

            f"{tier_name} üyesi olarak {eco_points} puanınız var. "
            f"Bir sonraki seviye {next_tier} için {remaining} puan daha kazanmanız gerekiyor.",
        ]

    return random.choice(variants)


# ── Uçuş şablonları ───────────────────────────────────────────────────────────

_SAYI_TR = ["", "Bir", "İki", "Üç", "Dört", "Beş", "Altı", "Yedi", "Sekiz", "Dokuz", "On"]


def _format_single_flight(f: Dict[str, Any]) -> str:
    """Tek bir uçuş sözlüğünü doğal Türkçe cümleye dönüştürür."""
    code     = f.get("code", "")
    dest     = f.get("destination", "")
    gate     = f.get("gate", "")
    dep_time = f.get("departure_time") or f.get("departureTime", "")
    status   = f.get("status", "")

    parts = []
    if code:
        parts.append(f"{code} uçuşunuz")
    if dest:
        parts.append(f"{dest}'e")
    if dep_time:
        parts.append(f"{dep_time}'de kalkacak")
    if gate:
        parts.append(f"ve {gate} kapısından biniş yapacaksınız")
    if status and status not in ("Planlandı", "SCHEDULED"):
        parts.append(f"(Durum: {status})")
    return " ".join(parts) + "." if parts else ""


def _format_multiple_flights(flights: List[Dict[str, Any]]) -> str:
    """
    Birden fazla uçuşu doğal Türkçe olarak listeler.
    Örnek (2 uçuş):
        "İki uçuşunuz var: TK1922 İstanbul'a 14:35'te A12 kapısından kalkacak,
         TK2087 ise Ankara'ya 18:50'de B7 kapısından kalkacak."
    Örnek (3+ uçuş): madde madde liste.
    """
    count = len(flights)
    sayi  = _SAYI_TR[count] if count < len(_SAYI_TR) else str(count)

    if count == 2:
        f1 = flights[0]
        f2 = flights[1]

        def _desc(f: Dict[str, Any]) -> str:
            code     = f.get("code", "")
            dest     = f.get("destination", "")
            dep_time = f.get("departure_time") or f.get("departureTime", "")
            gate     = f.get("gate", "")
            status   = f.get("status", "")
            parts = []
            if code:
                parts.append(code)
            if dest:
                parts.append(f"{dest}'a" if not dest.endswith(("a", "e", "ı", "i", "o", "ö", "u", "ü")) else f"{dest}'e")
            if dep_time:
                parts.append(f"{dep_time}'de kalkacak")
            if gate:
                parts.append(f"({gate} kapısı)")
            if status and status not in ("Planlandı", "SCHEDULED"):
                parts.append(f"[{status}]")
            return " ".join(parts)

        return f"{sayi} uçuşunuz var: {_desc(f1)}, {_desc(f2)}."

    # 3 veya daha fazla uçuş — madde madde
    lines = [f"{sayi} uçuşunuz var:"]
    for i, f in enumerate(flights, start=1):
        code     = f.get("code", "")
        dest     = f.get("destination", "")
        dep_time = f.get("departure_time") or f.get("departureTime", "")
        gate     = f.get("gate", "")
        status   = f.get("status", "")
        parts = []
        if code:
            parts.append(code)
        if dest:
            parts.append(f"→ {dest}")
        if dep_time:
            parts.append(f"| {dep_time}")
        if gate:
            parts.append(f"| {gate} kapısı")
        if status and status not in ("Planlandı", "SCHEDULED"):
            parts.append(f"[{status}]")
        lines.append(f"  {i}. {' '.join(parts)}")
    return "\n".join(lines)


def generate_flight_reply(
    flight_data: Optional[Dict[str, Any]],
    user_flights: Optional[List[Dict[str, Any]]],
    entities: Optional[Dict[str, Any]] = None,
) -> str:
    # Önce Python retriever'ın çektiği uçuş verisini dene
    if flight_data:
        data = flight_data.get("data", flight_data)  # ApiResponse wrapper'ı aç
        code        = data.get("flightCode") or data.get("flight_code") or data.get("code", "")
        destination = data.get("destination", "")
        gate        = data.get("gateName") or data.get("gate", "")
        dep_time    = data.get("departureTime") or data.get("departure_time", "")
        status      = data.get("status", "")

        if code and destination:
            parts = [f"{code} uçuşunuz"]
            if destination:
                parts.append(f"{destination}'e")
            if dep_time:
                # ISO timestamp ise sadece saat kısmını al
                if "T" in str(dep_time):
                    try:
                        dep_time = str(dep_time).split("T")[1][:5]
                    except Exception:
                        pass
                parts.append(f"{dep_time}'de kalkacak")
            if gate:
                parts.append(f"ve {gate} kapısından biniş yapacaksınız")
            if status and status not in ("SCHEDULED", "Planlandı"):
                parts.append(f"(Durum: {status})")
            return " ".join(parts) + "."

    # Java ChatContext'ten gelen kullanıcı uçuşları
    if user_flights:
        flight_code = (entities or {}).get("flight_code", "").upper() if entities else ""

        # Belirli bir uçuş kodu sorgulanıyorsa sadece o uçuşu göster
        if flight_code:
            matched = next(
                (f for f in user_flights if f.get("code", "").upper() == flight_code),
                None,
            )
            if matched:
                result = _format_single_flight(matched)
                if result:
                    return result

        # Genel soru: tek uçuş varsa tekil, birden fazla varsa hepsini listele
        if len(user_flights) == 1:
            result = _format_single_flight(user_flights[0])
            if result:
                return result
        else:
            return _format_multiple_flights(user_flights)

    return (
        "Kayıtlı bir uçuşunuz görünmüyor. Biletlerinizi 'Uçuşlarım' "
        "sayfasından görüntüleyebilirsiniz."
    )


# ── Yoğunluk şablonları ───────────────────────────────────────────────────────

def _density_desc(pct: float) -> str:
    if pct < 25:
        return "oldukça sakin"
    if pct < 50:
        return "orta yoğunlukta"
    if pct < 75:
        return "kalabalık"
    return "çok kalabalık"


def generate_crowd_reply(
    zone_status: Optional[Dict[str, Any]],
    heatmap: Optional[List[Dict[str, Any]]],
    hot_zones: Optional[List[Dict[str, Any]]],
    quiet_zones: Optional[List[Dict[str, Any]]],
    avg_density_pct: Optional[int],
    entities: Optional[Dict[str, Any]] = None,
) -> str:
    destination = (entities or {}).get("destination", "")

    # Belirli bir zone sorgusu
    if destination and zone_status:
        data = zone_status.get("data", zone_status)
        density = data.get("densityPct") or data.get("density_pct") or 0
        pct = round(density * 100) if density <= 1.0 else round(density)
        desc = _density_desc(pct)
        return f"{destination} şu anda %{pct} doluluk oranıyla {desc}."

    # Heatmap'ten belirli zone ara
    if destination and heatmap:
        for zone in heatmap:
            zones_data = zone.get("zones", [zone]) if "zones" in zone else [zone]
            for z in zones_data:
                name = z.get("zoneName") or z.get("name") or ""
                if destination.lower() in name.lower():
                    density = z.get("currentDensity") or z.get("densityPct") or 0
                    pct = round(density * 100) if density <= 1.0 else round(density)
                    desc = _density_desc(pct)
                    return f"{name} şu anda %{pct} doluluk oranıyla {desc}."

    # Genel yoğunluk durumu (Java'dan gelen hot/quiet zones)
    if hot_zones or quiet_zones or avg_density_pct is not None:
        parts = []
        if avg_density_pct is not None:
            parts.append(f"Terminal genelinde ortalama doluluk %{avg_density_pct}.")
        if hot_zones:
            hot_names = ", ".join(z.get("name", "") for z in hot_zones[:3])
            parts.append(f"En kalabalık bölgeler: {hot_names}.")
        if quiet_zones:
            quiet_names = ", ".join(z.get("name", "") for z in quiet_zones[:3])
            parts.append(f"En sakin bölgeler: {quiet_names}.")
        if parts:
            return " ".join(parts)

    return (
        "Hangi bölgenin yoğunluğunu öğrenmek istediğinizi belirtir misiniz? "
        "Örneğin: 'Security-1 kalabalık mı?'"
    )


# ── Genel bilgi şablonları ────────────────────────────────────────────────────

def generate_general_info_reply(facts: List[Any]) -> str:
    if facts:
        # En alakalı gerçeği döndür
        best_fact = facts[0]
        text = best_fact.text if hasattr(best_fact, "text") else str(best_fact)
        return text

    variants = [
        "Bu konuda elimde detaylı bilgi yok. Size terminal içi yönlendirme, "
        "uçuş bilgisi veya yoğunluk durumu konularında yardımcı olabilirim.",

        "Tam bilgiye ulaşamadım. Rota önerisi, zone yoğunluğu veya "
        "Eco-Puan durumunuzu sormaktan çekinmeyin.",
    ]
    return random.choice(variants)


# ── Bildirim şablonları ───────────────────────────────────────────────────────

def generate_notification_reply(unread_count: Optional[int]) -> str:
    if unread_count is None:
        return (
            "Bildirim bilgilerinize şu an erişemiyorum. "
            "Lütfen 'Bildirimler' sayfasından kontrol edin."
        )
    if unread_count == 0:
        return "Şu anda okunmamış bildiriminiz yok."
    return f"Şu anda {unread_count} okunmamış bildiriminiz var."


# ── Bilet ekleme yönlendirmesi ────────────────────────────────────────────────

def generate_ticket_help_reply() -> str:
    return (
        "Bilet eklemek için sol menüden 'Uçuşlar' sayfasına gidin. "
        "Orada mevcut uçuşları görüntüleyebilir ve bilet ekleyebilirsiniz."
    )


# ── Bilinmeyen intent ─────────────────────────────────────────────────────────

def generate_unknown_reply() -> str:
    variants = [
        "Üzgünüm, sorunuzu anlayamadım. Rota, uçuş, yoğunluk veya "
        "Eco-Puan konularında yardımcı olabilirim.",

        "Bu konuda yardımcı olamıyorum. Rota önerisi almak, uçuş bilgisi "
        "sorgulamak veya terminal yoğunluğunu öğrenmek için buradayım.",

        "Tam olarak anlayamadım. Terminal içi yönlendirme, uçuş durumu "
        "veya bekleme salonu önerisi için soru sorabilirsiniz.",
    ]
    return random.choice(variants)


# ── Ana giriş noktası ─────────────────────────────────────────────────────────

def get_smart_reply(
    intent: str,
    context,                           # RetrievedContext
    entities: Dict[str, Any],
    # Java'dan gelen ChatContext verileri
    eco_points: Optional[int] = None,
    tier_level: Optional[str] = None,
    user_flights: Optional[List[Dict[str, Any]]] = None,
    hot_zones: Optional[List[Dict[str, Any]]] = None,
    quiet_zones: Optional[List[Dict[str, Any]]] = None,
    avg_density_pct: Optional[int] = None,
    unread_notification_count: Optional[int] = None,
) -> str:
    """
    Intent'e göre elindeki gerçek veriyi kullanarak doğal bir yanıt üretir.
    Gemini başarısız olduğunda bu fonksiyon çağrılır.
    """
    logger.debug(
        "template_engine intent=%s eco_points=%s route=%s flight=%s",
        intent, eco_points, bool(context.route_data), bool(context.flight_data),
    )

    if intent == "route_request":
        return generate_route_reply(context.route_data)

    if intent == "loyalty_query":
        return generate_loyalty_reply(eco_points, tier_level)

    if intent == "flight_info":
        return generate_flight_reply(
            context.flight_data,
            user_flights,
            entities,
        )

    if intent == "crowd_query":
        return generate_crowd_reply(
            context.zone_status,
            context.heatmap,
            hot_zones,
            quiet_zones,
            avg_density_pct,
            entities,
        )

    if intent == "general_info":
        return generate_general_info_reply(context.facts)

    if intent == "notification_query":
        return generate_notification_reply(unread_notification_count)

    if intent == "ticket_help":
        return generate_ticket_help_reply()

    return generate_unknown_reply()
