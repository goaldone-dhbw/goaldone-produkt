# Phase 5: Member Management Rewrite & Cutover - Research

**Researched:** 2026-05-03
**Domain:** Spring Boot service-to-service OAuth2 client_credentials, Liquibase migrations, REST client integration
**Confidence:** HIGH

## Summary

Phase 5 replaces all Zitadel SDK usage in the backend with calls to the auth-service management API, performs a database PK unification (switching from local UUIDs + `authCompanyId`/`authUserId` mapping columns to using auth-service UUIDs directly as PKs), and removes the `io.github.zitadel:client` dependency entirely.

The auth-service already has most of the management API endpoints needed: invitations (POST/GET/DELETE), memberships (DELETE/PATCH), user CRUD, and organizations. The primary gap is a **bulk members endpoint** (`GET /api/v1/organizations/{id}/members`) that returns all members with roles and status for a given org. The `ClientSeedingRunner` already seeds an M2M client (`mgmt-client`) with `client_credentials` grant and `mgmt:admin` scope. The `DefaultSecurityConfig` requires authentication (JWT) for `/api/v1/**` endpoints, but does NOT yet enforce a scope guard — this needs to be added.

**Primary recommendation:** Start with OpenAPI spec updates (UUID types for userId params), then implement the auth-service bulk members endpoint, then wire the backend's `AuthServiceManagementClient` with Spring OAuth2 `client_credentials`, then rewrite services, then Liquibase PK migration, and finally clean up all Zitadel references.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Backend authenticates to auth-service management API using OAuth2 `client_credentials` grant with dedicated service client
- **D-02:** M2M access token cached in memory via `OAuth2AuthorizedClientManager`; refreshed proactively at <5min remaining
- **D-03:** Backend M2M client seeded by `ClientSeedingRunner` (same mechanism as frontend OIDC client)
- **D-04:** Management API requires `mgmt` scope to distinguish M2M from end-user JWTs
- **D-05:** Backend → auth-service calls use path parameters (not X-Org-ID header)
- **D-06:** Eager pending membership — `MembershipEntity` with `status=INVITED` created immediately on invite
- **D-07:** Pending membership stores `invitation_id` (UUID); `auth_user_id` remains null until acceptance
- **D-08:** Resend calls auth-service again and updates stored invitationId; Cancel marks local + calls auth-service DELETE
- **D-09:** `listMembers` returns both ACTIVE and INVITED members with `MemberStatus` field
- **D-10:** New bulk endpoint: `GET /api/v1/organizations/{authCompanyId}/members`
- **D-11:** Backend uses `OrganizationEntity.authCompanyId` (UUID from auth-service) for bulk endpoint call
- **D-12:** Roles from auth-service Membership entity's role field
- **D-13:** Auth-service UUIDs become entity PKs; `authCompanyId`/`authUserId` columns dropped
- **D-14:** Liquibase migration: `id = authCompanyId` / `id = authUserId`, then drop mapping columns
- **D-15:** No separate local PKs after Phase 5
- **D-16:** All member operation signatures use UUID (replacing String zitadelUserId)
- **D-17:** `MemberResponse.userId` is UUID; `zitadelUserId` field removed
- **D-18:** Dual sync for role/remove: call auth-service first, update local DB only on success
- **D-19:** Auth-service enforces last-admin guard (returns 409); backend maps to ResponseStatusException
- **D-20:** StartupValidator → auth-service reachability check only
- **D-21:** Full deletion of ZitadelManagementClient, ZitadelConfig, ZitadelApiException, ZitadelUserInfo, ZitadelUserInfoClient; remove `io.github.zitadel:client` from pom.xml

### Agent's Discretion
- Internal naming of the `AuthServiceManagementClient` class
- HTTP client choice for backend → auth-service (RestClient vs WebClient)
- Error handling strategy details (exception class naming)
- Liquibase changeset ordering/splitting approach

