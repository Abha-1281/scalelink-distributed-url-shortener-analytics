@echo off
REM =============================================================================
REM ScaleLink Microservices Test Runner for Windows
REM =============================================================================

set ROOT_DIR=%~dp0..

echo ==========================================================
echo  Running Java URL Service Tests (JUnit 5 + Mockito)
echo ==========================================================
cd "%ROOT_DIR%\services\url-service"
if exist "mvnw.cmd" (
    call mvnw.cmd clean test
) else (
    call mvn clean test
)
if %ERRORLEVEL% NEQ 0 (
    echo Java test suite failed!
    exit /b %ERRORLEVEL%
)

echo ==========================================================
echo  Running Python Analytics Service Tests (pytest)
echo ==========================================================
cd "%ROOT_DIR%\services\analytics-service"
call pytest -v
if %ERRORLEVEL% NEQ 0 (
    echo Python test suite failed!
    exit /b %ERRORLEVEL%
)

echo ==========================================================
echo  All ScaleLink Microservices Tests Passed Successfully!
echo ==========================================================
cd "%ROOT_DIR%"
