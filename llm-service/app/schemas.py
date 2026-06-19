"""Pydantic models for request/response validation."""
from typing import Any, Dict, Optional, List, Literal
from pydantic import BaseModel, Field


# ---------- Common ----------

class HealthResponse(BaseModel):
    """Service health and readiness."""
    status: Literal["ok", "degraded", "down"] = "ok"
    service: str
    version: str
    intent_classifier_enabled: bool
    rag_enabled: bool


# ---------- Chat ----------

class ChatRequest(BaseModel):
    """Incoming question from the user."""
    user_id: Optional[int] = Field(
        None, description="Authenticated user ID (passed from backend)"
    )
    message: str = Field(
        ..., min_length=1, max_length=500,
        description="Natural language query, e.g. 'A12 kapıya nasıl giderim?'"
    )
    session_id: Optional[str] = Field(
        None, description="Conversation session for multi-turn context"
    )
    locale: str = Field(default="tr-TR", description="User locale (tr-TR | en-US)")

    # Java ChatContext verileri — akıllı template engine için
    eco_points: Optional[int] = Field(None, description="Kullanıcının Eco-Puanı")
    tier_level: Optional[str] = Field(None, description="Loyalty seviyesi (BRONZE/SILVER/GOLD/PLATINUM)")
    user_flights: Optional[List[Dict[str, Any]]] = Field(None, description="Kullanıcının aktif uçuşları")
    hot_zones: Optional[List[Dict[str, Any]]] = Field(None, description="Yoğun bölgeler")
    quiet_zones: Optional[List[Dict[str, Any]]] = Field(None, description="Sakin bölgeler")
    avg_density_pct: Optional[int] = Field(None, description="Terminal ortalama doluluk yüzdesi")
    unread_notification_count: Optional[int] = Field(None, description="Okunmamış bildirim sayısı")


class ChatStep(BaseModel):
    """A single step in a suggested route (mirrors backend RouteStep)."""
    step_number: int
    zone_name: str
    instruction: str
    estimated_walk_minutes: int


class ChatResponse(BaseModel):
    """Generated reply with optional structured data."""
    reply: str = Field(..., description="Natural language reply for the user")
    intent: str = Field(..., description="Detected intent (e.g. 'route_request')")
    confidence: float = Field(..., ge=0.0, le=1.0)
    route_steps: Optional[List[ChatStep]] = None
    sources_used: List[str] = Field(
        default_factory=list,
        description="Knowledge sources retrieved (e.g. 'backend:/api/routes/suggest')"
    )
