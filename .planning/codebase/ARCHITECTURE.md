# Architecture

**Analysis Date:** 2026-05-02

## Pattern Overview

**Overall:** Stateless OAuth2/OpenID Connect resource server with Just-In-Time (JIT) user provisioning and multi-service architecture

**Key Characteristics:**
- Frontend and backend are separated; frontend initiates OIDC flows with Zitadel
- Backend acts as stateless OAuth2 resource server validating JWT tokens
- JIT provisioning synchronizes users from external IAM (Zitadel) to local database on first request
- Role-based access control (RBAC) extracted from JWT claims at authentication layer
- Multi-tenancy supported via organization-level role assignments
- Emerging auth-service provides custom OAuth2 authorization server capability

## Layers

**Authentication Layer (Frontend):**
- Purpose: OIDC client handling, token acquisition and management
- Location: `frontend/src/app/core/auth/`
- Contains: `AuthService` (token/OIDC config), `authGuard` (route protection), `authInterceptor` (Bearer token injection)
- Depends on: `angular-oauth2-oidc` library, Zitadel OIDC issuer
- Used by: All protected routes, all API requests

**Security Filter Chain (Backend):**
- Purpose: JWT validation, role extraction, JIT provisioning
- Location: `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java`, `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java`
- Contains: OAuth2 resource server config, JWT converter (Zitadel role claim mapping), JIT filter registration
- Depends on: Spring Security OAuth2, JWT utilities
- Used by: All incoming HTTP requests (enforced at `SecurityFilterChain`)

**JIT Provisioning Service (Backend):**
- Purpose: Create/update local database records (`UserAccountEntity`, `UserIdentityEntity`, `OrganizationEntity`) based on incoming JWT
- Location: `backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java`, `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java`
- Contains: Provisioning logic, race-condition handling, entity creation
- Depends on: `UserAccountRepository`, `UserIdentityRepository`, `OrganizationRepository`
- Used by: `JitProvisioningFilter` on every authenticated request

**Controller/API Layer (Backend):**
- Purpose: Thin layer implementing generated OpenAPI interfaces
- Location: `backend/src/main/java/de/goaldone/backend/controller/`
- Contains: `MemberManagementController`, `OrganizationManagementController`, `SuperAdminController`, etc.
- Depends on: Service layer, generated API models
- Used by: Frontend API calls, external clients

**Service Layer (Backend):**
- Purpose: Business logic for member management, organization management, user identity linking
- Location: `backend/src/main/java/de/goaldone/backend/service/`
- Contains: `MemberManagementService` (role assignment, member listing), `OrganizationManagementService`, `UserIdentityService`, `AccountLinkingService`
- Depends on: Repositories, Zitadel Management API client, current user resolver
- Used by: Controllers, other services

**Zitadel Integration Layer (Backend):**
- Purpose: Communication with external Zitadel Management API for user/grant management
- Location: `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java`
- Contains: Methods for user management, organization management, user grant operations
- Depends on: Zitadel Java SDK, REST client for v1 Management APIs
- Used by: Services requiring external user/role operations

**Repository Layer (Backend):**
- Purpose: JPA-based database access
- Location: `backend/src/main/java/de/goaldone/backend/repository/`
- Contains: Spring Data repositories for `UserAccountEntity`, `UserIdentityEntity`, `OrganizationEntity`, etc.
- Depends on: JPA/Hibernate
- Used by: Services, JIT provisioning

**Authorization Server (auth-service, New):**
- Purpose: Custom OAuth2 authorization server for token generation and management
- Location: `auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java`
- Contains: OAuth2 server configuration, registered clients, JWT signing keys
- Depends on: Spring Authorization Server, JWT libraries
- Used by: Future integration as custom OIDC/OAuth2 provider (currently in development)

## Data Flow

**Frontend Login (OIDC Flow):**

1. User clicks login on unauthenticated page
2. `authGuard` detects missing access token, calls `authService.initLoginFlow()`
3. `OAuthService` (from `angular-oauth2-oidc`) initiates PKCE code flow to Zitadel issuer URI
4. User authenticates with Zitadel (username/password)
5. Zitadel returns authorization code to callback URL (`/callback`)
6. `CallbackPageComponent` triggers `authService.loadDiscoveryDocumentAndTryLogin()`
7. Access token (and optional refresh token) stored in browser memory
8. User navigated to protected route (`/app`)