### Deferred Ideas (OUT OF SCOPE)
- Phase 6 scope: Restoring 100+ removed tests
- Admin Console UI for auth-service
- Social Login / 2FA / WebAuthn
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MEM-01 | Invite members to organization via auth-service invitation API | Auth-service POST /api/v1/invitations exists; backend needs AuthServiceManagementClient to call it |
| MEM-02 | Invitations stored in auth-service; backend receives acceptance | Auth-service Invitation entity stores invitations; backend stores invitation_id in MembershipEntity |
| MEM-03 | Accept invitation → user added to org with role | JIT provisioning (Phase 2/3) handles this; Phase 5 ensures pending membership transitions to ACTIVE |
| MEM-04 | Change member role via backend → auth-service Membership API | PATCH /api/v1/users/{userId}/memberships/{companyId} exists (has TODO in implementation) |
| MEM-05 | Remove member via backend → auth-service Membership API | DELETE /api/v1/users/{userId}/memberships/{companyId} exists (has TODO in implementation) |
| MEM-06 | All member operations restricted to COMPANY_ADMIN | Existing @PreAuthorize guards on MemberManagementController continue unchanged |
| MEM-07 | Last admin guard: cannot remove/demote last admin | Auth-service MembershipManagementController already validates; backend maps 409 |
| MEM-08 | Member list endpoint returns users with roles and status | New GET /api/v1/organizations/{id}/members endpoint needed in auth-service |
| AUTHZ-01 | RBAC works per organization | Existing @authz expressions + X-Org-ID pattern from Phase 3.1 |
| AUTHZ-02 | Users see only their organizations | JWT orgs claim + membership validation continues |
| AUTHZ-03 | Different roles in different organizations | Multi-org membership model already supports this |
| AUTHZ-04 | COMPANY_ADMIN can invite/manage/remove | @PreAuthorize hasOrgRole expression unchanged |
| AUTHZ-05 | Non-admin users cannot manage members | Same authorization expressions |
| TEST-03 | Member management tests pass | Existing tests need rewrite for new client; new integration tests needed |
| TEST-04 | Organization management tests pass | OrganizationManagementService also uses ZitadelClient — must be rewritten |
| TEST-06 | Role-based access control tests verify authorization | MockMvc tests with JWT authorities |
| INFRA-01 | Zitadel SDK removed from pom.xml | io.github.zitadel:client v4.1.2 at line 108 |
| INFRA-02 | ZitadelConfig, ZitadelManagementClient, ZitadelProperties deleted | 5 classes to delete |
| INFRA-03 | StartupValidator updated | Rewrite to auth-service health check |
| INFRA-05 | Zitadel credentials removed from all config files | application.yaml lines 15-19, application-local.yaml line 19 |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Member invitation flow | API / Backend | Auth-Service | Backend orchestrates; auth-service creates invitation + sends email |
| Member list data | Auth-Service | API / Backend | Auth-service owns user/membership data; backend proxies to frontend |
| Role change / removal | API / Backend | Auth-Service | Backend validates caller auth, delegates to auth-service for persistence |
| Last-admin guard | Auth-Service | — | Auth-service enforces constraint; backend maps errors |
| M2M authentication | API / Backend | Auth-Service | Backend obtains token; auth-service validates it |
| ID unification (PK migration) | Database / Storage | — | Liquibase changeset in backend |
| OpenAPI contract | API / Backend | — | Single source of truth for frontend ↔ backend |
| Scope guard for management API | Auth-Service | — | SecurityFilterChain must enforce scope claim |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.x (existing) | Application framework | Already in use [VERIFIED: pom.xml] |
| spring-security-oauth2-client | (Spring Boot managed) | OAuth2 client_credentials flow | Standard Spring approach for M2M token management [VERIFIED: Spring docs] |
| Spring RestClient | (Spring Boot managed) | HTTP client for auth-service calls | Already used by ZitadelManagementClient [VERIFIED: codebase] |
| Liquibase | (existing) | DB migrations | Already established pattern with 12 changesets [VERIFIED: codebase] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| OAuth2AuthorizedClientManager | Spring Security | Token lifecycle management | M2M token caching + auto-refresh |
| InMemoryOAuth2AuthorizedClientService | Spring Security | Token storage | In-memory cache for M2M tokens |

### No New Dependencies
Phase 5 requires no new Maven dependencies. The `spring-boot-starter-oauth2-client` is needed if not already present for the `client_credentials` flow. The Zitadel SDK (`io.github.zitadel:client:4.1.2`) is **removed**.

**Installation (additions to pom.xml):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

