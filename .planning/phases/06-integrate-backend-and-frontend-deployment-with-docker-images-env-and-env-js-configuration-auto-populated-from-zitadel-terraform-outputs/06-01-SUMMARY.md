---
phase: 06-integrate-backend-and-frontend-deployment
plan: "01"
subsystem: infrastructure
tags: [docker-compose, traefik, cors, postgresql, spring-boot]
dependency_graph:
  requires: []
  provides:
    - infra/docker-compose.app.yml (app stack Docker Compose definition)
    - backend CORS env var support
  affects:
    - infra/deploy.sh (Plan 02 will write app.env consumed by this stack)
tech_stack:
  added:
    - Docker Compose (goaldone-app project)
    - postgres:17.2-alpine (goaldone database)
  patterns:
    - Traefik label routing via external zitadel network
    - Spring Boot ${VAR:default} property resolution for env-driven CORS
key_files:
  created:
    - infra/docker-compose.app.yml
  modified:
    - backend/src/main/resources/application-prod.yaml
decisions:
  - goaldone-backend placed on goaldone network only (not zitadel) per D-03: backend-to-zitadel traffic goes through API calls, not direct network exposure
  - goaldone-frontend joins both goaldone and zitadel networks so Traefik (on zitadel network) can route HTTPS traffic to it
  - CORS_ALLOWED_ORIGINS uses Spring fallback syntax so existing deployments without the var continue to work
metrics:
  duration: "~5 minutes"
  completed: "2026-05-15"
  tasks_completed: 2
  tasks_total: 2
  files_created: 1
  files_modified: 1
---

# Phase 06 Plan 01: Docker Compose App Stack and CORS Env Var Summary

Docker Compose app stack defining goaldone-backend, goaldone-frontend, and goaldone-postgres services with Traefik routing, dual-network topology, and CORS env var support in production Spring Boot config.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Create docker-compose.app.yml with all three services | 2298074 | infra/docker-compose.app.yml |
| 2 | Update application-prod.yaml CORS to read from env var | c4e59b3 | backend/src/main/resources/application-prod.yaml |

## What Was Built

### docker-compose.app.yml
- Compose project name `goaldone-app` to avoid conflicts with the zitadel stack
- `goaldone-backend`: Spring Boot container on the internal `goaldone` network only, with all required env vars (`SPRING_PROFILES_ACTIVE`, `ZITADEL_ISSUER_URI`, `ZITADEL_SERVICE_ACCOUNT_TOKEN`, `ZITADEL_GOALDONE_PROJECT_ID`, `ZITADEL_GOALDONE_ORG_ID`, `SPRING_DATASOURCE_*`, `CORS_ALLOWED_ORIGINS`); healthcheck against `/api/v1/actuator/health` with 12 retries and 30s start period
- `goaldone-frontend`: nginx container on both `goaldone` and `zitadel` networks; Traefik labels using `goaldone-web` / `goaldone-websecure` router names with `redirect-to-https` middleware on HTTP and TLS cert-resolver on HTTPS; env vars wired for `envsubst` at container startup (`ZITADEL_CLIENT_ID`, `ZITADEL_ISSUER_URI`, `API_BASE_PATH`, `BACKEND_HOST`)
- `goaldone-postgres`: postgres 17.2-alpine matching the Zitadel stack version, with `pg_isready` healthcheck and named volume `goaldone-postgres-data`
- Networks: `goaldone` (internal, `name: goaldone`) and `zitadel` (`external: true`)
- All services have `restart: unless-stopped`

### application-prod.yaml
- Changed `allowed-origins: https://goaldone.de` to `allowed-origins: ${CORS_ALLOWED_ORIGINS:https://goaldone.de}`
- No other lines modified; datasource, JPA, and Liquibase config unchanged

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan creates infrastructure configuration only; no UI or data-layer stubs.

## Self-Check: PASSED

- `infra/docker-compose.app.yml` exists and contains all required content
- `backend/src/main/resources/application-prod.yaml` updated with CORS env var
- Commit 2298074 exists (Task 1)
- Commit c4e59b3 exists (Task 2)
