# ==============================================================================
# SkillHub Elite - Production Multi-Stage Dockerfile
# Java 17, Spring Boot 3.5.5, Non-Root Hardened Container
# ==============================================================================

# Stage 1: Runtime Base
FROM eclipse-temurin:17-jre-jammy AS runtime

# Install curl for container health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates tzdata && \
    rm -rf /var/lib/apt/lists/*

# Set standard timezone and UTF-8 encoding
ENV TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# Create secure non-root group and user
RUN groupadd -g 10001 skillhub && \
    useradd -u 10001 -g skillhub -m -s /bin/bash skillhub

# Create app and upload directories with appropriate ownership
WORKDIR /app
RUN mkdir -p /var/skillhub/uploads /var/skillhub/uploads/private /var/skillhub/uploads/videos && \
    chown -R skillhub:skillhub /app /var/skillhub

# Copy pre-built application artifact from Maven target directory
COPY --chown=skillhub:skillhub target/skillhub-0.0.1-SNAPSHOT.war /app/skillhub.war

# Drop privileges to non-root user
USER skillhub:skillhub

# Expose HTTP port
EXPOSE 8080

# Configure container health check against liveness endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/health/liveness || exit 1

# Launch Spring Boot with optimized production JVM settings
ENTRYPOINT ["java", \
    "-server", \
    "-Dspring.profiles.active=prod", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "/app/skillhub.war"]