**Removal from pom.xml:**
```xml
<!-- DELETE -->
<dependency>
    <groupId>io.github.zitadel</groupId>
    <artifactId>client</artifactId>
    <version>4.1.2</version>
</dependency>
```

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────┐          ┌──────────────────────┐          ┌─────────────────────┐
│   Frontend      │──────────▶  Backend (Spring)     │──────────▶  Auth-Service        │
│   (Angular)     │  REST     │                      │  M2M     │  (Spring Auth Srv)  │
│                 │  + JWT    │  MemberMgmtService   │  REST    │                     │
│                 │  + X-Org  │  MemberInviteService │  + OAuth │  /api/v1/invitations│
│                 │◀──────────│  AuthSvcMgmtClient   │◀─────────│  /api/v1/users/…    │
└─────────────────┘           │                      │          │  /api/v1/orgs/…/mem │
                              │  ┌──────────┐        │          │                     │
                              │  │ OAuth2   │        │          │  SecurityFilterChain│
                              │  │ Client   │ token  │          │  (scope=mgmt check) │
                              │  │ Manager  │────────│──────────▶  /oauth2/token      │
                              │  └──────────┘        │          │                     │
                              │                      │          └─────────────────────┘
                              │  ┌──────────┐        │
                              │  │ Liquibase│        │
                              │  │ PK Mig.  │        │
                              │  └──────────┘        │
                              └──────────────────────┘
```

**Data Flow (Invite Member):**
1. Frontend → `POST /organization/members/invite` (JWT + X-Org-ID header)
2. Backend validates caller is COMPANY_ADMIN via `@PreAuthorize`
3. Backend calls `POST /api/v1/invitations` on auth-service (M2M token)
4. Auth-service creates invitation, sends email, returns `InvitationResponse` with `id`
5. Backend creates `MembershipEntity(status=INVITED, invitation_id=response.id)`
6. Returns 201 to frontend

### Recommended Project Structure (New/Modified Files)

```
backend/src/main/java/de/goaldone/backend/
├── client/
│   ├── AuthServiceManagementClient.java      # NEW - replaces ZitadelManagementClient
│   ├── AuthServiceManagementException.java   # NEW - replaces ZitadelApiException  
│   └── dto/                                  # NEW - DTOs for auth-service responses
│       ├── AuthMemberResponse.java
│       └── AuthMemberListResponse.java
├── config/
│   ├── AuthServiceClientConfig.java          # NEW - OAuth2 client_credentials setup
│   ├── StartupValidator.java                 # REWRITE - health check only
│   └── [DELETE ZitadelConfig.java]
├── service/
│   ├── MemberManagementService.java          # REWRITE
│   ├── MemberInviteService.java              # REWRITE
│   ├── MembershipDeletionService.java        # MODIFY - remove Zitadel call
│   ├── OrganizationManagementService.java    # MODIFY - replace Zitadel calls
│   ├── SuperAdminService.java                # MODIFY - replace Zitadel calls
│   └── UserService.java                      # MODIFY - remove Zitadel dep
└── entity/
    ├── MembershipEntity.java                 # MODIFY - add invitation_id, status
    ├── OrganizationEntity.java               # MODIFY - drop authCompanyId (after PK migration)
    └── UserEntity.java                       # MODIFY - drop authUserId (after PK migration)

auth-service/src/main/java/de/goaldone/authservice/
├── controller/
│   └── OrganizationManagementController.java # MODIFY - add GET /{id}/members
├── dto/
│   └── MemberListItemResponse.java           # NEW
├── service/
│   ├── OrganizationManagementService.java    # MODIFY - add listMembers
│   └── UserManagementService.java            # MODIFY - implement actual delete/update membership
└── config/
    └── DefaultSecurityConfig.java            # MODIFY - add scope guard for mgmt
```

### Pattern 1: Spring OAuth2 Client Credentials for M2M

**What:** Backend obtains M2M token from auth-service's `/oauth2/token` endpoint using `client_credentials` grant, caches it, and attaches it to outgoing requests.

**When to use:** All backend → auth-service management API calls.

**Example:**
```java
// Source: Spring Security OAuth2 Client documentation [ASSUMED - standard pattern]
@Configuration
public class AuthServiceClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {
        
