"""Health and readiness endpoints."""
from fastapi import APIRouter
from app.config import settings
from app.schemas import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Liveness check. Returns 'ok' if service is running.

    Future improvement (after step 3.3): mark 'degraded' if Gemini API
    is unreachable but service can still respond with rule-based fallback.
    """
    return HealthResponse(
        status="ok",
        service=settings.service_name,
        version=settings.service_version,
        intent_classifier_enabled=settings.enable_intent_classifier,
        rag_enabled=settings.enable_rag,
    )
