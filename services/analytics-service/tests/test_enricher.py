import pytest
from datetime import datetime, timezone
from app.consumer.enricher import EventEnricher


def test_enrich_desktop_user_agent():
    raw_event = {
        "eventId": "evt-001",
        "shortCode": "promo24",
        "originalUrl": "https://example.com/promo",
        "timestamp": "2026-10-12T14:35:22.123456Z",
        "ipAddress": "198.51.100.12",
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "referer": "https://twitter.com"
    }

    enriched = EventEnricher.enrich(raw_event)

    assert enriched["event_id"] == "evt-001"
    assert enriched["short_code"] == "promo24"
    assert enriched["device"] == "Desktop"
    assert "Chrome" in enriched["browser"]
    assert enriched["referer"] == "https://twitter.com"
    assert enriched["bucket_hour"] == datetime(2026, 10, 12, 14, 0, 0, tzinfo=timezone.utc)


def test_enrich_mobile_user_agent():
    raw_event = {
        "eventId": "evt-002",
        "shortCode": "iosApp",
        "ipAddress": "127.0.0.1",
        "userAgent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    }

    enriched = EventEnricher.enrich(raw_event)

    assert enriched["device"] == "Mobile"
    assert "Safari" in enriched["browser"]
    assert enriched["country"] == "US"  # Localhost default


def test_enrich_tablet_user_agent():
    raw_event = {
        "eventId": "evt-003",
        "shortCode": "ipadPromo",
        "userAgent": "Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
    }

    enriched = EventEnricher.enrich(raw_event)
    assert enriched["device"] == "Tablet"


def test_enrich_bot_user_agent():
    raw_event = {
        "eventId": "evt-004",
        "shortCode": "botTest",
        "userAgent": "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
    }

    enriched = EventEnricher.enrich(raw_event)
    assert enriched["device"] == "Bot"


def test_enrich_timestamp_formats():
    # Unix timestamp
    unix_event = {"timestamp": 1728734400}  # 2024-10-12 12:00:00 UTC
    enriched_unix = EventEnricher.enrich(unix_event)
    assert enriched_unix["bucket_hour"].minute == 0
    assert enriched_unix["bucket_hour"].second == 0

    # Malformed timestamp fallback
    bad_event = {"timestamp": "invalid-date-string"}
    enriched_bad = EventEnricher.enrich(bad_event)
    assert isinstance(enriched_bad["bucket_hour"], datetime)


def test_enrich_referer_truncation():
    long_referer = "https://example.com/" + "a" * 300
    event = {"referer": long_referer}
    enriched = EventEnricher.enrich(event)
    assert len(enriched["referer"]) <= 255
