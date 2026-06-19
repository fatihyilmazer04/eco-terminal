"""Application settings loaded from environment variables."""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """LLM service configuration.

    Environment variables override defaults. Use .env file for local dev.
    """
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Service info
    service_name: str = "llm-service"
    service_version: str = "0.1.0"

    # Backend integration (Dijkstra route fetching)
    backend_base_url: str = "http://backend:8080"
    backend_timeout_seconds: float = 5.0
    backend_internal_token: str = ""  # X-Internal-Token header value; set via LLM_SERVICE_INTERNAL_TOKEN

    # Hugging Face yerel model
    hf_model_id: str = "Qwen/Qwen2.5-3B-Instruct"
    hf_timeout_seconds: float = 150.0

    # Intent classifier mode (added in step 4.2)
    # Options: "rule_based" | "distilbert" | "hybrid"
    intent_classifier_mode: str = "hybrid"

    # Feature flags
    enable_intent_classifier: bool = True   # Activated in step 3.2
    enable_rag: bool = True                 # Activated in step 3.3

    # Logging
    log_level: str = "INFO"


settings = Settings()
