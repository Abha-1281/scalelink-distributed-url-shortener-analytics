"""
ScaleLink Analytics & Real-Time Clickstream Engine
===================================================

HIGH-LEVEL ARCHITECTURE (HLA):
------------------------------
This microservice is the data engineering and analytics backbone of the ScaleLink platform.
It is decoupled from the low-latency Java redirect service via Apache Kafka (topic: `url.clicks.v1`).
It consumes real-time clickstream events, enriches raw HTTP metadata, rolls up hourly timeseries
aggregates, and serves analytical reporting REST APIs to clients and dashboards.

LOW-LEVEL ARCHITECTURE (LLA):
-----------------------------
1. Asynchronous Event Loop (`aiokafka`):
   - Background worker task runs inside FastAPI's async event loop.
   - Consumes from `url.clicks.v1` using consumer group `scalelink-analytics-workers`.
   - Records are keyed by `shortCode` in Kafka, ensuring chronological partition alignment per link.

2. Metadata Enrichment Engine (`app.consumer.enricher`):
   - Resolves IP addresses to ISO country codes.
   - Parses raw User-Agent headers into device category (Mobile, Tablet, Desktop, Bot),
     browser family, and operating system.
   - Truncates event timestamps into hourly buckets (`bucket_hour`).

3. Micro-Batch Aggregation Queue (`app.consumer.batch_buffer`):
   - Mitigates PostgreSQL write amplification by buffering incoming events in memory.
   - Dual-trigger flush invariant:
     `len(buffer) >= 500 records` OR `elapsed_time >= 1.0 second`.
   - Performs in-memory map-reduce rollup across the micro-batch before database execution.

4. PostgreSQL Atomic Bulk Upsert (`ON CONFLICT DO UPDATE`):
   - Persists aggregates into `hourly_click_aggregates` using PostgreSQL composite unique
     constraint `uq_hourly_aggregate (short_code, bucket_hour, country, device, browser)`.
   - Atomically increments `click_count = click_count + EXCLUDED.click_count`.
   - Reduces database I/O IOPS by up to 99%.

5. Analytics Reporting REST APIs (`app.api.v1.analytics`):
   - `GET /api/v1/analytics/{short_code}/summary`: High-level traffic summary.
   - `GET /api/v1/analytics/{short_code}/time-series`: Hourly trend analysis.
   - `GET /api/v1/analytics/{short_code}/breakdown`: Categorical distribution by Country, Device, Browser.
"""

__version__ = "1.0.0"
