# External Integrations

**Analysis Date:** 2026-05-02

## APIs & External Services

**Zitadel (Identity & Access Management):**
- **Service:** Zitadel (self-hosted or managed instance)
  - Used for OAuth2/OIDC authentication, user management, organization management, role assignment
  - Primary integration point for the entire auth infrastructure
  
- **Backend SDK Client:** `io.github.zitadel:client` 4.1.2
  - Location: `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java`
  - Provides typed access to Zitadel Management API v2 (users, organizations, authorizations)
  - Methods: user creation, role grant assignment, org creation/deletion, user deletion, invite code generation

- **UserInfo Endpoint (OIDC):**
  - Location: `backend/src/main/java/de/goaldone/backend/client/ZitadelUserInfoClient.java`
  - Endpoint: `${ZITADEL_ISSUER_URI}/oidc/v1/userinfo`
  - Used to fetch additional user profile info if needed
  - Authenticated with bearer token

- **Management API v1 (REST):**
  - Accessed via `RestClient` with raw JSON for unsupported SDK endpoints
  - Uses base URL from `spring.security.oauth2.resourceserver.jwt.issuer-uri` config
  - Endpoints called:
    - `POST /management/v1/users/grants/_search` - Search user grants by role/project
    - `PUT /management/v1/users/grants/{grantId}` - Update user role grants
    - `POST /management/v1/users/{userId}/grants` - Add user grants

- **JWT/OIDC Discovery:**
  - Frontend loads discovery document from `${issuerUri}/.well-known/openid-configuration`
  - Used by `angular-oauth2-oidc` to determine token endpoint, authorization endpoint, etc.

- **Auth Environment Variables:**
  - `ZITADEL_ISSUER_URI` - OIDC issuer (e.g., `https://sso.dev.goaldone.de`)
  - `ZITADEL_SERVICE_ACCOUNT_TOKEN` - Static token for backend-to-Zitadel communication (Management API)
  - `ZITADEL_GOALDONE_PROJECT_ID` - Project ID in Zitadel
  - `ZITADEL_GOALDONE_ORG_ID` - Organization ID in Zitadel

## Data Storage

**Databases:**
- **Primary (Production/Dev):** PostgreSQL 14+
  - Connection: `SPRING_DATASOURCE_URL` (default: `jdbc:postgresql://localhost:5432/goaldone`)
  - Credentials: `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
  - Client: Hibernate JPA (Spring Data JPA abstracts connection pooling)
  - Config files: `application-dev.yaml`, `application-prod.yaml`

- **Fallback (Local Development):** H2 in-memory database
  - Connection: `jdbc:h2:mem:goaldonedb;DB_CLOSE_DELAY=-1` (in-memory, survives request lifecycle)
  - No external setup needed; enabled via Spring profile `local`
  - Web console available at `http://localhost:8080/h2-console` (local only)
  - Config file: `application-local.yaml`

- **Schema Management:** Liquibase
  - Master changelog: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
  - Change sets stored in: `backend/src/main/resources/db/changelog/changes/`
  - Applied on application startup (configured in `application.yaml`)
  - Never use Hibernate `ddl-auto: create` (only `validate` per CLAUDE.md)

- **Core Entities:**
  - `UserIdentityEntity` - Global user identity across organizations
  - `UserAccountEntity` - User account scoped to an organization, linked to Zitadel SUB claim
  - `OrganizationEntity` - Organization managed via GoalDone API
  - `TaskEntity` - Tasks/goals
  - `AppointmentEntity` - Scheduled appointments
  - `WorkingTimeEntity` - Working hours configuration per account

**File Storage:**
- Not detected. No AWS S3, GCS, or local file upload integration configured.
- Files are not part of the current stack.

**Caching:**
- Not detected. No Redis, Memcached, or Spring Cache abstractions configured.
- All data flows directly to PostgreSQL/H2 on each request.

## Authentication & Identity

**Auth Provider:**
- **Zitadel** (external identity provider)
  - Protocol: OAuth2 + OIDC (OpenID Connect)
  - Flow: Authorization Code Grant with PKCE (Public Client as per `angular-oauth2-oidc` configuration)

- **JWT Token Validation (Backend):**
  - Location: `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java`
  - Framework: Spring Security OAuth2 Resource Server
  - Issuer URI validation against configured `ZITADEL_ISSUER_URI`
  - Custom `JwtAuthenticationConverter` extracts roles from claim: `urn:zitadel:iam:org:project:roles`
  - Stateless (no session cookies; Bearer token only)

- **Frontend Auth Flow:**
  - Service: `frontend/src/app/core/auth/auth.service.ts`
  - Library: `angular-oauth2-oidc` 20.0.2
  - Flow: Authorization Code with PKCE
  - Redirect URI: `${window.location.origin}/callback`
  - Scopes: `openid profile email offline_access urn:zitadel:iam:user:resourceowner`
  - Automatic token refresh enabled via `useSilentRefresh`
  - On token error, logs out and redirects to login

