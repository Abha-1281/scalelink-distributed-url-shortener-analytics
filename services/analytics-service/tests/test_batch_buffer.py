import pytest
import asyncio
import time
from unittest.mock import AsyncMock, patch, MagicMock
from datetime import datetime, timezone
from app.consumer.batch_buffer import MicroBatchBuffer


@pytest.mark.asyncio
async def test_buffer_add_under_limit():
    buffer = MicroBatchBuffer(max_batch_size=10, flush_interval=10.0)
    event = {
        "event_id": "e1",
        "short_code": "code1",
        "bucket_hour": datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc),
        "country": "US",
        "device": "Desktop",
        "browser": "Chrome",
        "timestamp": datetime.now(timezone.utc),
        "original_url": "https://example.com",
        "referer": ""
    }

    flushed = await buffer.add(event)
    assert not flushed
    assert len(buffer._buffer) == 1


@pytest.mark.asyncio
async def test_buffer_auto_flush_at_threshold():
    buffer = MicroBatchBuffer(max_batch_size=3, flush_interval=10.0)

    with patch.object(buffer, "_persist_batch", new_callable=AsyncMock) as mock_persist:
        for i in range(2):
            flushed = await buffer.add({
                "event_id": f"e{i}",
                "short_code": "code1",
                "bucket_hour": datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc),
                "country": "US",
                "device": "Desktop",
                "browser": "Chrome"
            })
            assert not flushed

        # Third event triggers flush
        flushed = await buffer.add({
            "event_id": "e2",
            "short_code": "code1",
            "bucket_hour": datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc),
            "country": "US",
            "device": "Desktop",
            "browser": "Chrome"
        })
        assert flushed
        mock_persist.assert_called_once()
        assert len(mock_persist.call_args[0][0]) == 3
        assert len(buffer._buffer) == 0


@pytest.mark.asyncio
async def test_periodic_flush():
    buffer = MicroBatchBuffer(max_batch_size=100, flush_interval=0.1)

    with patch.object(buffer, "_persist_batch", new_callable=AsyncMock) as mock_persist:
        await buffer.add({"event_id": "e1", "short_code": "c1"})
        assert len(buffer._buffer) == 1

        # Force stale timestamp
        buffer._last_flush = time.monotonic() - 0.2
        await buffer.check_periodic_flush()

        mock_persist.assert_called_once()
        assert len(buffer._buffer) == 0


@pytest.mark.asyncio
async def test_in_memory_aggregation_and_database_persist():
    buffer = MicroBatchBuffer(max_batch_size=500, flush_interval=1.0)
    hour = datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc)

    # 4 events for same dimension, 1 event for different dimension
    batch = [
        {"event_id": "e1", "short_code": "c1", "bucket_hour": hour, "country": "US", "device": "Desktop", "browser": "Chrome", "timestamp": hour, "original_url": "", "referer": ""},
        {"event_id": "e2", "short_code": "c1", "bucket_hour": hour, "country": "US", "device": "Desktop", "browser": "Chrome", "timestamp": hour, "original_url": "", "referer": ""},
        {"event_id": "e3", "short_code": "c1", "bucket_hour": hour, "country": "US", "device": "Desktop", "browser": "Chrome", "timestamp": hour, "original_url": "", "referer": ""},
        {"event_id": "e4", "short_code": "c1", "bucket_hour": hour, "country": "US", "device": "Desktop", "browser": "Chrome", "timestamp": hour, "original_url": "", "referer": ""},
        {"event_id": "e5", "short_code": "c2", "bucket_hour": hour, "country": "GB", "device": "Mobile", "browser": "Safari", "timestamp": hour, "original_url": "", "referer": ""},
    ]

    mock_session = AsyncMock()
    mock_session_ctx = AsyncMock()
    mock_session_ctx.__aenter__.return_value = mock_session
    mock_session_ctx.__aexit__.return_value = None

    with patch("app.consumer.batch_buffer.AsyncSessionLocal", return_value=mock_session_ctx):
        await buffer._persist_batch(batch)

        # 2 aggregate upserts + 1 bulk log insert = 3 execute calls
        assert mock_session.execute.call_count == 3
        mock_session.commit.assert_called_once()