**Backend Request with JWT:**

1. Frontend makes API call with `Authorization: Bearer <token>` header (via `authInterceptor`)
2. `BearerTokenAuthenticationFilter` extracts and validates JWT signature using Zitadel public keys
3. `JwtAuthenticationConverter` extracts roles from claim `urn:zitadel:iam:org:project:roles`, creates `JwtAuthenticationToken` with `ROLE_` prefixed authorities
4. Request proceeds with authenticated context
5. `JitProvisioningFilter` (runs after `BearerTokenAuthenticationFilter`) extracts JWT subject (`sub`) claim
6. Service calls can use `SecurityContextHolder.getContext().getAuthentication()` or `CurrentUserResolver` to access JWT and resolve entities

**JIT Provisioning Flow:**

1. First authenticated request arrives with JWT
2. `JitProvisioningFilter.doFilterInternal()` extracts JWT from security context
3. Calls `JitProvisioningService.provisionUser(jwt)`
4. Service checks if `UserAccountEntity` with matching `zitadelSub` exists
5. If exists: updates `lastSeenAt` timestamp
6. If not exists:
   - Extracts `urn:zitadel:iam:user:resourceowner:id` (org ID) and `urn:zitadel:iam:user:resourceowner:name` (org name) from JWT
   - Finds or creates `OrganizationEntity` with Zitadel org ID
   - Creates new `UserIdentityEntity` (global identity)
   - Creates new `UserAccountEntity` linking to organization and identity
7. Logs provisioning event, continues request

**Member Management (Role Assignment):**

1. Organization admin calls `PATCH /api/v1/organizations/{orgId}/members/{zitadelUserId}/role` with new role
2. `MemberManagementController` extracts JWT subject via `SecurityContextHolder`
3. Service validates caller belongs to organization (via `UserAccountEntity`)
4. Service calls `ZitadelManagementClient.updateUserGrant()` with new role
5. Zitadel updates user grant, subsequent login will have new role in JWT claim
6. Local role caching could occur in database if needed (currently fetched per request)

**State Management:**

- **JWT as source of truth:** Roles are never cached locally; extracted from JWT claim `urn:zitadel:iam:org:project:roles` on every request
- **Local database:** Stores minimal state (`UserAccountEntity`, `UserIdentityEntity`, `OrganizationEntity`) for org/user lookup, audit, and linking
- **Zitadel as external state:** User details, grants, role assignments stored in Zitadel; backend syncs on demand via Management API

## Key Abstractions

