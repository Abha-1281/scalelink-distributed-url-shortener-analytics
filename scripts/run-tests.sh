#!/usr/bin/env bash
# =============================================================================
# ScaleLink Microservices Test Runner
# Executes test suites across Java link-service and Python analytics-service
# =============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=========================================================="
echo " Running Java URL Service Tests (JUnit 5 + Mockito)"
echo "=========================================================="
cd "$DIR/services/url-service"
if [ -f "./mvnw" ]; then
    ./mvnw clean test
else
    mvn clean test
fi

echo "=========================================================="
echo " Running Python Analytics Service Tests (pytest)"
echo "=========================================================="
cd "$DIR/services/analytics-service"
pytest -v

echo "=========================================================="
echo " All ScaleLink Microservices Tests Passed Successfully!"
echo "=========================================================="
