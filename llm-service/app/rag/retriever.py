"""Retriever: orchestrates knowledge base + backend client per intent.

Per-intent retrieval strategy:
    route_request   -> KB (zone facts) + Backend (optimal route)
    flight_info     -> Backend (flight details) + KB (gate location)
    crowd_query     -> Backend (zone status or heatmap)
    loyalty_query   -> KB (loyalty policy) + Backend (wallet — TODO)
    general_info    -> KB only
    unknown         -> KB only (best-effort)
"""
import logging
from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional

from app.rag.knowledge_base import KnowledgeBase, Fact
from app.rag.backend_client import BackendClient
from app.intent import IntentLabel, IntentResult

logger = logging.getLogger(__name__)


@dataclass
class RetrievedContext:
    """Bundle of all context fetched for one user query."""
    facts: List[Fact] = field(default_factory=list)
    route_data: Optional[Dict[str, Any]] = None
    flight_data: Optional[Dict[str, Any]] = None
    zone_status: Optional[Dict[str, Any]] = None
    heatmap: Optional[List[Dict[str, Any]]] = None
    sources_used: List[str] = field(default_factory=list)


class Retriever:
    """Pull together knowledge base + backend data for prompt building."""

    # Hardcoded zone name → ID mapping (matches V3+V21 seed data).
    # In production this would be cached from /api/zones, but the
    # IDs are stable for the demo seed.
    _ZONE_NAME_TO_ID: Dict[str, int] = {
        "CheckIn-1": 4, "CheckIn-2": 20, "CheckIn-3": 21,
        "Security-1": 2, "Security-2": 16,
        "Lounge-1": 3, "Lounge-2": 18,
        "Gate A1": 1, "Gate A2": 8, "Gate A3": 9,
        "Gate B1": 10, "Gate B2": 5,
        "Gate C1": 12, "Gate C2": 13, "Gate C3": 6,
    }

    # Default starting point for users (could be from session/profile)
    _DEFAULT_FROM_ZONE = "CheckIn-1"

    def __init__(self, knowledge_base: KnowledgeBase, backend_client: BackendClient):
        self.kb = knowledge_base
        self.backend = backend_client

    async def retrieve(self, intent_result: IntentResult) -> RetrievedContext:
        """Main entry point — fetch all relevant context."""
        ctx = RetrievedContext()

        # Always pull KB facts (cheap, helps every intent)
        ctx.facts = self.kb.search(
            query=intent_result.raw_message,
            intent=intent_result.intent.value,
            entities=intent_result.entities,
            top_k=3,
        )
        if ctx.facts:
            ctx.sources_used.append("knowledge_base")

        # Intent-specific backend calls
        intent = intent_result.intent
        entities = intent_result.entities

        if intent == IntentLabel.ROUTE_REQUEST:
            await self._retrieve_route(ctx, entities)
        elif intent == IntentLabel.FLIGHT_INFO:
            await self._retrieve_flight(ctx, entities)
        elif intent == IntentLabel.CROWD_QUERY:
            await self._retrieve_crowd(ctx, entities)
        # loyalty_query / general_info / unknown -> KB only

        return ctx

    async def _retrieve_route(self, ctx: RetrievedContext, entities: Dict[str, Any]):
        """Fetch optimal route from backend."""
        destination = entities.get("destination")
        if not destination:
            logger.debug("route_request without destination — skipping backend call")
            return

        to_id = self._ZONE_NAME_TO_ID.get(destination)
        from_id = self._ZONE_NAME_TO_ID.get(self._DEFAULT_FROM_ZONE)

        if to_id is None:
            logger.warning("unknown_destination dest=%s", destination)
            return

        route_data = await self.backend.get_optimal_route(from_id, to_id)
        if route_data:
            ctx.route_data = route_data
            ctx.sources_used.append("backend:dijkstra")

    async def _retrieve_flight(self, ctx: RetrievedContext, entities: Dict[str, Any]):
        """Fetch flight details."""
        code = entities.get("flight_code")
        if not code:
            return

        flight_data = await self.backend.get_flight_info(code)
        if flight_data:
            ctx.flight_data = flight_data
            ctx.sources_used.append("backend:flights")

    async def _retrieve_crowd(self, ctx: RetrievedContext, entities: Dict[str, Any]):
        """Fetch zone status or full heatmap."""
        destination = entities.get("destination")
        if destination:
            status = await self.backend.get_zone_status(destination)
            if status:
                ctx.zone_status = status
                ctx.sources_used.append("backend:occupancy")
        else:
            heatmap = await self.backend.get_all_zones_status()
            if heatmap:
                ctx.heatmap = heatmap
                ctx.sources_used.append("backend:heatmap")
