import asyncio
import logging
import time
from collections import defaultdict
from typing import List, Dict, Any, Optional
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import AsyncSessionLocal
from app.models import HourlyClickAggregate, ClickEventLog

logger = logging.getLogger("batch_buffer")


class MicroBatchBuffer:
    """
    =============================================================================
    LOW-LEVEL ARCHITECTURE (LLA): MICRO-BATCH QUEUE & BULK PERSISTENCE ENGINE
    =============================================================================
    1. Queuing Model & Write Amplification Problem:
       - Individual inserts for 10,000 req/sec saturate PostgreSQL disk IOPS and connection pools.
       - MicroBatchBuffer buffers events in an in-memory queue protected by an asyncio.Lock.

    2. Dual-Trigger Invariants:
       - Size Threshold: len(buffer) >= max_batch_size (500 records).
       - Time Window Threshold: (currentTime - lastFlushTime) >= flush_interval (1.0s).

    3. In-Memory Map-Reduce Aggregation:
       - Incoming records are pre-aggregated in memory across composite tuple:
         Key: (short_code, bucket_hour, country, device, browser)
         Value: click_count++
       - Consolidates N records into M distinct database upsert rows (where M << N).

    4. PostgreSQL Atomic Bulk Upsert:
       - Uses PostgreSQL dialect `ON CONFLICT ON CONSTRAINT uq_hourly_aggregate DO UPDATE`.
       - Atomically executes:
         `SET click_count = hourly_click_aggregates.click_count + EXCLUDED.click_count`.
       - Achieves up to 99% reduction in PostgreSQL disk writes.
    =============================================================================
    """

    def __init__(self, max_batch_size: int = 500, flush_interval: float = 1.0):
        self.max_batch_size = max_batch_size
        self.flush_interval = flush_interval
        self._buffer: List[Dict[str, Any]] = []
        self._lock: Optional[asyncio.Lock] = None
        self._last_flush = time.monotonic()

    def _get_lock(self) -> asyncio.Lock:
        if self._lock is None:
            self._lock = asyncio.Lock()
        return self._lock

    async def add(self, event: Dict[str, Any]) -> bool:
        async with self._get_lock():
            self._buffer.append(event)
            should_flush = len(self._buffer) >= self.max_batch_size

        if should_flush:
            await self.flush()
            return True
        return False

    async def check_periodic_flush(self):
        async with self._get_lock():
            now = time.monotonic()
            is_stale = (now - self._last_flush >= self.flush_interval) and len(self._buffer) > 0

        if is_stale:
            await self.flush()

    async def flush(self):
        async with self._get_lock():
            if not self._buffer:
                return
            batch = self._buffer[:]
            self._buffer.clear()
            self._last_flush = time.monotonic()

        await self._persist_batch(batch)

    async def _persist_batch(self, batch: List[Dict[str, Any]]):
        # 1. In-memory Aggregation (roll up duplicates within the micro-batch)
        aggregate_counts = defaultdict(int)
        for item in batch:
            key = (
                item["short_code"],
                item["bucket_hour"],
                item["country"],
                item["device"],
                item["browser"]
            )
            aggregate_counts[key] += 1

        async with AsyncSessionLocal() as session:
            try:
                # 2. Bulk Upsert into HourlyClickAggregate using PostgreSQL ON CONFLICT DO UPDATE
                for (code, hour, country, device, browser), count in aggregate_counts.items():
                    stmt = insert(HourlyClickAggregate).values(
                        short_code=code,
                        bucket_hour=hour,
                        country=country,
                        device=device,
                        browser=browser,
                        click_count=count
                    )
                    upsert_stmt = stmt.on_conflict_do_update(
                        constraint="uq_hourly_aggregate",
                        set_={"click_count": HourlyClickAggregate.click_count + stmt.excluded.click_count}
                    )
                    await session.execute(upsert_stmt)

                # 3. Bulk Insert into ClickEventLog audit table (idempotent ON CONFLICT DO NOTHING)
                raw_logs = [
                    {
                        "event_id": item["event_id"],
                        "short_code": item["short_code"],
                        "original_url": item["original_url"],
                        "timestamp": item["timestamp"],
                        "country": item["country"],
                        "device": item["device"],
                        "browser": item["browser"],
                        "referer": item["referer"]
                    }
                    for item in batch if item.get("event_id")
                ]
                if raw_logs:
                    log_stmt = insert(ClickEventLog).values(raw_logs)
                    log_upsert = log_stmt.on_conflict_do_nothing(index_elements=["event_id"])
                    await session.execute(log_upsert)

                await session.commit()
                logger.info("Successfully flushed micro-batch of %d events (%d distinct rollups)",
                            len(batch), len(aggregate_counts))
            except Exception as e:
                await session.rollback()
                logger.error("Failed to persist micro-batch to PostgreSQL: %s", str(e), exc_info=True)