**UserAccountEntity (Multi-tenancy Model):**
- Purpose: Represents a user's account within an organization (one identity can have multiple accounts across orgs)
- Examples: `backend/src/main/java/de/goaldone/backend/entity/UserAccountEntity.java`
- Pattern: One-to-many from `UserIdentityEntity`; one-to-one with `OrganizationEntity`; mapped via `zitadelSub` (user's external identifier)

**UserIdentityEntity (Cross-Org Identity):**
- Purpose: Global user identity consolidating multiple organizational accounts
- Examples: `backend/src/main/java/de/goaldone/backend/entity/UserIdentityEntity.java`
- Pattern: Created on first login; linked to multiple `UserAccountEntity` records when user joins new org

**CurrentUserResolver:**
- Purpose: Utility for extracting current user's JWT and resolving corresponding database entities
- Examples: `backend/src/main/java/de/goaldone/backend/service/CurrentUserResolver.java`
- Pattern: Used in services to avoid repeated `SecurityContextHolder` calls; methods: `extractJwt()`, `resolveCurrentAccount()`, `resolveCurrentOrganization()`

**JwtAuthenticationConverter (Role Claim Mapping):**
- Purpose: Maps Zitadel's nested role claim structure to Spring Security authorities
- Examples: `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` (lines 89-103)
- Pattern: Extracts `urn:zitadel:iam:org:project:roles` (JSON object with role names as keys), converts to list of `SimpleGrantedAuthority("ROLE_<name>")`

**AuthService (Frontend Token Management):**
- Purpose: Wrapper around `OAuthService` for OIDC flow initialization, token access, and role extraction
- Examples: `frontend/src/app/core/auth/auth.service.ts`
- Pattern: Methods: `initialize()` (configures OIDC client at app startup), `initLoginFlow()`, `logout()`, `getUserRoles()`, `getUserOrganizationId()`
- Token is decoded client-side via `decodeJwtToken()` for UI role-based display

## Entry Points

**Backend Application:**
- Location: `backend/src/main/java/de/goaldone/backend/BackendApplication.java`
- Triggers: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- Responsibilities: Spring Boot application initialization, loads all configurations and services

**Frontend Application:**
- Location: `frontend/src/main.ts`
- Triggers: `npm start` (dev server)
- Responsibilities: Bootstraps Angular app with configuration (providers, router, HTTP client, OAuth), calls `AuthService.initialize()` to set up OIDC

**Auth-Service Application (New):**
- Location: `auth-service/src/main/java/de/goaldone/authservice/AuthServiceApplication.java`
- Triggers: Separate service run
- Responsibilities: Standalone OAuth2 authorization server; provides custom token generation

**Frontend Auth Initialization:**
- Location: `frontend/src/app/app.config.ts` (line 40-44)
- Triggers: App startup via `APP_INITIALIZER`
- Responsibilities: Calls `AuthService.initialize()` which configures OIDC issuer, clientId, scopes and attempts to load discovery document and restore JWT from browser storage

**JWT Filter Chain:**
- Location: `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` (line 63-77)
- Triggers: Every HTTP request to protected endpoints
- Responsibilities: Validates JWT signature, extracts roles, registers JIT provisioning filter

## Error Handling

**Strategy:** Exceptions bubble up to `GlobalExceptionHandler` which converts to HTTP responses with appropriate status codes

**Patterns:**

- **JWT Validation Failure:** `BearerTokenAuthenticationFilter` returns 401 Unauthorized if JWT signature invalid or expired
- **Missing Organization Access:** `NotMemberOfOrganizationException` (thrown in `MemberManagementService.validateCallerBelongsToOrg()`) returns 403 Forbidden
- **Zitadel API Errors:** `ZitadelApiException` wraps API failures, includes HTTP status code and message
- **Last Admin Constraint:** Services check admin count before demoting/removing; throw `ResponseStatusException(HttpStatus.CONFLICT)` if attempting to remove last admin
- **Self-Removal Prevention:** `ResponseStatusException(HttpStatus.FORBIDDEN)` if user attempts to remove themselves from organization
- **JIT Provisioning Failure:** `JitProvisioningFilter` catches all exceptions and logs error (lines 46-51) but allows request to continue (prevents one service failure from breaking auth)

## Cross-Cutting Concerns

**Logging:** 
- Backend: SLF4J with Logback, configured in `application.yaml` (lines 26-30), defaults to INFO level
- Services and filters use `@Slf4j` annotation (Lombok) for logger injection
- Key events: User provisioning, role changes, Zitadel API calls

**Validation:** 
- Backend: Spring `@Validated` + `@Valid` on request parameters
- Frontend: Angular form validation, HTTP interceptor checks token existence before sending requests

**Authentication:** 
- Backend: JWT signature validation via `BearerTokenAuthenticationFilter` + custom `JwtAuthenticationConverter`
- Frontend: OIDC code flow via `angular-oauth2-oidc` library with PKCE code challenge

**Authorization:**
- Backend: Role-based (extracted from JWT claim), checked via Spring `@PreAuthorize` annotations (though not visible in this sample, the framework is enabled via `@EnableMethodSecurity`)
- Frontend: Route guards via `authGuard`, role display via decoded token in `AuthService.getUserRoles()`
- Org-level access: Explicit validation in services checking `UserAccountEntity.organizationId` matches request `orgId`

**CORS:**
- Backend: Configured in `SecurityConfig.corsConfigurationSource()` (lines 112-121)
- Allowed origins configurable via `app.cors.allowed-origins` property (defaults to `http://localhost:4200` for local dev)

---

*Architecture analysis: 2026-05-02*
