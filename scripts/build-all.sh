#!/usr/bin/env bash
# =============================================================================
# ScaleLink Microservices Build Runner
# Builds Java and Python packages and Docker containers
# =============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Building Java URL Service..."
cd "$DIR/services/url-service"
if [ -f "./mvnw" ]; then
    ./mvnw clean package -DskipTests
else
    mvn clean package -DskipTests
fi

echo "Building Docker Compose containers..."
cd "$DIR"
docker compose build

echo "ScaleLink ecosystem built successfully!"
