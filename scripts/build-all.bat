@echo off
REM =============================================================================
REM ScaleLink Microservices Build Runner for Windows
REM =============================================================================

set ROOT_DIR=%~dp0..

echo Building Java URL Service...
cd "%ROOT_DIR%\services\url-service"
if exist "mvnw.cmd" (
    call mvnw.cmd clean package -DskipTests
) else (
    call mvn clean package -DskipTests
)

echo Building Docker Compose containers...
cd "%ROOT_DIR%"
call docker compose build

echo ScaleLink ecosystem built successfully!
