from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    PROJECT_NAME: str = "ScaleLink Analytics Engine"
    API_V1_STR: str = "/api/v1"

    # PostgreSQL Database URL (asyncpg)
    DATABASE_URL: str = Field(
        default="postgresql+asyncpg://postgres:postgres@localhost:5432/analytics_db",
        validation_alias="DATABASE_URL"
    )

    # Kafka Settings
    KAFKA_BOOTSTRAP_SERVERS: str = Field(
        default="localhost:9092",
        validation_alias="KAFKA_BOOTSTRAP_SERVERS"
    )
    KAFKA_TOPIC_CLICKS: str = Field(
        default="url.clicks.v1",
        validation_alias="KAFKA_TOPIC_CLICKS"
    )
    KAFKA_CONSUMER_GROUP: str = "scalelink-analytics-workers"

    # Micro-batch Aggregation Parameters
    BATCH_BUFFER_SIZE: int = 500
    BATCH_FLUSH_INTERVAL_SECONDS: float = 1.0

    # OpenTelemetry
    OTEL_EXPORTER_OTLP_ENDPOINT: str = Field(
        default="http://localhost:4318",
        validation_alias="OTEL_EXPORTER_OTLP_ENDPOINT"
    )

    class Config:
        case_sensitive = True
        env_file = ".env"
        extra = "ignore"


settings = Settings()
