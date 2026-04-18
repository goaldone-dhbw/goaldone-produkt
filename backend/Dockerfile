# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy api-spec and backend (Maven needs api-spec for code generation)
COPY api-spec/ api-spec/
COPY backend/ backend/

WORKDIR /build/backend

# Build with Maven, skip tests
RUN ./mvnw clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/backend/target/*.jar app.jar

EXPOSE 8080

# Spring profile is controlled via SPRING_PROFILES_ACTIVE environment variable
ENTRYPOINT ["java", "-jar", "app.jar"]
