#!/usr/bin/env bash
# Deploy script for blue-green backend rollouts, frontend deployments, and auth-service orchestration
# Usage: deploy.sh <stage> <backend_version> <frontend_version> <auth-service_version>
# Example: deploy.sh dev 0.0.2 0.0.2 0.0.2

set -euo pipefail

STAGE=$1
BE_VER=$2
FE_VER=$3
AUTH_SVC_VER=$4
DIR=$HOME/docker/$STAGE
ENV_FILE=$DIR/.env

echo "Starting deployment for stage: $STAGE"
echo "Backend version: $BE_VER"
echo "Frontend version: $FE_VER"
echo "Auth-Service version: $AUTH_SVC_VER"

# Pull new images
echo "Pulling backend image: ghcr.io/goaldone-dhbw/goaldone-backend:$BE_VER"
docker pull ghcr.io/goaldone-dhbw/goaldone-backend:$BE_VER

echo "Pulling frontend image: ghcr.io/goaldone-dhbw/goaldone-frontend:$FE_VER"
docker pull ghcr.io/goaldone-dhbw/goaldone-frontend:$FE_VER

echo "Pulling auth-service image: ghcr.io/goaldone-dhbw/goaldone-auth-service:$AUTH_SVC_VER"
docker pull ghcr.io/goaldone-dhbw/goaldone-auth-service:$AUTH_SVC_VER

# ============================================================================
# Postgres-Auth: Ensure auth-service database is running
# ============================================================================
echo "Ensuring postgres-auth is running..."
docker compose -f "$DIR/docker-compose.yaml" up -d --no-deps "postgres-auth-$STAGE"

echo "Waiting for postgres-auth health check..."
max_attempts=12
attempt=0
while [ $attempt -lt $max_attempts ]; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' \
    "$(docker compose -f "$DIR/docker-compose.yaml" ps -q "postgres-auth-$STAGE")" 2>/dev/null || echo "starting")
  echo "  Attempt $((attempt + 1))/$max_attempts - Status: $STATUS"
  [ "$STATUS" = "healthy" ] && break
  attempt=$((attempt + 1))
  sleep 5
done

if [ $attempt -eq $max_attempts ]; then
  echo "❌ Postgres-Auth health check failed after $max_attempts attempts"
  exit 1
fi
echo "✓ Postgres-Auth is healthy"

# ============================================================================
# Auth-Service: Pull image, start, and validate JWKS endpoint
# ============================================================================
echo "Updating auth-service to version $AUTH_SVC_VER..."
sed -i "s/^AUTH_SERVICE_VERSION=.*/AUTH_SERVICE_VERSION=$AUTH_SVC_VER/" "$ENV_FILE"
docker compose -f "$DIR/docker-compose.yaml" up -d --no-deps "auth-service-$STAGE"

echo "Waiting for auth-service health check..."
max_attempts=24
attempt=0
while [ $attempt -lt $max_attempts ]; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' \
    "$(docker compose -f "$DIR/docker-compose.yaml" ps -q "auth-service-$STAGE")" 2>/dev/null || echo "starting")
  echo "  Attempt $((attempt + 1))/$max_attempts - Status: $STATUS"
  [ "$STATUS" = "healthy" ] && break
  attempt=$((attempt + 1))
  sleep 5
done

if [ $attempt -eq $max_attempts ]; then
  echo "❌ Auth-Service health check failed after $max_attempts attempts"
  exit 1
fi
echo "✓ Auth-Service is healthy"

# Validate JWKS endpoint is reachable (D-07: JWKS Endpoint Health Check)
echo "Validating JWKS endpoint..."
if curl -sf "http://auth-service-${STAGE}:9000/oauth2/jwks" > /dev/null; then
  echo "✓ JWKS endpoint is reachable"
else
  echo "❌ JWKS endpoint validation failed"
  exit 1
fi

# ============================================================================
# Postgres: Ensure database is running (idempotent, won't restart if healthy)
# ============================================================================
echo "Ensuring postgres is running..."
docker compose -f "$DIR/docker-compose.yaml" up -d --no-deps "postgres-$STAGE"

echo "Waiting for postgres health check..."
max_attempts=12
attempt=0
while [ $attempt -lt $max_attempts ]; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' \
    "$(docker compose -f "$DIR/docker-compose.yaml" ps -q "postgres-$STAGE")" 2>/dev/null || echo "starting")
  echo "  Attempt $((attempt + 1))/$max_attempts - Status: $STATUS"
  [ "$STATUS" = "healthy" ] && break
  attempt=$((attempt + 1))
  sleep 5
done