        OAuth2AuthorizedClientService authorizedClientService = 
            new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
            new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        
        ClientCredentialsOAuth2AuthorizedClientProvider provider = 
            new ClientCredentialsOAuth2AuthorizedClientProvider();
        // Token refresh happens automatically when token expires
        manager.setAuthorizedClientProvider(provider);
        
        return manager;
    }

    @Bean
    public RestClient authServiceRestClient(OAuth2AuthorizedClientManager clientManager,
                                            @Value("${auth-service.base-url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestInterceptor((request, body, execution) -> {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("auth-service-mgmt")
                    .principal("backend-service")
                    .build();
                OAuth2AuthorizedClient client = clientManager.authorize(authorizeRequest);
                request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                return execution.execute(request, body);
            })
            .build();
    }
}
```

**application.yaml addition:**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          auth-service-mgmt:
            client-id: ${MGMT_CLIENT_ID:mgmt-client}
            client-secret: ${MGMT_CLIENT_SECRET:mgmt-secret}
            authorization-grant-type: client_credentials
            scope: mgmt:admin
        provider:
          auth-service-mgmt:
            token-uri: ${AUTH_SERVICE_URL:http://localhost:9000}/oauth2/token

auth-service:
  base-url: ${AUTH_SERVICE_URL:http://localhost:9000}
```

### Pattern 2: Dual Sync (Auth-Service First, Local DB Second)

**What:** For mutating operations (role change, remove), call auth-service first. Only update local DB if auth-service succeeds.

**Example:**
```java
// Source: D-18 decision
public void changeMemberRole(UUID orgId, UUID userId, ChangeRoleRequest request) {
    // 1. Call auth-service (authoritative)
    try {
        authServiceClient.updateMembershipRole(userId, orgId, request.getRole());
    } catch (AuthServiceManagementException e) {
        if (e.getStatusCode() == 409) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED");
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service error");
    }
    
    // 2. Update local DB only on success
    // (local membership role update if stored locally)
}
```

### Pattern 3: Scope Guard in Auth-Service SecurityFilterChain

**What:** Management API endpoints require the `mgmt:admin` scope in the JWT to prevent end-user tokens from calling management endpoints.

**Example:**
```java
// Modification to DefaultSecurityConfig.managementApiSecurityFilterChain
@Bean
@Order(1)
public SecurityFilterChain managementApiSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/v1/**")
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/api/v1/invitations/*/status").permitAll()
            .anyRequest().hasAuthority("SCOPE_mgmt:admin")
        )
        .oauth2ResourceServer(resourceServer -> resourceServer
            .jwt(Customizer.withDefaults()));
    return http.build();
}
```

### Anti-Patterns to Avoid

- **Removing Zitadel SDK before services are rewritten:** The backend won't compile. SDK removal must be the LAST step.
- **Doing PK migration before service rewrite:** Services still reference `authCompanyId`/`authUserId`. Rewrite services first, then migrate PKs.
- **Calling auth-service with end-user JWT:** Management endpoints should reject user tokens (missing `mgmt:admin` scope). Always use M2M token.
- **Inconsistent state on partial failure:** If auth-service call succeeds but local DB update fails, data is inconsistent. Use `@Transactional` carefully — auth-service call should be OUTSIDE the transaction boundary, or use compensation.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token caching + refresh | Custom token cache with expiry logic | `OAuth2AuthorizedClientManager` + `ClientCredentialsOAuth2AuthorizedClientProvider` | Handles token expiry, concurrent access, retry automatically |
| HTTP client with auth | Manual `Authorization` header setting | RestClient + `ClientHttpRequestInterceptor` | Consistent, testable, Spring-idiomatic |
| Last-admin validation | Duplicate count logic in backend | Auth-service 409 response | Single source of truth; avoid split-brain |
| DB migration | Manual SQL scripts | Liquibase changesets | Versioned, rollback-capable, already established |

## Common Pitfalls

### Pitfall 1: Token Scope Mismatch
**What goes wrong:** M2M client registered with scope `mgmt:admin` but security config checks for `SCOPE_mgmt` (without `:admin` suffix).
**Why it happens:** Spring Security prefixes scope authorities with `SCOPE_` and uses the exact scope string.
**How to avoid:** Verify the scope name is consistent: `ClientSeedingRunner` uses `mgmt:admin`, `DefaultSecurityConfig` must check `hasAuthority("SCOPE_mgmt:admin")`.
**Warning signs:** 403 on all management API calls even with valid M2M token.

