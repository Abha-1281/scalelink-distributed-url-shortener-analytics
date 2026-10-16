import asyncio
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.database import engine, Base
from app.consumer.kafka_consumer import AnalyticsKafkaConsumer
from app.api.v1.analytics import router as analytics_router
from app.telemetry import setup_telemetry

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("analytics_main")

consumer_service = AnalyticsKafkaConsumer()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize OpenTelemetry instrumentation
    setup_telemetry(app)

    # Startup: Ensure database tables exist in analytics_db with retry
    logger.info("Initializing analytics database schema...")
    connected = False
    for attempt in range(1, 10):
        try:
            async with engine.begin() as conn:
                await conn.run_sync(Base.metadata.create_all)
            connected = True
            logger.info("Database tables initialized and verified.")
            break
        except Exception as e:
            logger.warning("Database connection attempt %d failed: %s. Retrying in 2s...", attempt, str(e))
            await asyncio.sleep(2)

    if not connected:
        logger.error("Could not connect to database after retries. Schema verification deferred.")

    # Start Kafka background consumer task
    logger.info("Starting background Kafka consumer worker...")
    await consumer_service.start()

    yield

    # Shutdown: Cleanly stop Kafka consumer and flush remaining buffer
    logger.info("Shutting down analytics worker...")
    await consumer_service.stop()
    await engine.dispose()
    logger.info("Shutdown complete.")


app = FastAPI(
    title=settings.PROJECT_NAME,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Health Check Endpoint
@app.get("/health", tags=["system"])
async def health_check():
    return {
        "status": "UP",
        "service": "ScaleLink Analytics Engine",
        "consumer_running": consumer_service._running
    }

# Mount REST API Routes
app.include_router(analytics_router, prefix=settings.API_V1_STR)
