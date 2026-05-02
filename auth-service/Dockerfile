# ==============================================================================
# Stage 1: Builder - Compile application
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and project metadata first for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -B -DskipTests=true

# Copy source and build
COPY src src
RUN ./mvnw clean package -B -DskipTests=true -Dmaven.compiler.fork=false

# ==============================================================================
# Stage 2: Runtime - Minimal runtime image
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

# Required for healthcheck command
RUN apk add --no-cache wget && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /build/target/auth-service-*.jar app.jar

USER appuser

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -Xms512m -Xmx1024m"

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9000/actuator/health || exit 1

EXPOSE 9000

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
