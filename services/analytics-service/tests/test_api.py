import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.database import get_db


@pytest.fixture
def mock_db():
    session = AsyncMock()
    return session


@pytest.mark.asyncio
async def test_health_check():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "UP"
        assert "ScaleLink Analytics Engine" in data["service"]


@pytest.mark.asyncio
async def test_get_summary_endpoint(mock_db):
    # Mock total clicks and unique countries
    mock_result_clicks = MagicMock()
    mock_result_clicks.scalar_one.return_value = 150

    mock_result_countries = MagicMock()
    mock_result_countries.scalar_one.return_value = 5

    mock_db.execute.side_effect = [mock_result_clicks, mock_result_countries]

    app.dependency_overrides[get_db] = lambda: mock_db
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/testCode/summary")
            assert response.status_code == 200
            data = response.json()
            assert data["short_code"] == "testCode"
            assert data["total_clicks"] == 150
            assert data["unique_countries"] == 5
            assert data["status"] == "ACTIVE"
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_get_time_series_endpoint(mock_db):
    h1 = datetime(2026, 10, 12, 10, 0, tzinfo=timezone.utc)
    mock_result = MagicMock()
    mock_result.all.return_value = [(h1, 45)]
    mock_db.execute.return_value = mock_result

    app.dependency_overrides[get_db] = lambda: mock_db
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/testCode/time-series?limit=12")
            assert response.status_code == 200
            data = response.json()
            assert data["short_code"] == "testCode"
            assert len(data["time_series"]) == 1
            assert data["time_series"][0]["clicks"] == 45
            assert "2026-10-12T10:00:00" in data["time_series"][0]["hour"]
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_get_breakdown_endpoint(mock_db):
    res_countries = MagicMock()
    res_countries.all.return_value = [("US", 100), ("DE", 50)]

    res_devices = MagicMock()
    res_devices.all.return_value = [("Desktop", 120), ("Mobile", 30)]

    res_browsers = MagicMock()
    res_browsers.all.return_value = [("Chrome", 90), ("Firefox", 60)]

    mock_db.execute.side_effect = [res_countries, res_devices, res_browsers]

    app.dependency_overrides[get_db] = lambda: mock_db
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/testCode/breakdown")
            assert response.status_code == 200
            data = response.json()
            assert data["short_code"] == "testCode"
            assert len(data["countries"]) == 2
            assert data["countries"][0] == {"name": "US", "clicks": 100}
            assert len(data["devices"]) == 2
            assert data["devices"][0] == {"name": "Desktop", "clicks": 120}
            assert len(data["browsers"]) == 2
    finally:
        app.dependency_overrides.clear()
