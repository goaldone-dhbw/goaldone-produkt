# Codebase Structure

**Analysis Date:** 2026-05-02

## Directory Layout

```
goaldone-projekt/
├── api-spec/
│   └── openapi.yaml                    # Single source of truth for API contracts
├── backend/                            # Spring Boot resource server (main application)
│   ├── src/main/java/de/goaldone/backend/
│   │   ├── config/                     # Spring security, Zitadel config
│   │   ├── controller/                 # API endpoints (generated interfaces + implementations)
│   │   ├── service/                    # Business logic (JIT, member mgmt, org mgmt)
│   │   ├── repository/                 # Spring Data JPA repositories
│   │   ├── entity/                     # JPA entities (UserAccount, UserIdentity, Organization)
│   │   ├── filter/                     # Servlet filters (JIT provisioning)
│   │   ├── exception/                  # Custom exceptions + global handler
│   │   ├── client/                     # Zitadel Management API client
│   │   └── scheduler/                  # Scheduled tasks (not auth-related)
│   ├── src/main/resources/
│   │   ├── application.yaml            # Base config (profiles, logging)
│   │   ├── application-local.yaml      # H2 in-memory DB (local dev)
│   │   ├── application-dev.yaml        # PostgreSQL (dev)
│   │   ├── application-prod.yaml       # PostgreSQL (production)
│   │   └── db/changelog/               # Liquibase database schema versions
│   ├── src/test/java/de/goaldone/backend/
│   │   ├── controller/                 # Integration tests for endpoints
│   │   ├── service/                    # Unit and integration tests for services
│   │   ├── filter/                     # Tests for security filters
│   │   └── scheduler/                  # Tests for scheduled tasks
│   └── pom.xml                         # Maven build config (Spring Boot, Zitadel SDK, OpenAPI generation)
├── auth-service/                       # Spring Authorization Server (custom OAuth2, emerging)
│   ├── src/main/java/de/goaldone/authservice/
│   │   ├── config/                     # Authorization server, security, token customization config
│   │   ├── controller/                 # Auth endpoints (login, token, etc.)
│   │   ├── service/                    # Auth business logic
│   │   ├── domain/                     # Auth domain models
│   │   ├── security/                   # Auth security components
│   │   └── repository/                 # Auth data repositories
│   ├── src/main/resources/
│   │   ├── application.yaml            # Base auth-service config
│   │   ├── application-local.yaml      # Local auth-service setup
│   │   └── db/changelog/               # Liquibase for auth-service DB
│   ├── src/test/java/de/goaldone/authservice/
│   │   ├── controller/                 # Auth endpoint tests
│   │   ├── integration/                # Integration test suites (account, invitation, oauth, etc.)
│   │   └── service/                    # Auth service tests
│   └── pom.xml                         # Maven config for auth-service
├── frontend/                           # Angular 21 SPA
│   ├── src/app/
│   │   ├── core/                       # Core application infrastructure
│   │   │   ├── auth/                   # OIDC/OAuth2 client (AuthService, authGuard, authInterceptor)
│   │   │   ├── services/               # Shared services (AccountStateService, etc.)
│   │   │   ├── layouts/                # App shell and layout components
│   │   │   └── accounts/               # Account-related utilities
│   │   ├── api/                        # Generated API client (git-ignored, do NOT edit)
│   │   │   ├── api/                    # Generated API service classes
│   │   │   └── model/                  # Generated request/response DTOs
│   │   ├── features/                   # Feature-specific pages (lazy-loadable)
│   │   │   ├── mainpage/               # Main dashboard
│   │   │   ├── tasks/                  # Task management page
│   │   │   ├── schedule/               # Schedule page
│   │   │   ├── org-settings/           # Organization settings (member management, etc.)
│   │   │   ├── user-settings/          # User account settings
│   │   │   ├── super-admins/           # Super admin features
│   │   │   ├── callback/               # OIDC callback handler
│   │   │   ├── startpage/              # Unauthenticated landing page
│   │   │   └── working-hours/          # Working hours configuration
│   │   ├── shared/                     # Shared components and utilities (PrimeNG wrappers, dialogs)
│   │   ├── app.routes.ts               # Route definitions with authGuard
│   │   ├── app.config.ts               # App configuration (HTTP interceptors, providers, OAuth)
│   │   ├── app.ts                      # Root component
│   │   └── GoaldoneTheme.ts            # Custom PrimeNG theme
│   ├── src/main.ts                     # Bootstrap entry point
│   ├── package.json                    # npm dependencies (@angular, PrimeNG, angular-oauth2-oidc, OpenAPI generator)
│   ├── tsconfig.json                   # TypeScript configuration
│   ├── vite.config.ts                  # Vite build configuration
│   ├── vitest.config.ts                # Vitest testing framework config
│   └── angular.json                    # Angular CLI configuration
├── infra/
│   └── infra-setup/                    # Docker Compose for local Zitadel + PostgreSQL
│       └── docker-compose.yaml
├── .planning/
│   ├── codebase/                       # Analysis documents (this directory)
│   │   ├── ARCHITECTURE.md
│   │   ├── STRUCTURE.md
│   │   └── (other codebase docs)
│   └── phases/                         # Planning phases for features
└── CLAUDE.md                           # Project guidelines for Claude AI
```

