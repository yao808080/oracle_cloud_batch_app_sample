# Multi-stage build for Helidon MP application
FROM maven:3.9-openjdk-21-slim AS build

WORKDIR /app
COPY pom.xml .
COPY src src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:21-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r helidon && useradd -r -g helidon helidon

# Set up application directory
WORKDIR /app

# Copy application files
COPY --from=build /app/target/csv-batch-processor-1.0.0.jar app.jar
COPY --from=build /app/target/libs/ libs/

# Create necessary directories and set permissions
RUN mkdir -p /app/output /app/cache /app/logs && \
    chown -R helidon:helidon /app

# Switch to non-root user
USER helidon

# Health check configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health/ready || exit 1

# Expose application port
EXPOSE 8080

# JVM optimization for containers
ENV JAVA_OPTS="-server \
               -Xms512m -Xmx2g \
               -XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -XX:+PrintGCDetails \
               -XX:+PrintGCTimeStamps \
               -Djava.security.egd=file:/dev/./urandom \
               -Duser.timezone=Asia/Tokyo"

# Helidon specific configurations
ENV MP_CONFIG_PROFILE=docker
ENV HELIDON_MP_LOG_LEVEL=INFO

# Application metadata
LABEL maintainer="csv-batch-processor-team"
LABEL version="1.0.0"
LABEL description="Enterprise CSV Batch Processor for OCI"

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]