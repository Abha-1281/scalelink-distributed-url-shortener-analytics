import logging
from typing import Optional
from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from app.config import settings

logger = logging.getLogger("telemetry")


def setup_telemetry(app: Optional[FastAPI] = None):
    """
    Configures OpenTelemetry TracerProvider, OTLP Span Exporter to Jaeger,
    and W3C Context Propagation.
    """
    try:
        resource = Resource.create({
            "service.name": "scalelink-analytics-service",
            "service.version": "1.0.0"
        })
        provider = TracerProvider(resource=resource)

        otlp_endpoint = settings.OTEL_EXPORTER_OTLP_ENDPOINT
        if not otlp_endpoint.endswith("/v1/traces"):
            otlp_endpoint = f"{otlp_endpoint.rstrip('/')}/v1/traces"

        exporter = OTLPSpanExporter(endpoint=otlp_endpoint)
        processor = BatchSpanProcessor(exporter)
        provider.add_span_processor(processor)

        trace.set_tracer_provider(provider)

        if app is not None:
            FastAPIInstrumentor.instrument_app(app, tracer_provider=provider)

        logger.info("OpenTelemetry initialized with OTLP endpoint: %s", otlp_endpoint)
    except Exception as e:
        logger.warning("Could not initialize OpenTelemetry: %s", str(e))


def get_tracer():
    return trace.get_tracer("scalelink-analytics-service")
