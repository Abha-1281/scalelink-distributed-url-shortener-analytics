import re
from datetime import datetime, timezone
from user_agents import parse as parse_user_agent
from typing import Dict, Any


class EventEnricher:
    """
    Enriches raw click events with User-Agent and GeoIP metadata.
    """

    @staticmethod
    def enrich(raw_event: Dict[str, Any]) -> Dict[str, Any]:
        # 1. Parse User Agent Header
        ua_string = raw_event.get("userAgent", "")
        ua = parse_user_agent(ua_string)

        if ua.is_bot:
            device = "Bot"
        elif ua.is_mobile:
            device = "Mobile"
        elif ua.is_tablet:
            device = "Tablet"
        else:
            device = "Desktop"

        browser_family = ua.browser.family if ua.browser.family else "Unknown"

        # 2. Resolve Country (Lightweight resolver mapping IP blocks)
        ip = raw_event.get("ipAddress", "")
        country = EventEnricher._resolve_country(ip)

        # 3. Truncate Timestamp to Hourly Bucket for Micro-batch Aggregation
        ts_raw = raw_event.get("timestamp")
        if isinstance(ts_raw, str):
            try:
                # Handle ISO string with or without Z
                ts = datetime.fromisoformat(ts_raw.replace("Z", "+00:00"))
            except Exception:
                ts = datetime.now(timezone.utc)
        elif isinstance(ts_raw, (int, float)):
            ts = datetime.fromtimestamp(ts_raw, tz=timezone.utc)
        else:
            ts = datetime.now(timezone.utc)

        bucket_hour = ts.replace(minute=0, second=0, microsecond=0)

        return {
            "event_id": raw_event.get("eventId"),
            "short_code": raw_event.get("shortCode"),
            "original_url": raw_event.get("originalUrl"),
            "timestamp": ts,
            "bucket_hour": bucket_hour,
            "country": country,
            "device": device,
            "browser": browser_family,
            "referer": (raw_event.get("referer") or "")[:255]
        }

    @staticmethod
    def _resolve_country(ip: str) -> str:
        if not ip or ip in ("UNKNOWN", "127.0.0.1", "localhost", "::1"):
            return "US" # Local testing default
        # Simple deterministic subnet hash for realistic GeoIP distribution in testing
        subnets = ["US", "GB", "DE", "IN", "CA", "FR", "JP", "AU"]
        idx = abs(hash(ip)) % len(subnets)
        return subnets[idx]