if [ $attempt -eq $max_attempts ]; then
  echo "❌ Postgres health check failed after $max_attempts attempts"
  exit 1
fi
echo "✓ Postgres is healthy"

# ============================================================================
# Backend: Rolling restart (single service, stateless)
# Backend depends on auth-service being healthy (enforced via docker-compose depends_on).
# Auth-service startup above ensures JWKS endpoint is reachable.
# ============================================================================
echo "Updating backend to version $BE_VER..."
sed -i "s/^BACKEND_VERSION=.*/BACKEND_VERSION=$BE_VER/" "$ENV_FILE"
docker compose -f "$DIR/docker-compose.yaml" up -d --no-deps "backend-$STAGE"

echo "Waiting for backend health check..."
max_attempts=24
attempt=0
while [ $attempt -lt $max_attempts ]; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' \
    "$(docker compose -f "$DIR/docker-compose.yaml" ps -q "backend-$STAGE")" 2>/dev/null || echo "starting")
  echo "  Attempt $((attempt + 1))/$max_attempts - Status: $STATUS"
  [ "$STATUS" = "healthy" ] && break
  attempt=$((attempt + 1))
  sleep 5
done

if [ $attempt -eq $max_attempts ]; then
  echo "❌ Backend health check failed after $max_attempts attempts"
  exit 1
fi
echo "✓ Backend is healthy"

# ============================================================================
# Frontend: Blue-green deployment
# ============================================================================
echo "Starting frontend blue-green deployment..."

# Determine active and inactive slots
ACTIVE=$(grep "^ACTIVE_COLOR=" "$ENV_FILE" | cut -d= -f2)
INACTIVE=$([ "$ACTIVE" = "blue" ] && echo "green" || echo "blue")

echo "Current active slot: $ACTIVE"
echo "Deploying to inactive slot: $INACTIVE"

# Update .env for the inactive slot
sed -i "s/^FRONTEND_VERSION=.*/FRONTEND_VERSION=$FE_VER/" "$ENV_FILE"
sed -i "s/^FRONTEND_${INACTIVE^^}_ACTIVE=.*/FRONTEND_${INACTIVE^^}_ACTIVE=false/" "$ENV_FILE"

# Start the inactive slot with new image
echo "Starting frontend-$INACTIVE container..."
docker compose -f "$DIR/docker-compose.yaml" up -d --no-deps "frontend-$INACTIVE"

echo "Waiting for frontend-$INACTIVE health check..."
max_attempts=24
attempt=0
while [ $attempt -lt $max_attempts ]; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' \
    "$(docker compose -f "$DIR/docker-compose.yaml" ps -q "frontend-$INACTIVE")" 2>/dev/null || echo "starting")
  echo "  Attempt $((attempt + 1))/$max_attempts - Status: $STATUS"
  [ "$STATUS" = "healthy" ] && break
  attempt=$((attempt + 1))
  sleep 5
done

if [ $attempt -eq $max_attempts ]; then
  echo "❌ Frontend-$INACTIVE health check failed after $max_attempts attempts"
  exit 1
fi
echo "✓ Frontend-$INACTIVE is healthy"

# Switch traffic: enable inactive, disable active via Traefik labels
echo "Switching traffic to $INACTIVE (disabling $ACTIVE)..."
sed -i "s/^FRONTEND_${INACTIVE^^}_ACTIVE=.*/FRONTEND_${INACTIVE^^}_ACTIVE=true/" "$ENV_FILE"
sed -i "s/^FRONTEND_${ACTIVE^^}_ACTIVE=.*/FRONTEND_${ACTIVE^^}_ACTIVE=false/" "$ENV_FILE"

# Both containers need to be recreated to apply the label changes in Traefik
docker compose -f "$DIR/docker-compose.yaml" up -d --no-deps --force-recreate \
  "frontend-$INACTIVE" "frontend-$ACTIVE"

echo "Waiting for Traefik to update routing..."
sleep 3

# Update the ACTIVE_COLOR in .env
sed -i "s/^ACTIVE_COLOR=.*/ACTIVE_COLOR=$INACTIVE/" "$ENV_FILE"

# Stop the old (now inactive) slot
echo "Stopping old slot: frontend-$ACTIVE..."
docker compose -f "$DIR/docker-compose.yaml" stop "frontend-$ACTIVE"
echo "✓ frontend-$ACTIVE stopped"

echo ""
echo "==========================================="
echo "✓ Deployment complete!"
echo "==========================================="
echo "Backend version: $BE_VER"
echo "Frontend version: $FE_VER"
echo "Auth-Service version: $AUTH_SVC_VER"
echo "Active frontend slot: $INACTIVE"
echo "Stage: $STAGE"
echo "==========================================="
