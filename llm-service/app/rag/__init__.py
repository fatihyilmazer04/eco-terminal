"""RAG (Retrieval-Augmented Generation) module.

Components:
    - KnowledgeBase: Static facts about terminal (zones, services)
    - BackendClient: Live data from Spring Boot backend (routes, flights)
    - Retriever: Orchestrator — picks relevant context per intent
"""
from app.rag.knowledge_base import KnowledgeBase
from app.rag.backend_client import BackendClient
from app.rag.retriever import Retriever, RetrievedContext

__all__ = ["KnowledgeBase", "BackendClient", "Retriever", "RetrievedContext"]
