"""FastAPI application entrypoint."""
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import health, chat

# Logging setup
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Eco-Terminal LLM Service",
    description=(
        "Natural language interface for passenger queries. "
        "Combines intent classification, RAG, and Gemini API."
    ),
    version=settings.service_version,
)

# CORS (backend ve frontend bu servise erişebilsin)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Production'da kısıtlanmalı
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(health.router)
app.include_router(chat.router)


@app.on_event("startup")
async def startup() -> None:
    """Log readiness info on startup."""
    logger.info(
        "%s v%s starting | intent=%s rag=%s backend=%s",
        settings.service_name,
        settings.service_version,
        settings.enable_intent_classifier,
        settings.enable_rag,
        settings.backend_base_url,
    )


@app.on_event("shutdown")
async def shutdown() -> None:
    logger.info("%s shutting down", settings.service_name)
