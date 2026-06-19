"""HTTP client for the Spring Boot backend.

Fetches live data: routes (Dijkstra), occupancy, flights, loyalty.
All calls are async (httpx.AsyncClient), with timeout and graceful failure.

Internal auth: every request carries X-Internal-Token header so Spring Boot's
InternalTokenFilter grants ROLE_INTERNAL_SERVICE access without a user JWT.
"""
import logging
from typing import Optional, Dict, Any, List

import httpx
from app.config import settings

logger = logging.getLogger(__name__)


class BackendClient:
    """Async HTTP client wrapping common backend calls."""

    def __init__(self, base_url: Optional[str] = None, timeout: Optional[float] = None):
        self.base_url = (base_url or settings.backend_base_url).rstrip("/")
        self.timeout = timeout or settings.backend_timeout_seconds

    def _build_headers(self) -> Dict[str, str]:
        """Return headers required for internal service calls."""
        headers: Dict[str, str] = {"Content-Type": "application/json"}
        token = settings.backend_internal_token
        if token:
            headers["X-Internal-Token"] = token
        return headers

    async def get_optimal_route(
        self,
        from_zone_id: int,
        to_zone_id: int,
    ) -> Optional[Dict[str, Any]]:
        """Call backend Dijkstra to get optimal routes.

        Returns dict with 'alternatives' list, or None on failure.
        """
        url = f"{self.base_url}/api/routes/optimal"
        payload = {"fromZoneId": from_zone_id, "toZoneId": to_zone_id}

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(url, json=payload, headers=self._build_headers())
                resp.raise_for_status()
                body = resp.json()
                # Unwrap Spring Boot ApiResponse wrapper: {"success":true,"data":{...}}
                return body.get("data", body) if isinstance(body, dict) else body
        except httpx.TimeoutException:
            logger.warning("backend_timeout url=%s", url)
            return None
        except httpx.HTTPStatusError as e:
            logger.warning("backend_http_error url=%s status=%d", url, e.response.status_code)
            return None
        except Exception as e:
            logger.error("backend_unexpected_error url=%s err=%s", url, e)
            return None

    async def get_zone_status(self, zone_name: str) -> Optional[Dict[str, Any]]:
        """Get current occupancy status for a zone."""
        url = f"{self.base_url}/api/occupancy/zone"
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(url, params={"name": zone_name}, headers=self._build_headers())
                resp.raise_for_status()
                return resp.json()
        except Exception as e:
            logger.debug("zone_status_failed zone=%s err=%s", zone_name, e)
            return None

    async def get_flight_info(self, flight_code: str) -> Optional[Dict[str, Any]]:
        """Get flight details by code (e.g. 'TK1922')."""
        url = f"{self.base_url}/api/flights/{flight_code}"
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(url, headers=self._build_headers())
                resp.raise_for_status()
                return resp.json()
        except Exception as e:
            logger.debug("flight_info_failed code=%s err=%s", flight_code, e)
            return None

    async def get_all_zones_status(self) -> Optional[List[Dict[str, Any]]]:
        """Heatmap snapshot — all zones with current density.

        Unwraps Spring Boot ApiResponse wrapper:
            {"success": true, "data": {"zones": [...], ...}}  →  [...]
        Returns the zone list directly, or None on failure.
        """
        url = f"{self.base_url}/api/heatmap/live"
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(url, headers=self._build_headers())
                resp.raise_for_status()
                body = resp.json()
                # Unwrap ApiResponse → data dict → zones list
                zones = (
                    body.get("data", {}).get("zones", [])
                    if isinstance(body, dict)
                    else []
                )
                return zones if zones else None
        except Exception as e:
            logger.debug("heatmap_failed err=%s", e)
            return None