### Pitfall 2: PK Migration Breaking FK References
**What goes wrong:** After changing `organizations.id` from local UUID to auth-service UUID, all FK references (memberships.organization_id, tasks via membership, etc.) become invalid.
**Why it happens:** FK values reference the OLD PK value.
**How to avoid:** Migration must update FK columns in dependent tables BEFORE changing the PK. Or: since the `id` column value changes, all rows referencing it must be updated.
**Warning signs:** Foreign key constraint violations on application startup.

### Pitfall 3: MembershipManagementController TODOs
**What goes wrong:** The auth-service `MembershipManagementController` has `// TODO: Implement actual membership deletion/update in service layer`. Backend calls these endpoints expecting real behavior.
**Why it happens:** Phase was scaffolded but implementation was deferred.
**How to avoid:** Complete auth-service implementation BEFORE backend rewrite. Verify with integration tests.
**Warning signs:** Backend tests pass with mocks but fail in integration.

### Pitfall 4: Invitation InvitationRequest Missing Role Field
**What goes wrong:** The `InvitationRequest` DTO in auth-service has `email`, `companyId`, `inviterId` — but NO `role` field. Backend needs to specify role for the invited member.
**Why it happens:** Auth-service invitation was built for basic invite flow; role assignment was handled separately in Zitadel.
**How to avoid:** Add `role` field to `InvitationRequest` or handle role assignment separately after invitation acceptance.
**Warning signs:** Invited members have no role; must be manually promoted.

### Pitfall 5: Spring OAuth2 Client Conflicts with Resource Server
**What goes wrong:** Backend is BOTH an OAuth2 resource server (validates user JWTs) AND an OAuth2 client (obtains M2M tokens). Misconfiguration causes one to override the other.
**Why it happens:** Both `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-oauth2-client` are on classpath.
**How to avoid:** Use `AuthorizedClientServiceOAuth2AuthorizedClientManager` (not the reactive variant). Register client under a unique registration ID. Keep resource server config separate.
**Warning signs:** User authentication breaks after adding OAuth2 client config.

## Code Examples

### AuthServiceManagementClient (Core Pattern)

```java
// New client replacing ZitadelManagementClient
@Component
@RequiredArgsConstructor
public class AuthServiceManagementClient {

    private final RestClient authServiceRestClient; // Injected with M2M token interceptor

    public InvitationResponse createInvitation(UUID companyId, String email, UUID inviterId, Role role) {
        InvitationRequest request = new InvitationRequest(email, companyId, inviterId /*, role */);
        return authServiceRestClient.post()
            .uri("/api/v1/invitations")
            .body(request)
            .retrieve()
            .body(InvitationResponse.class);
    }

    public void cancelInvitation(UUID token) {
        authServiceRestClient.delete()
            .uri("/api/v1/invitations/{token}", token)
            .retrieve()
            .toBodilessEntity();
    }

    public void deleteMembership(UUID userId, UUID companyId) {
        authServiceRestClient.delete()
            .uri("/api/v1/users/{userId}/memberships/{companyId}", userId, companyId)
            .retrieve()
            .toBodilessEntity();
    }

    public void updateMembershipRole(UUID userId, UUID companyId, Role newRole) {
        authServiceRestClient.patch()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/users/{userId}/memberships/{companyId}")
                .queryParam("newRole", newRole.name())
                .build(userId, companyId))
            .retrieve()
            .toBodilessEntity();
    }

    public List<MemberListItemResponse> listOrganizationMembers(UUID orgId) {
        return authServiceRestClient.get()
            .uri("/api/v1/organizations/{orgId}/members", orgId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<MemberListItemResponse>>() {});
    }

    public boolean isReachable() {
        try {
            authServiceRestClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### Liquibase PK Migration Strategy

```xml
<!-- 013-unify-entity-pks.xml -->
<changeSet id="013-1" author="phase5">
    <comment>Unify OrganizationEntity PK with auth-service UUID</comment>
    
    <!-- Step 1: Update all FK references to use authCompanyId value -->
    <sql>
        UPDATE memberships SET organization_id = (
            SELECT auth_company_id::uuid FROM organizations WHERE organizations.id = memberships.organization_id
        )
    </sql>
    
    <!-- Step 2: Update PK -->
    <sql>UPDATE organizations SET id = auth_company_id::uuid</sql>
    
    <!-- Step 3: Drop mapping column -->
    <dropColumn tableName="organizations" columnName="auth_company_id"/>
