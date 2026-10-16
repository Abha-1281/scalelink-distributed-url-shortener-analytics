from sqlalchemy import Column, BigInteger, String, Integer, DateTime, UniqueConstraint, Index
from app.database import Base
from datetime import datetime, timezone


class HourlyClickAggregate(Base):
    """
    Pre-aggregated hourly rollups for sub-millisecond analytics reporting.
    Supports atomic ON CONFLICT DO UPDATE upserts from the Kafka consumer.
    """
    __tablename__ = "hourly_click_aggregates"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    short_code = Column(String(32), nullable=False, index=True)
    bucket_hour = Column(DateTime(timezone=True), nullable=False, index=True)
    country = Column(String(3), nullable=False, default="ZZ")
    device = Column(String(32), nullable=False, default="Desktop")
    browser = Column(String(64), nullable=False, default="Unknown")
    click_count = Column(Integer, nullable=False, default=1)

    __table_args__ = (
        UniqueConstraint(
            "short_code", "bucket_hour", "country", "device", "browser",
            name="uq_hourly_aggregate"
        ),
        Index("idx_aggregate_lookup", "short_code", "bucket_hour"),
    )


class ClickEventLog(Base):
    """
    Immutable audit log of raw ingested click events.
    """
    __tablename__ = "click_events"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    event_id = Column(String(36), nullable=False, unique=True, index=True)
    short_code = Column(String(32), nullable=False, index=True)
    original_url = Column(String, nullable=True)
    timestamp = Column(DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc))
    country = Column(String(3), nullable=False, default="ZZ")
    device = Column(String(32), nullable=False, default="Desktop")
    browser = Column(String(64), nullable=False, default="Unknown")
    referer = Column(String(256), nullable=True)