## Directory Purposes

**api-spec/**
- Purpose: OpenAPI 3.0 specification defining all API contracts
- Contains: `openapi.yaml` - single source of truth for backend endpoints, DTOs, and client models
- Key files: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/api-spec/openapi.yaml`

**backend/src/main/java/de/goaldone/backend/config/**
- Purpose: Spring security, OAuth2, and application configuration
- Contains: `SecurityConfig.java` (JWT validation, role extraction, JIT filter), `ZitadelConfig.java` (Zitadel SDK setup), `RestClientConfig.java` (HTTP client for Management APIs)
- Key files: `SecurityConfig.java`, `ZitadelConfig.java`

**backend/src/main/java/de/goaldone/backend/filter/**
- Purpose: Servlet filters applied to request pipeline
- Contains: `JitProvisioningFilter.java` - runs after JWT validation, provisions users in local DB on first request
- Key files: `JitProvisioningFilter.java`

**backend/src/main/java/de/goaldone/backend/service/**
- Purpose: Business logic layer
- Contains: `JitProvisioningService.java` (user/org provisioning), `MemberManagementService.java` (role assignment, member listing), `OrganizationManagementService.java`, `CurrentUserResolver.java` (JWT to entity mapping)
- Key auth-related files: `JitProvisioningService.java`, `MemberManagementService.java`, `CurrentUserResolver.java`

**backend/src/main/java/de/goaldone/backend/entity/**
- Purpose: JPA entity models mapping to database tables
- Contains: `UserAccountEntity.java` (per-org user account with `zitadelSub`, `organizationId`, `userIdentityId`), `UserIdentityEntity.java` (global identity), `OrganizationEntity.java` (organization with `zitadelOrgId`)
- Key files: `UserAccountEntity.java`, `UserIdentityEntity.java`, `OrganizationEntity.java`

**backend/src/main/java/de/goaldone/backend/client/**
- Purpose: External API clients
- Contains: `ZitadelManagementClient.java` (Zitadel Management API interactions for user/grant management), `ZitadelUserInfoClient.java` (user info endpoint)
- Key files: `ZitadelManagementClient.java`

**backend/src/main/java/de/goaldone/backend/exception/**
- Purpose: Custom exceptions and centralized error handling
- Contains: `GlobalExceptionHandler.java` (maps exceptions to HTTP responses), `NotMemberOfOrganizationException.java`, `ZitadelApiException.java`
- Key files: `GlobalExceptionHandler.java`

**backend/src/main/resources/**
- Purpose: Runtime configuration and database schemas
- Contains: `application.yaml` (base config with logging), `application-{local|dev|prod}.yaml` (environment-specific DB/Zitadel URLs), `db/changelog/` (Liquibase migration files)
- Config pattern: Properties like `spring.security.oauth2.resourceserver.jwt.issuer-uri`, `zitadel.service-account-token`

**backend/src/test/java/de/goaldone/backend/**
- Purpose: Test suites for backend
- Contains: `filter/JitProvisioningFilterTest.java`, `service/JitProvisioningServiceTest.java`, controller integration tests
- Key auth tests: `JitProvisioningFilterTest.java` (verifies filter behavior with JWT), `MemberManagementServiceTest.java`, `CurrentUserResolverTest.java`

**auth-service/src/main/java/de/goaldone/authservice/config/**
- Purpose: Authorization server configuration
- Contains: `AuthorizationServerConfig.java` (OAuth2 server setup, registered clients, JWT signing), `TokenCustomizerConfig.java` (JWT claim customization)
- Key files: `AuthorizationServerConfig.java`

**frontend/src/app/core/auth/**
- Purpose: OIDC/OAuth2 client implementation
- Contains: `auth.service.ts` (OIDC configuration, token management, role extraction), `auth.guard.ts` (route protection), `auth.interceptor.ts` (Bearer token injection)
- Key files: `auth.service.ts`, `auth.guard.ts`, `auth.interceptor.ts`

**frontend/src/app/api/**
- Purpose: Generated OpenAPI client (auto-generated, git-ignored)
- Contains: API service classes and DTOs generated from `api-spec/openapi.yaml`
- Location: `/frontend/src/app/api/` (entire directory is git-ignored)
- Generation: `npm run generate-api` (via `@openapitools/openapi-generator-cli`)

**frontend/src/app/features/**
- Purpose: Feature-specific pages (lazy-loadable)
- Contains: `org-settings/` (member management, org role assignment), `super-admins/` (super admin features), other feature modules
- Key auth-related: `org-settings/` calls member management APIs with current org ID

**frontend/src/app/shared/**
- Purpose: Shared UI components (PrimeNG wrappers, dialogs)
- Contains: Reusable components used across features
- Pattern: No business logic; composition only

**infra/infra-setup/**
- Purpose: Local development infrastructure
- Contains: `docker-compose.yaml` with Zitadel and PostgreSQL containers
- Files: `.env-dev-example` (template for `.env` with Zitadel config)

## Key File Locations

**Entry Points:**

- Backend: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/BackendApplication.java`
- Frontend: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/main.ts`
- Auth-Service: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/auth-service/src/main/java/de/goaldone/authservice/AuthServiceApplication.java`

**Configuration:**

- Backend base: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/resources/application.yaml`
- Backend security: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java`
- Frontend app config: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/app.config.ts`
- Frontend routes: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/app.routes.ts`

**Core Auth Logic:**

- Backend JIT filter: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java`
- Backend JIT service: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java`
- Backend current user resolver: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/service/CurrentUserResolver.java`
- Backend member management: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/service/MemberManagementService.java`
- Zitadel client: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java`
- Frontend auth service: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/core/auth/auth.service.ts`
- Frontend auth guard: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/core/auth/auth.guard.ts`
- Frontend auth interceptor: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/core/auth/auth.interceptor.ts`

**Database & Models:**

- User account entity: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/entity/UserAccountEntity.java`
- User identity entity: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/entity/UserIdentityEntity.java`
- Organization entity: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/entity/OrganizationEntity.java`
- Liquibase changesets: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/resources/db/changelog/changes/`

**Tests:**

- JIT filter tests: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/test/java/de/goaldone/backend/filter/JitProvisioningFilterTest.java`
- JIT service tests: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/test/java/de/goaldone/backend/service/JitProvisioningServiceTest.java`
- Member management tests: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/test/java/de/goaldone/backend/service/MemberManagementServiceTest.java`
- Auth service tests: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/core/auth/auth.service.spec.ts`

## Naming Conventions

**Files:**

- Java: PascalCase, suffix by type (e.g., `SecurityConfig.java`, `JitProvisioningService.java`, `UserAccountEntity.java`, `NotMemberOfOrganizationException.java`)
- TypeScript: kebab-case for feature dirs/files, PascalCase for exported classes (e.g., `auth.service.ts`, `auth.guard.ts`, `auth.interceptor.ts`)
- Database migrations: Liquibase XML files in `db/changelog/changes/` named by sequence and purpose (e.g., `001_create_tables.xml`)

**Classes/Components:**

- Services: suffix `Service` (e.g., `JitProvisioningService`, `AuthService`)
- Entities: suffix `Entity` (e.g., `UserAccountEntity`)
- Exceptions: suffix `Exception` (e.g., `NotMemberOfOrganizationException`)
- Repositories: suffix `Repository` (e.g., `UserAccountRepository`)
- Filters: suffix `Filter` (e.g., `JitProvisioningFilter`)
- Controllers: suffix `Controller` (e.g., `MemberManagementController`)
- Guards: suffix with domain then `guard` (e.g., `authGuard`)
- Interceptors: suffix with domain then `interceptor` (e.g., `authInterceptor`)

**Properties:**

- Backend config: kebab-case (e.g., `spring.security.oauth2.resourceserver.jwt.issuer-uri`, `zitadel.service-account-token`)
- Environment variables: UPPER_SNAKE_CASE (e.g., `ZITADEL_ISSUER_URI`, `ZITADEL_SERVICE_ACCOUNT_TOKEN`)
- Java fields: camelCase with `@Column` annotations indicating snake_case DB columns

**Directories:**

- Services: `service/`, `services/` (depending on nesting depth and context)
- Controllers: `controller/`
- Entities: `entity/`
- Repositories: `repository/`
- Config: `config/`
- Filters: `filter/`
- Exceptions: `exception/`
- Client libraries: `client/`
- Feature modules: `features/[feature-name]/`
- Core/shared infrastructure: `core/`, `shared/`

## Where to Add New Code

**New API Endpoint:**
1. Define in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/api-spec/openapi.yaml`
2. Backend: Run `./mvnw generate-sources` to generate controller interface in `de.goaldone.backend.api`
3. Backend: Create controller implementation in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/controller/[Domain]Controller.java`
4. Backend: Implement business logic in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/service/[Domain]Service.java`
5. Frontend: Run `npm run generate-api` to generate client in `src/app/api/`
6. Frontend: Use generated service in feature component

**New Auth-Related Filter/Interceptor:**
- Backend filter: Add to `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/filter/`, register in `SecurityConfig.java` filter chain
- Frontend interceptor: Add to `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/core/auth/`, register in `app.config.ts` `withInterceptors([])`

**New Database Entity:**
1. Create entity class in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/entity/[Entity]Entity.java`
2. Create repository in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/repository/[Entity]Repository.java`
3. Create Liquibase changelog in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/resources/db/changelog/changes/[seq]_[description].xml`
4. Reference in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/resources/db/changelog/db.changelog-master.xml`

**New Service Class:**
- Location: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/java/de/goaldone/backend/service/[Domain]Service.java`
- Inject repositories via constructor (Spring will auto-wire via `@RequiredArgsConstructor` Lombok annotation)
- Use `CurrentUserResolver` if you need to access current authenticated user

**New Frontend Feature:**
1. Create directory: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/features/[feature-name]/`
2. Create component and route in `app.routes.ts` (with `canActivate: [authGuard]` if protected)
3. Use generated API client from `src/app/api/`
4. Use PrimeNG components for UI (DO NOT create custom components)

**New Unit Test:**
- Location: Colocated with source file, suffix `.test.ts` (frontend) or `.java` (backend)
- Backend: Use MockMvc for controller tests, Mockito for unit tests
- Frontend: Use Vitest with Angular testing utilities

## Special Directories

**Generated API Client (frontend):**
- Location: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/frontend/src/app/api/`
- Purpose: Auto-generated OpenAPI client (TypeScript services + DTOs)
- Generated: `npm run generate-api` (via `@openapitools/openapi-generator-cli`)
- Committed: NO (git-ignored)
- Editing: NEVER edit manually; regenerate after `api-spec/openapi.yaml` changes

**Database Changelogs:**
- Location: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/resources/db/changelog/changes/`
- Purpose: Liquibase versioned schema migrations
- Pattern: XML files numbered sequentially (e.g., `001_`, `002_`, etc.)
- Reference: All changesets listed in `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/backend/src/main/resources/db/changelog/db.changelog-master.xml`

**Planning/Phases:**
- Location: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/.planning/phases/`
- Purpose: Implementation planning documents for features (generated by `/gsd:plan-phase`)
- Committed: YES
- Structure: Numbered phases (01-, 02-, etc.) with tasks and implementation details

**Planning/Codebase Analysis:**
- Location: `/Users/johannes/IdeaProjects/goaldone/goaldone-projekt/.planning/codebase/`
- Purpose: Static codebase analysis documents (ARCHITECTURE.md, STRUCTURE.md, TESTING.md, CONVENTIONS.md, CONCERNS.md, INTEGRATIONS.md, STACK.md)
- Committed: YES
- Consumed by: `/gsd:plan-phase` and `/gsd:execute-phase` commands

---

*Structure analysis: 2026-05-02*