- **Just-In-Time (JIT) User Provisioning:**
  - Filter: `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java`
  - Runs after `BearerTokenAuthenticationFilter` on every authenticated request
  - Creates/updates `UserIdentityEntity` and `UserAccountEntity` from JWT subject if not in DB
  - Service: `JitProvisioningService` (business logic)
  - Syncs user data from Zitadel JWT claim to local database

- **Role Extraction:**
  - Backend: From JWT claim `urn:zitadel:iam:org:project:roles` (nested map of role names)
  - Converted to Spring Security `SimpleGrantedAuthority` with `ROLE_` prefix
  - Method-level security via `@PreAuthorize` annotations on controllers/services
  - Frontend: Decoded from access token in `AuthService.getUserRoles()`

- **Interceptors/Filters:**
  - Backend: `JitProvisioningFilter` (post-auth provisioning)
  - Frontend: `authInterceptor` (adds Bearer token to API requests, `frontend/src/app/core/auth/auth.interceptor.ts`)
  - CORS configured in `SecurityConfig.corsConfigurationSource()`

## Monitoring & Observability

**Error Tracking:**
- Not detected. No Sentry, Datadog, or other error tracking service integrated.

**Logs:**
- Backend: 
  - SLF4J (via Lombok `@Slf4j`)
  - Console output with pattern: `"%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"`
  - Level: `INFO` by default, `DEBUG` for `de.goaldone` package in local profile
- Frontend:
  - Service: `frontend/src/app/core/logger.service.ts` (custom logger)
  - Level: `DEBUG` in development, configurable via `environment.ts`

**Metrics:**
- Spring Actuator available at `/actuator/health` and `/swagger-ui/**` (permitted without authentication)
- No external metrics aggregation detected

## CI/CD & Deployment

**Hosting:**
- Not configured in codebase. Assumed external deployment (cloud provider or private infrastructure).
- Backend: Packaged as Spring Boot JAR (Maven `spring-boot-maven-plugin`)
- Frontend: Static assets in `dist/frontend/browser/`

**CI Pipeline:**
- Not detected. No GitHub Actions, GitLab CI, Jenkins, or other CI tool configuration in repo.

**Local Development Infrastructure:**
- Docker Compose files in `infra/infra-setup/` for Zitadel + PostgreSQL
- Images: `zitadel:v4.11.0`, `postgres:17.2-alpine`, `traefik:v3.6.8`
- Config: `.env-dev-example` template (copy to `.env-dev` and populate secrets)
- Startup: `docker compose -f docker-compose.dev.yml --env-file .env-dev up -d --wait`

## Environment Configuration

**Required Environment Variables (Backend):**
- `ZITADEL_ISSUER_URI` - Zitadel OAuth2/OIDC issuer (e.g., `https://sso.dev.goaldone.de` or `http://localhost:8080` for local)
- `ZITADEL_SERVICE_ACCOUNT_TOKEN` - Static access token for Management API calls
- `ZITADEL_GOALDONE_PROJECT_ID` - Zitadel project ID for role assignment
- `ZITADEL_GOALDONE_ORG_ID` - Root organization ID in Zitadel
- `SPRING_DATASOURCE_URL` - JDBC URL (PostgreSQL for prod/dev, H2 for local)
- `SPRING_DATASOURCE_USERNAME` - Database user
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `app.cors.allowed-origins` - CORS whitelist (dev: `https://dev.goaldone.de`, prod: `https://goaldone.de`)

**Required Environment Variables (Frontend):**
- Injected at runtime via `window.__env` from `frontend/src/assets/env.js`
  - `clientId` - Zitadel OIDC client ID
  - `issuerUri` - Zitadel issuer URI
  - `apiBasePath` - Backend API base URL (e.g., `http://localhost:8080/api/v1` for local)

**Secrets Location:**
- Development: `.env-dev` file in `infra/infra-setup/` (not committed)
- Production: Environment variables set via deployment platform (Kubernetes, Docker, etc.)
- Code: No hardcoded secrets; all external credentials via environment variables or runtime injection

## Webhooks & Callbacks

**Incoming:**
- None detected. Application does not expose webhook endpoints for external services to call.

**Outgoing:**
- None detected. Application does not call external webhooks or event-based integrations beyond Zitadel APIs.

**Callback URLs (OAuth2):**
- Authorization Code callback: `${window.location.origin}/callback` (handled by `angular-oauth2-oidc`)
- Logout redirect: `${window.location.origin}` (default home)
- Configured in `frontend/src/app/core/auth/auth.service.ts`

---

*Integration audit: 2026-05-02*
