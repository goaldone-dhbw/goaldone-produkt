# Technology Stack

**Analysis Date:** 2026-05-02

## Languages

**Primary:**
- Java 21 - Backend (Spring Boot 4.0.5), configured in `backend/pom.xml`
- TypeScript 5.9.2 - Frontend (Angular 21.2.0), configured in `frontend/package.json`

**Secondary:**
- JavaScript (IIFE for runtime configuration) - `frontend/src/assets/env.js`
- YAML - Configuration files (`application.yaml`, `docker-compose.yml`)
- SQL - Database schemas via Liquibase changelogs

## Runtime

**Environment:**
- **Backend:** Java 21 (JVM)
- **Frontend:** Node.js (runtime for npm tooling; outputs static HTML/CSS/JS for browser)
- **Local Infrastructure:** Docker Compose (Zitadel + PostgreSQL)

**Package Manager:**
- **Backend:** Maven 3.x (via Maven Wrapper `./mvnw`)
- **Frontend:** npm 11.11.0 (specified in `frontend/package.json` packageManager field)
- **Lockfile:** 
  - Backend: `backend/pom.xml` (maven metadata)
  - Frontend: `frontend/package-lock.json` (standard npm lockfile)

## Frameworks

**Core:**
- **Spring Boot** 4.0.5 - Backend web framework, dependency management via `backend/pom.xml`
- **Angular** 21.2.0 - Frontend SPA framework, core in `frontend/package.json`
- **PrimeNG** 21.1.6 - UI component library (Angular), mandatory per CLAUDE.md

**Testing:**
- **Spring Boot Test** (in `backend/pom.xml`) - Backend unit/integration testing with MockMvc
- **spring-security-test** - Security context testing for JWT and filters
- **WireMock** 3.12.0 - HTTP mocking for external service tests (Zitadel API simulation)
- **Vitest** 4.0.8 - Frontend unit testing framework
- **jsdom** 28.0.0 - DOM simulation for frontend tests

**Build/Dev:**
- **OpenAPI Generator** 7.10.0 (Maven plugin) - Generates backend API interfaces and Spring controllers from `api-spec/openapi.yaml`
- **OpenAPI Generator CLI** 2.13.4 (npm) - Generates TypeScript Angular API client from `api-spec/openapi.yaml`
- **Apache Maven Compiler Plugin** - Java compilation with Lombok annotation processing
- **JaCoCo** 0.8.12 - Code coverage reporting for backend tests
- **TailwindCSS** 4.1.12 - Utility CSS framework (layout/spacing only per CLAUDE.md)
- **Prettier** 3.8.1 - Code formatting (frontend)
- **Angular CLI** 21.2.7 - Development server and build tooling

## Key Dependencies

**Critical Auth Stack:**
- **spring-security-oauth2-resource-server** - OAuth2 resource server (JWT validation, stateless auth)
- **spring-boot-starter-security** - Spring Security core, CORS, method-level security (@EnableMethodSecurity)
- **angular-oauth2-oidc** 20.0.2 - Frontend OIDC client, handles login flow and token refresh
- **Zitadel Java SDK** (`io.github.zitadel:client` 4.1.2) - Official typed client for Zitadel Management API v2
  - Used in `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java`
  - Provides user, organization, authorization management

**ORM & Persistence:**
- **spring-boot-starter-data-jpa** - JPA data layer (Hibernate under the hood)
- **spring-boot-starter-liquibase** - Database schema versioning and migrations
- **PostgreSQL** (driver: `org.postgresql:postgresql`) - Production/dev database, H2 fallback for local
- **h2** - In-memory database for local development (Spring profile: `local`)

**HTTP & Client:**
- **Spring RestClient** (Spring 6.1+, built into Spring Boot 4.0.5) - Modern HTTP client for external APIs
  - Configured with HTTP/1.1 in `backend/src/main/java/de/goaldone/backend/config/RestClientConfig.java`
  - Used for Zitadel OIDC UserInfo endpoint and Management v1 APIs

**Data Handling:**
- **Jackson** (spring-boot-starter-json) - JSON serialization/deserialization
- **jackson-databind-nullable** 0.2.6 - Handles OpenAPI-generated nullable fields
- **Swagger/OpenAPI Annotations** 2.2.21 - API documentation annotations

**Utilities:**
- **Lombok** - Boilerplate reduction (@Data, @RequiredArgsConstructor, @Slf4j)
- **RxJS** 7.8.0 - Reactive streams (frontend, used by angular-oauth2-oidc)
- **PrimeIcons** 7.0.0 - Icon library for PrimeNG components

**Infrastructure:**
- **spring-boot-h2console** - Web console for H2 (local development only)
- **spring-boot-starter-actuator** - Health checks and metrics
- **spring-boot-starter-validation** - Bean validation (Jakarta Validation API)
- **spring-boot-starter-webmvc** - Spring MVC servlet-based web framework

## Configuration

**Environment:**
- **Backend:** Spring profiles in `src/main/resources/`
  - `application.yaml` - Base config (JWT issuer URI, Zitadel service account token, context path: `/api/v1`)
  - `application-local.yaml` - H2 in-memory, debug logging, localhost Zitadel
  - `application-dev.yaml` - PostgreSQL with dev credentials, CORS for `https://dev.goaldone.de`
  - `application-prod.yaml` - PostgreSQL production, CORS for `https://goaldone.de`
- **Frontend:** Runtime environment injection via `window.__env` (set by `frontend/src/assets/env.js` before Angular bootstrap)
  - Supports dynamic issuer URI, client ID, API base path without rebuild
  - Fallbacks in `frontend/src/environments/environment.ts` for development

**Build:**
- **Backend:**
  - Maven properties: Java 21 target in `backend/pom.xml` line 30
  - OpenAPI Generator configured for Spring Boot 3, interface-only generation, Zitadel code generation at `generate-sources` phase
  - Build outputs to `target/` directory (JAR packaging via Spring Boot Maven plugin)
- **Frontend:**
  - Angular CLI with TypeScript strict mode enabled
  - Build output: `dist/frontend/browser/` (static assets)
  - API client auto-generated to `src/app/api/` (git-ignored) before `ng serve` or `ng build`

## Platform Requirements

**Development:**
- Java 21 JDK
- Node.js (any recent version; npm 11.11.0 specified)
- Docker & Docker Compose (for local Zitadel + PostgreSQL infrastructure)
- Maven 3.x (provided via Maven Wrapper)

**Local Credentials:**
- `.env-dev` file (infrastructure setup) with Zitadel master key, PostgreSQL passwords, certificates
- `frontend/src/assets/env.js` with Zitadel client ID, issuer URI, API base path
- No `.env` file in backend root (Spring profiles handle configuration)

**Production:**
- Java 21 JVM (container or server)
- PostgreSQL 14+ database
- External Zitadel instance (OIDC issuer URI, service account token)
- Web server / reverse proxy (recommended for static frontend assets and API routing)

---

*Stack analysis: 2026-05-02*
