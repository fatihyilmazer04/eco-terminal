"""Chat endpoint — full RAG pipeline.

Pipeline (step 3.3 complete):
    1. Validate request
    2. Classify intent (rule-based)
    3. Retrieve context (KB + backend)
    4. Build prompt
    5. Call Gemini
    6. Return reply OR fallback to template if Gemini unavailable
"""
import logging
from typing import List, Optional

from fastapi import APIRouter, HTTPException

from app.schemas import ChatRequest, ChatResponse, ChatStep
from app.intent import RuleBasedClassifier, DistilBertClassifier, HybridClassifier
from app.rag import KnowledgeBase, BackendClient, Retriever, RetrievedContext
from app.llm import GeminiClient, PromptBuilder
from app.llm.template_engine import (
    get_smart_reply,
    generate_notification_reply,
    generate_ticket_help_reply,
)
from app.config import settings

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/chat", tags=["chat"])

# ---- Singletons ----
# All components are stateless or thread-safe; safe to share.

# Select classifier based on config (default: hybrid)
if settings.intent_classifier_mode == "distilbert":
    _classifier = DistilBertClassifier()
elif settings.intent_classifier_mode == "rule_based":
    _classifier = RuleBasedClassifier()
else:  # default: hybrid
    _classifier = HybridClassifier()

logger.info("intent_classifier_mode=%s classifier=%s", settings.intent_classifier_mode, _classifier.name)
_kb = KnowledgeBase()
_backend = BackendClient()
_retriever = Retriever(_kb, _backend)
_gemini = GeminiClient()
_prompt_builder = PromptBuilder()



@router.post("", response_model=ChatResponse)
async def chat(req: ChatRequest) -> ChatResponse:
    """Full RAG pipeline: classify -> retrieve -> prompt -> generate."""
    logger.info(
        "chat_request user_id=%s session=%s msg_len=%d locale=%s",
        req.user_id, req.session_id, len(req.message), req.locale,
    )

    if not req.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    # 1. Classify
    intent_result = _classifier.classify(req.message, locale=req.locale)
    logger.info(
        "intent_classified intent=%s confidence=%.2f entities=%s",
        intent_result.intent.value, intent_result.confidence, intent_result.entities,
    )

    sources_used: List[str] = [f"classifier:{_classifier.name}"]

    # 1b. Early exit for deterministic intents — no RAG, no Gemini needed
    if intent_result.intent.value == "notification_query":
        reply = generate_notification_reply(req.unread_notification_count)
        return ChatResponse(
            reply=reply,
            intent="notification_query",
            confidence=intent_result.confidence,
            sources_used=sources_used + ["template:notification"],
        )

    if intent_result.intent.value == "ticket_help":
        reply = generate_ticket_help_reply()
        return ChatResponse(
            reply=reply,
            intent="ticket_help",
            confidence=intent_result.confidence,
            sources_used=sources_used + ["template:ticket_help"],
        )

    # 2. Retrieve (RAG)
    if settings.enable_rag:
        context = await _retriever.retrieve(intent_result)
        sources_used.extend(context.sources_used)
        logger.info(
            "context_retrieved facts=%d route=%s flight=%s zone=%s heatmap=%s",
            len(context.facts),
            bool(context.route_data),
            bool(context.flight_data),
            bool(context.zone_status),
            bool(context.heatmap),
        )
    else:
        context = RetrievedContext()

    # 3. Build prompt
    prompt = _prompt_builder.build(intent_result, context)
    logger.debug("prompt_built length=%d", len(prompt))

    # 4. Call Gemini (with smart template fallback)
    reply: str
    if _gemini.is_configured():
        gemini_reply = await _gemini.generate(prompt)
        if gemini_reply:
            reply = gemini_reply
            sources_used.append(f"gemini:{settings.gemini_model}")
        else:
            reply = get_smart_reply(
                intent=intent_result.intent.value,
                context=context,
                entities=intent_result.entities,
                eco_points=req.eco_points,
                tier_level=req.tier_level,
                user_flights=req.user_flights,
                hot_zones=req.hot_zones,
                quiet_zones=req.quiet_zones,
                avg_density_pct=req.avg_density_pct,
                unread_notification_count=req.unread_notification_count,
            )
            sources_used.append("fallback:template")
    else:
        reply = get_smart_reply(
            intent=intent_result.intent.value,
            context=context,
            entities=intent_result.entities,
            eco_points=req.eco_points,
            tier_level=req.tier_level,
            user_flights=req.user_flights,
            hot_zones=req.hot_zones,
            quiet_zones=req.quiet_zones,
            avg_density_pct=req.avg_density_pct,
            unread_notification_count=req.unread_notification_count,
        )
        sources_used.append("fallback:template")

    # 5. Build response — include route steps if backend returned them
    route_steps: Optional[List[ChatStep]] = None
    if context.route_data:
        alternatives = context.route_data.get("alternatives", [])
        if alternatives:
            raw_steps = alternatives[0].get("steps", [])
            if raw_steps:
                route_steps = [
                    ChatStep(
                        step_number=s.get("stepNumber", i + 1),
                        zone_name=s.get("zoneName", ""),
                        instruction=s.get("instruction", ""),
                        estimated_walk_minutes=s.get("estimatedWalkMinutes", 0),
                    )
                    for i, s in enumerate(raw_steps)
                ]

    return ChatResponse(
        reply=reply,
        intent=intent_result.intent.value,
        confidence=intent_result.confidence,
        route_steps=route_steps,
        sources_used=sources_used,
    )