</changeSet>

<changeSet id="013-2" author="phase5">
    <comment>Unify UserEntity PK with auth-service UUID</comment>
    
    <!-- Step 1: Update all FK references -->
    <sql>
        UPDATE memberships SET user_id = (
            SELECT auth_user_id::uuid FROM users WHERE users.id = memberships.user_id
        )
    </sql>
    
    <!-- Step 2: Update PK -->
    <sql>UPDATE users SET id = auth_user_id::uuid</sql>
    
    <!-- Step 3: Drop mapping column -->
    <dropColumn tableName="users" columnName="auth_user_id"/>
</changeSet>

<changeSet id="013-3" author="phase5">
    <comment>Add invitation_id column to memberships, add status column</comment>
    <addColumn tableName="memberships">
        <column name="invitation_id" type="UUID"/>
        <column name="status" type="VARCHAR(20)" defaultValue="ACTIVE">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>
```

### Rewritten StartupValidator

```java
@Component
@Slf4j
public class StartupValidator {

    private final AuthServiceManagementClient authServiceClient;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public StartupValidator(AuthServiceManagementClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateAuthServiceConnection() {
        if ("test".equals(activeProfile)) {
            log.debug("Skipping auth-service validation in test profile");
            return;
        }

        log.info("Checking auth-service reachability...");
        if (!authServiceClient.isReachable()) {
            log.warn("AUTH_SERVICE_UNREACHABLE: Auth-service management API is not reachable at startup");
        } else {
            log.info("Auth-service management API is reachable.");
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Zitadel SDK direct calls | Auth-service REST API via M2M OAuth2 | Phase 5 | All member operations route through auth-service |
| String zitadelUserId | UUID userId (auth-service PKs) | Phase 5 | Type safety, consistent identity |
| Separate local PKs + mapping columns | Auth-service UUIDs as PKs | Phase 5 | Simplified data model, no ID translation |
| StartupValidator checks Zitadel org/admin | Health check against auth-service | Phase 5 | Simpler, auth-service-centric |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `spring-boot-starter-oauth2-client` is not already in backend pom.xml | Standard Stack | Minor — might already be present, just verify |
| A2 | `auth_company_id` is castable to UUID for PK migration | Code Examples / Liquibase | HIGH — if authCompanyId is not a valid UUID string, migration fails. Need to verify data format |
| A3 | Spring OAuth2 `AuthorizedClientServiceOAuth2AuthorizedClientManager` handles proactive refresh automatically | Pattern 1 | MEDIUM — may need custom clock skew config for 5-min-early refresh |
| A4 | Auth-service `/actuator/health` endpoint is available without authentication | Code Examples | LOW — might need to permit in security config |

## Open Questions (RESOLVED)

1. **Auth-service `MembershipManagementController` TODO implementation** (RESOLVED → Plan 05-01, Task 2)
   - What we know: Delete and update endpoints exist but have `// TODO: Implement actual` comments
   - Resolution: Phase 5 Plan 01 Task 2 completes these implementations

2. **InvitationRequest role field** (RESOLVED → Plan 05-01, Task 2)
   - What we know: Current `InvitationRequest` has email, companyId, inviterId — no role
   - Resolution: Plan 01 Task 2 adds `role` field to `InvitationRequest` and `Invitation` entity

3. **OrganizationManagementService and SuperAdminService Zitadel usage** (RESOLVED → Plan 05-04, Task 1)
   - What we know: Both services use `ZitadelManagementClient` for org creation and super-admin invite
   - Resolution: Plan 04 Task 1 rewrites both to use `AuthServiceManagementClient`

4. **auth_company_id data type for PK migration** (RESOLVED → Plan 05-05, Task 1)
   - What we know: `OrganizationEntity.authCompanyId` is `String` (VARCHAR(64)). Need to verify all existing values are valid UUIDs.
   - Resolution: Plan 05 Task 1 includes preCondition check in Liquibase changeset to validate before migration

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + MockMvc |
| Config file | backend/pom.xml (surefire plugin) |
| Quick run command | `cd backend && ./mvnw test -pl . -Dtest=MemberManagement* -DfailIfNoTests=false` |
| Full suite command | `cd backend && ./mvnw test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MEM-01 | Invite member via auth-service | integration | `./mvnw test -Dtest=MemberInviteServiceTest` | ❌ Wave 0 |
| MEM-04 | Change member role | integration | `./mvnw test -Dtest=MemberManagementServiceTest#changeRole*` | ❌ Wave 0 |
| MEM-05 | Remove member | integration | `./mvnw test -Dtest=MemberManagementServiceTest#removeMember*` | ❌ Wave 0 |
| MEM-07 | Last admin guard | integration | `./mvnw test -Dtest=MemberManagementServiceTest#lastAdmin*` | ❌ Wave 0 |
| MEM-08 | Member list with status | integration | `./mvnw test -Dtest=MemberManagementServiceTest#listMembers*` | ❌ Wave 0 |
| AUTHZ-04 | COMPANY_ADMIN can manage | MockMvc | `./mvnw test -Dtest=MemberManagementControllerTest` | ❌ Wave 0 |
| AUTHZ-05 | Non-admin cannot manage | MockMvc | `./mvnw test -Dtest=MemberManagementControllerTest#unauthorized*` | ❌ Wave 0 |
| INFRA-01 | Zitadel SDK removed | compilation | `./mvnw compile` | ✅ (implicit) |
| TEST-06 | RBAC authorization tests | MockMvc | `./mvnw test -Dtest=*AuthorizationTest` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./mvnw test -DfailIfNoTests=false`
- **Per wave merge:** `cd backend && ./mvnw test && cd ../auth-service && ./mvnw test`
- **Phase gate:** Full suite green + `./mvnw compile` with Zitadel removed

### Wave 0 Gaps
- [ ] `backend/src/test/java/de/goaldone/backend/service/MemberManagementServiceTest.java` — covers MEM-04, MEM-05, MEM-07, MEM-08
- [ ] `backend/src/test/java/de/goaldone/backend/service/MemberInviteServiceTest.java` — covers MEM-01, MEM-02
- [ ] `backend/src/test/java/de/goaldone/backend/client/AuthServiceManagementClientTest.java` — covers M2M integration
- [ ] `auth-service/src/test/java/.../controller/OrganizationManagementControllerTest.java` — covers GET members endpoint

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | OAuth2 client_credentials for M2M; existing JWT for users |
| V3 Session Management | no | Stateless JWT (no sessions) |
| V4 Access Control | yes | @PreAuthorize + scope guard + last-admin constraint |
| V5 Input Validation | yes | Jakarta Bean Validation on DTOs; UUID type enforcement |
| V6 Cryptography | no | No new crypto (existing RSA keys from Phase 1) |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| End-user token calls management API | Elevation of Privilege | Scope guard (`SCOPE_mgmt:admin`) on /api/v1/** |
| M2M client secret exposure | Information Disclosure | Environment variable injection; never in code/git |
| Race condition on last-admin check | Tampering | Auth-service enforces atomically (single source of truth) |
| Invitation token guessing | Spoofing | UUID tokens (128-bit entropy); expiry after 7 days |

## Sources

### Primary (HIGH confidence)
- Codebase inspection: All source files listed in canonical references examined directly
- Auth-service controllers/services: Verified endpoint signatures and existing implementations
- Backend entities: Verified current schema (authCompanyId is String VARCHAR(64))
- Liquibase changesets: Verified migration history (12 existing changesets)
- ClientSeedingRunner: Verified M2M client already seeded with `mgmt:admin` scope
- DefaultSecurityConfig: Verified current auth setup (JWT required but no scope check)

### Secondary (MEDIUM confidence)
- Spring OAuth2 Client Credentials pattern: Based on Spring Security documentation conventions [ASSUMED — standard Spring pattern]

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - verified from codebase, no new libraries needed
- Architecture: HIGH - all integration points inspected, gaps identified (TODOs in auth-service)
- Pitfalls: HIGH - concrete issues found (missing role field, TODO implementations, FK cascade risk)
- Security: HIGH - scope guard pattern clear, threat model straightforward

**Research date:** 2026-05-03
**Valid until:** 2026-06-03 (stable — internal project, no external API changes expected)
