from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, desc
from app.database import get_db
from app.models import HourlyClickAggregate, ClickEventLog
from typing import List, Dict, Any

router = APIRouter(prefix="/analytics", tags=["analytics"])


@router.get("/{short_code}/summary")
async def get_summary(short_code: str, db: AsyncSession = Depends(get_db)):
    """
    Returns high-level click statistics for a given short URL code.
    """
    query = select(
        func.coalesce(func.sum(HourlyClickAggregate.click_count), 0).label("total_clicks")
    ).where(HourlyClickAggregate.short_code == short_code)

    result = await db.execute(query)
    total_clicks = result.scalar_one()

    # Get distinct countries count
    country_query = select(func.count(func.distinct(HourlyClickAggregate.country))).where(
        HourlyClickAggregate.short_code == short_code
    )
    country_result = await db.execute(country_query)
    unique_countries = country_result.scalar_one()

    return {
        "short_code": short_code,
        "total_clicks": total_clicks,
        "unique_countries": unique_countries,
        "status": "ACTIVE" if total_clicks > 0 else "NO_TRAFFIC"
    }


@router.get("/{short_code}/time-series")
async def get_time_series(
    short_code: str,
    limit: int = Query(default=24, ge=1, le=168),
    db: AsyncSession = Depends(get_db)
):
    """
    Returns hourly timeseries click trend for charts and dashboards.
    """
    query = select(
        HourlyClickAggregate.bucket_hour,
        func.sum(HourlyClickAggregate.click_count).label("clicks")
    ).where(
        HourlyClickAggregate.short_code == short_code
    ).group_by(
        HourlyClickAggregate.bucket_hour
    ).order_by(
        desc(HourlyClickAggregate.bucket_hour)
    ).limit(limit)

    result = await db.execute(query)
    rows = result.all()

    return {
        "short_code": short_code,
        "time_series": [
            {"hour": row[0].isoformat() if hasattr(row[0], "isoformat") else str(row[0]), "clicks": int(row[1]) if row[1] is not None else 0}
            for row in reversed(rows)
        ]
    }


@router.get("/{short_code}/breakdown")
async def get_breakdowns(short_code: str, db: AsyncSession = Depends(get_db)):
    """
    Returns demographic categorical breakdown by Country, Device type, and Browser.
    """
    # 1. Countries
    country_query = select(
        HourlyClickAggregate.country,
        func.sum(HourlyClickAggregate.click_count).label("clicks")
    ).where(
        HourlyClickAggregate.short_code == short_code
    ).group_by(
        HourlyClickAggregate.country
    ).order_by(desc("clicks")).limit(10)

    countries = (await db.execute(country_query)).all()

    # 2. Devices
    device_query = select(
        HourlyClickAggregate.device,
        func.sum(HourlyClickAggregate.click_count).label("clicks")
    ).where(
        HourlyClickAggregate.short_code == short_code
    ).group_by(
        HourlyClickAggregate.device
    ).order_by(desc("clicks"))

    devices = (await db.execute(device_query)).all()

    # 3. Browsers
    browser_query = select(
        HourlyClickAggregate.browser,
        func.sum(HourlyClickAggregate.click_count).label("clicks")
    ).where(
        HourlyClickAggregate.short_code == short_code
    ).group_by(
        HourlyClickAggregate.browser
    ).order_by(desc("clicks")).limit(10)

    browsers = (await db.execute(browser_query)).all()

    return {
        "short_code": short_code,
        "countries": [{"name": r[0], "clicks": int(r[1]) if r[1] is not None else 0} for r in countries],
        "devices": [{"name": r[0], "clicks": int(r[1]) if r[1] is not None else 0} for r in devices],
        "browsers": [{"name": r[0], "clicks": int(r[1]) if r[1] is not None else 0} for r in browsers],
    }
