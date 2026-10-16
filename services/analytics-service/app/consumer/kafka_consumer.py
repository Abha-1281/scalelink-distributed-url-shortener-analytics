import asyncio
import json
import logging
from aiokafka import AIOKafkaConsumer
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from app.config import settings
from app.consumer.enricher import EventEnricher
from app.consumer.batch_buffer import MicroBatchBuffer
from app.telemetry import get_tracer

logger = logging.getLogger("kafka_consumer")
tracer = get_tracer()
propagator = TraceContextTextMapPropagator()


class AnalyticsKafkaConsumer:
    """
    Asynchronous Kafka Consumer Service consuming url.clicks.v1 events.
    Extracts W3C traceparent headers for unbroken end-to-end distributed tracing.
    """

    def __init__(self):
        self.buffer = MicroBatchBuffer(
            max_batch_size=settings.BATCH_BUFFER_SIZE,
            flush_interval=settings.BATCH_FLUSH_INTERVAL_SECONDS
        )
        self.consumer: AIOKafkaConsumer = None
        self._running = False
        self._task: asyncio.Task = None
        self._flush_task: asyncio.Task = None

    async def start(self):
        self._running = True
        self.consumer = AIOKafkaConsumer(
            settings.KAFKA_TOPIC_CLICKS,
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            group_id=settings.KAFKA_CONSUMER_GROUP,
            enable_auto_commit=True,
            auto_offset_reset="earliest",
            value_deserializer=lambda m: json.loads(m.decode("utf-8"))
        )

        # Retry loop for Kafka connection on boot
        connected = False
        for attempt in range(1, 15):
            try:
                await self.consumer.start()
                connected = True
                logger.info("AIOKafkaConsumer connected successfully to %s", settings.KAFKA_BOOTSTRAP_SERVERS)
                break
            except Exception as e:
                logger.warning("Kafka connection attempt %d failed: %s. Retrying in 2s...", attempt, str(e))
                await asyncio.sleep(2)

        if not connected:
            self._running = False
            logger.error("Could not connect to Kafka after multiple retries. Consumer worker not running.")
            return

        self._task = asyncio.create_task(self._consume_loop())
        self._flush_task = asyncio.create_task(self._periodic_flush_loop())

    async def _consume_loop(self):
        try:
            async for msg in self.consumer:
                if not self._running:
                    break
                try:
                    raw_event = msg.value

                    # Extract W3C trace context from Kafka message headers
                    carrier = {}
                    if hasattr(msg, "headers") and msg.headers:
                        for k, v in msg.headers:
                            k_str = k if isinstance(k, str) else k.decode("utf-8", errors="ignore")
                            v_str = v if isinstance(v, str) else v.decode("utf-8", errors="ignore")
                            carrier[k_str.lower()] = v_str

                    extracted_context = propagator.extract(carrier=carrier)
                    with tracer.start_as_current_span("consume_click_event", context=extracted_context) as span:
                        span.set_attribute("messaging.system", "kafka")
                        span.set_attribute("messaging.destination", settings.KAFKA_TOPIC_CLICKS)
                        span.set_attribute("scalelink.short_code", raw_event.get("shortCode", ""))
                        span.set_attribute("scalelink.event_id", raw_event.get("eventId", ""))

                        enriched = EventEnricher.enrich(raw_event)
                        await self.buffer.add(enriched)
                except Exception as e:
                    logger.error("Error processing click message: %s", str(e), exc_info=True)
        except asyncio.CancelledError:
            logger.info("Kafka consumer loop cancelled.")
        except Exception as e:
            logger.error("Unexpected error in consumer loop: %s", str(e), exc_info=True)

    async def _periodic_flush_loop(self):
        while self._running:
            try:
                await asyncio.sleep(0.5)
                await self.buffer.check_periodic_flush()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error("Error in periodic flush check: %s", str(e))

    async def stop(self):
        self._running = False
        tasks = [t for t in (self._flush_task, self._task) if t is not None]
        for t in tasks:
            t.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        if self.consumer:
            try:
                await self.consumer.stop()
            except Exception as e:
                logger.warning("Error stopping consumer: %s", str(e))
        # Final flush before shutdown
        await self.buffer.flush()
        logger.info("Kafka consumer stopped cleanly.")
