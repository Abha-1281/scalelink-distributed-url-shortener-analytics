# -----------------------------------------------------------------------------
# Stage 1: Build Java Application
# -----------------------------------------------------------------------------
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "app.jar"]
