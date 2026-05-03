# Architecture Research

**Domain:** Multi-tenant SaaS — custom OAuth2 Authorization Server replacing Zitadel
**Researched:** 2026-05-02
**Confidence:** HIGH (based on direct code inspection of both systems)

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Browser (Angular)                     │
│  angular-oauth2-oidc  →  PKCE Authorization Code Flow       │
│  authInterceptor      →  adds Bearer token to all requests  │
└──────────────┬──────────────────────────┬───────────────────┘
               │  /authorize  (login)     │  /api/v1/** (business)
               ▼                          ▼
┌──────────────────────┐      ┌────────────────────────────────┐
│     auth-service     │      │         backend API             │
│  Spring Auth Server  │      │  Spring Boot resource server   │
│  port 9000           │◄─────│  port 8080                     │
│                      │      │  (JWKS fetch at startup/cache) │
│  /oauth2/token       │      │                                │
│  /oauth2/jwks        │      │  JwtAuthenticationConverter    │
│  /.well-known/oidc.. │      │  JitProvisioningFilter         │
│  /api/v1/mgmt/**     │      │  @PreAuthorize method security │
└──────────────────────┘      └────────────────────────────────┘
         │                               │
         ▼                               ▼
┌──────────────────────┐      ┌────────────────────────────────┐
│  auth-service DB     │      │       backend DB               │
│  (PostgreSQL)        │      │  (PostgreSQL / H2 local)       │
│  users, companies,   │      │  user_accounts, organizations, │
│  memberships,        │      │  tasks, appointments,          │
│  invitations,        │      │  working_times, etc.           │
│  verification_tokens │      │                                │
└──────────────────────┘      └────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Key Facts |
|-----------|----------------|-----------|
| auth-service | OAuth2 Authorization Server (OIDC), user identity, org/company management, invitation flow, password reset | Spring Authorization Server on port 9000; issues JWTs; holds User, Company, Membership domain |
| backend API | Business logic resource server; validates JWTs; enforces per-org data isolation | Stateless; uses JWKS from auth-service to verify tokens; JIT provisions local user records |
| frontend | OIDC client; PKCE code flow; attaches Bearer token to every API request | `angular-oauth2-oidc`; reads `window.__env.issuerUri` at runtime |
| auth-service DB | Single source of truth for identity | Users are uniquely identified by their UUID here (no Zitadel sub after migration) |
| backend DB | Application data scoped to organizations | `user_accounts` references auth-service user UUID as `auth_user_id` (renamed from `zitadel_sub`) |

## Component Boundaries — What Each System Owns

### auth-service owns:
- User creation, password hashing, account status
- Email verification and password reset flows
- Company (org) creation and slug management
- Membership CRUD (user ↔ company ↔ role)
- Invitation lifecycle (create, accept, expire)
- Issuing access tokens and refresh tokens
- The JWKS endpoint (`/oauth2/jwks`) for public key distribution
- OIDC Discovery Document (`/.well-known/openid-configuration`)
- Management API (`/api/v1/**`) protected by client-credentials token for backend-to-auth-service calls

### backend API owns:
- All business data (tasks, appointments, working times, etc.)
- Local "shadow" records of users and orgs (fast lookups without auth-service round-trips)
- Per-org data isolation enforcement at the repository query level
- Role-based access control via Spring `@PreAuthorize`

### frontend owns:
- PKCE flow orchestration (no secrets in browser)
- Token storage and automatic refresh (delegated to `angular-oauth2-oidc`)
- Runtime OIDC configuration via `window.__env` (injected before Angular boots)

## Token Validation Flow (Request → Authorization)

```
1. Frontend → backend:
   GET /api/v1/tasks  +  Authorization: Bearer <access_token>

2. backend BearerTokenAuthenticationFilter:
   - Extracts token from header

3. backend JwtDecoder (Spring auto-configured):
   - On first request: fetches JWKS from auth-service /oauth2/jwks
   - Caches public keys (TTL driven by Spring Security's NimbusJwtDecoder cache)
   - Verifies token signature, exp, iss
   - Rejects invalid tokens → 401

4. backend JwtAuthenticationConverter (custom):
   CURRENT (Zitadel): reads "urn:zitadel:iam:org:project:roles" nested map → ROLE_*
   TARGET (auth-service): reads "authorities" claim (flat Set<String>) → ROLE_*, ORG_<id>_<role>

5. backend JitProvisioningFilter (after BearerTokenAuthenticationFilter):
   - Reads jwt.getSubject() → auth-service user UUID
   - Finds or creates UserAccountEntity keyed by auth_user_id (was: zitadel_sub)
   - Reads jwt claim "org_id" (to be defined) → finds or creates OrganizationEntity
   - Updates last_seen_at on repeat visits

6. backend Controller → Service → Repository:
   - @PreAuthorize("hasRole('USER')") guards endpoints
   - CurrentUserResolver.resolve(jwt) → loads UserAccountEntity by auth_user_id
   - All queries scoped to organization_id from UserAccountEntity
```

## Role/Permission Extraction — Current vs Target

### Current JWT (Zitadel)
```json
{
  "sub": "zitadel-user-id",
  "urn:zitadel:iam:org:project:roles": {
    "ADMIN": { "orgId123": "domain.com" }
  },
  "urn:zitadel:iam:user:resourceowner:id": "zitadel-org-id",
  "urn:zitadel:iam:user:resourceowner:name": "My Company"
}
```

JwtAuthenticationConverter extracts: `ROLE_ADMIN`

### Target JWT (auth-service)
Already emitted by `TokenCustomizerConfig.java`:
```json
{
  "sub": "auth-service-user-uuid",
  "authorities": ["ROLE_USER", "ORG_<companyId>_ADMIN"],
  "user_id": "auth-service-user-uuid",
  "primary_email": "user@example.com",
  "emails": ["user@example.com"]
}
```

**Gap:** The JWT currently does NOT include a claim that tells the backend which organization the user is "currently acting in." The auth-service's `CustomUserDetails.calculateAuthorities()` encodes org membership as `ORG_<companyId>_<role>`, which means the backend can decode all memberships. However, the JIT provisioning flow (single `UserAccountEntity` per JWT) needs a strategy.

**Decision required for JitProvisioningService redesign:** With Zitadel, one JWT = one org (org scoped token). With auth-service, one JWT = all org memberships. The backend's single-org-per-user-account model must be reconsidered. Two options:

1. **Include active org claim** — add `active_org_id` claim to JWT (selected at login or passed as scope param). JIT provisioning stays simple: one active org per session.
2. **Multi-account provisioning** — on JIT, create a `UserAccountEntity` for each org in memberships. Requires looping; more complex but consistent with auth-service's multi-org model.

Option 1 is lower-risk for the migration scope. Option 2 aligns with the simplified model long-term.

## Multi-Org Data Isolation

### Current model (Zitadel-driven)
```
UserIdentityEntity (1)  ←→  (N) UserAccountEntity
UserAccountEntity.organization_id → OrganizationEntity
OrganizationEntity.zitadel_org_id → "Zitadel org ID"
UserAccountEntity.zitadel_sub → "Zitadel user sub"
```

One `UserAccountEntity` per Zitadel org-scoped token. Isolation enforced because token is org-scoped.

### Target model (auth-service)
```
auth-service: User (1) ←→ (N) Membership ←→ (N) Company

backend:
UserIdentityEntity (1)  ←→  (N) UserAccountEntity
UserAccountEntity.auth_user_id → auth-service User.id (UUID)
UserAccountEntity.organization_id → OrganizationEntity
OrganizationEntity.auth_company_id → auth-service Company.id (UUID)
```

Isolation must be enforced explicitly. All repository queries should include `organization_id` in WHERE clause. Spring `@PreAuthorize` enforces role membership in the target org before business operations.

## Build Order and Phase Implications

### Phase 1: Auth-service token contract (MUST come first)
**Why first:** Everything else depends on the exact JWT claim structure. The backend and frontend cannot be updated until the token format is stable.

Deliverables:
- Finalize JWT claim names: `auth_user_id` (or use `user_id` already present), `active_org_id` or similar, `authorities` format
- Add `active_org_id` to `TokenCustomizerConfig` (requires `RegisteredClient` scope or session parameter)
- Add `issuer-uri` OIDC discovery to confirm `/.well-known/openid-configuration` is correct
- Switch `RegisteredClientRepository` from in-memory to database-backed (`JdbcRegisteredClientRepository`)
- Persist RSA key pair (currently generated in-memory on every startup — breaks token validation after restart)

### Phase 2: Backend JWT validation migration
**Why second:** Depends on stable token format from Phase 1.

Deliverables:
- Replace `JwtAuthenticationConverter`: read `authorities` claim (flat list) instead of Zitadel nested map
- Update `application.yaml`: change `issuer-uri` from Zitadel to auth-service URL
- Remove `ZitadelConfig.java` (Zitadel SDK bean)
- Update `JitProvisioningService`: use `user_id` claim instead of `sub`; use `active_org_id` claim instead of Zitadel resource-owner claims
- Update `CurrentUserResolver`: use `auth_user_id` instead of `zitadel_sub`

### Phase 3: Database migration (Liquibase changelogs)
**Why third:** Can be done in parallel with Phase 2, but needs Phase 1 token contract to know the new column names.

Deliverables:
- Changelog: rename `user_accounts.zitadel_sub` → `auth_user_id` (type remains VARCHAR/UUID)
- Changelog: rename `organizations.zitadel_org_id` → `auth_company_id`
- Drop unused `UserIdentityEntity` if moving to single-account model (or keep for profile abstraction)
- Update all `findByZitadelSub` → `findByAuthUserId` repository methods
- Update all `findByZitadelOrgId` → `findByAuthCompanyId` repository methods

### Phase 4: Frontend OIDC config update
**Why fourth:** Low risk, mostly config change. Depends on auth-service OIDC discovery being correct.

Deliverables:
- `env.js`: change `issuerUri` from Zitadel URL to auth-service URL (`http://auth-service:9000` or public URL)
- `auth.service.ts` scope: remove `urn:zitadel:iam:user:resourceowner` scope; use `openid profile email offline_access`
- `auth.service.ts` `getUserRoles()`: update claim key from Zitadel claims to `authorities`
- `auth.service.ts` `getUserOrganizationId()`: update claim key to `active_org_id`
- Update redirect URIs: register frontend client in auth-service DB (was in-memory test-client)
- Remove Zitadel client ID from env vars

### Phase 5: Backend member management via auth-service API
**Why last:** Most complex; depends on auth-service management API being stable.

Deliverables:
- Replace `ZitadelManagementClient` with `AuthServiceManagementClient` (REST client to auth-service `/api/v1/mgmt/**`)
- `MemberInviteService`: call auth-service invitation API instead of Zitadel Management API
- `MemberManagementService`: call auth-service membership API for role changes and removal
- Backend uses `mgmt-client` (client-credentials flow) to authenticate to auth-service management endpoints

## Database Migration Strategy

### Backend DB changes (Liquibase, additive first)

Step 1 — Add new columns (nullable, coexisting with old):
```xml
<addColumn tableName="user_accounts">
    <column name="auth_user_id" type="VARCHAR(64)"/>
</addColumn>
<addColumn tableName="organizations">
    <column name="auth_company_id" type="VARCHAR(64)"/>
</addColumn>
```

Step 2 — Data migration (if existing data needs mapping):
```xml
<!-- If doing a clean cutover with no live Zitadel users to migrate, skip this -->
<sql>UPDATE user_accounts SET auth_user_id = zitadel_sub WHERE auth_user_id IS NULL</sql>
```

Step 3 — Add NOT NULL constraint, unique index, drop old columns:
```xml
<addNotNullConstraint tableName="user_accounts" columnName="auth_user_id"/>
<addUniqueConstraint tableName="user_accounts" columnNames="auth_user_id"/>
<dropColumn tableName="user_accounts" columnName="zitadel_sub"/>
<dropColumn tableName="organizations" columnName="zitadel_org_id"/>
```

**Important:** Because this is a complete replacement (not gradual), and there are no live Zitadel users to carry over (PROJECT.md: "None validated"), the migration can be done as a breaking changeset if done at cutover time. No dual-write period is needed.

### Auth-service DB (already exists)
No changes required for token contract updates. The `auth-service` DB already has:
- `users`, `companies`, `memberships` (core identity model)
- `invitations`, `verification_tokens` (invitation flow)
- Liquibase managed at its own schema

**Critical gap — RSA key persistence:** `AuthorizationServerConfig.generateRsaKey()` generates a fresh RSA key pair on every JVM restart. After restart, all previously issued JWTs become invalid (JWKS key ID changes). This MUST be fixed in Phase 1 before any production use. Fix: externalize key pair via environment variable or database-backed `KeyStore`.

## Architectural Patterns

### Pattern 1: JWKS-based stateless token validation

**What:** Backend fetches public keys from `auth-service/oauth2/jwks` and caches them. Tokens are verified locally on every request without calling auth-service.
**When to use:** Always — avoids auth-service becoming a synchronous dependency for every API request.
**Trade-offs:** Cache invalidation on key rotation requires restart or explicit cache eviction. Spring's `NimbusJwtDecoder` handles this automatically by refetching on unknown key IDs.

### Pattern 2: JIT provisioning (retain, update claim sources)

**What:** On first request, auto-create backend user/org shadow records from JWT claims. Subsequent requests update `last_seen_at` only.
**When to use:** Keeps backend DB in sync without a webhook or event bus.
**Trade-offs:** First request is slightly slower; race conditions on concurrent first requests (mitigated by `DataIntegrityViolationException` catch-and-refetch pattern already in codebase).

### Pattern 3: Per-org data isolation via FK, not claim re-validation

**What:** Once `UserAccountEntity.organization_id` is set (via JIT), all subsequent queries use it as a WHERE clause filter. The org membership is trusted after JIT runs.
**When to use:** For performance — no need to re-validate JWT memberships on every DB query.
**Trade-offs:** If membership is revoked in auth-service, the backend will still allow access until the user's JWT expires (short token lifetime recommended: 15 min access token with refresh token).

### Pattern 4: Client credentials for backend→auth-service management calls

**What:** `backend` uses an OAuth2 client-credentials grant with `mgmt-client` to call auth-service management APIs. No user context is passed.
**When to use:** Any backend→auth-service call (invite member, change role, deactivate user).
**Trade-offs:** auth-service management endpoints must be secured with scope checks (`mgmt:admin` scope already defined in `AuthorizationServerConfig`).

## Anti-Patterns

### Anti-Pattern 1: In-memory RSA keys in auth-service

**What people do:** Leave `generateRsaKey()` generating a fresh in-memory key at startup (current state).
**Why it's wrong:** Every restart invalidates all live tokens. Breaks all authenticated sessions. Makes rolling deployments impossible.
**Do this instead:** Load RSA key from a persisted source: environment variable (PEM-encoded), database table, or external secrets manager. Store a fixed `keyID` so the `kid` header in JWTs remains stable across restarts.

### Anti-Pattern 2: In-memory RegisteredClientRepository

**What people do:** Define clients in code (`InMemoryRegisteredClientRepository`) as is currently done.
**Why it's wrong:** Client secrets are in source code; cannot rotate without redeploy; no persistence of authorization codes or consent records.
**Do this instead:** Use `JdbcRegisteredClientRepository` with `oauth2_registered_client` schema managed by Liquibase. Keep secrets in environment variables, not source code.

### Anti-Pattern 3: Reading Zitadel-specific claims in the frontend

**What people do:** `getUserRoles()` searches for `urn:zitadel:iam:org:project:roles` in the token.
**Why it's wrong:** The auth-service issues `authorities` as a flat array — the existing code already handles arrays, but the claim key search loop will miss it unless `authorities` is added to the search list.
**Do this instead:** Read the `authorities` claim directly; remove the Zitadel-specific key lookup. Make `getUserOrganizationId()` read the new `active_org_id` claim key.

### Anti-Pattern 4: Scope creep — doing member management in the backend via Zitadel SDK

**What people do:** Backend calls Zitadel Management API (Java SDK) to create users, assign roles, send invitations (current `MemberInviteService`).
**Why it's wrong:** After migration, there is no Zitadel. Backend should never own identity operations.
**Do this instead:** Backend delegates all identity mutations to auth-service via its management REST API. Backend is purely a consumer/validator of identity data.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| auth-service JWKS | HTTP GET at startup + cache (Spring auto) | URL: `{issuer-uri}/oauth2/jwks`; no auth required |
| auth-service OIDC discovery | HTTP GET at startup (Spring auto) | URL: `{issuer-uri}/.well-known/openid-configuration`; must be accessible from backend at boot |
| auth-service management API | REST + client-credentials Bearer token | `mgmt-client` credentials stored in backend env vars; scope: `mgmt:admin` |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| frontend ↔ auth-service | PKCE OAuth2 browser redirect + token fetch | auth-service must be publicly accessible (browser makes direct calls) |
| frontend ↔ backend API | REST + Bearer token in Authorization header | `authInterceptor.ts` already handles this; no change needed |
| backend ↔ auth-service (read) | None — JWT is self-contained | Token validated via JWKS; no auth-service calls per-request |
| backend ↔ auth-service (write) | REST (client-credentials) | Member management, invitation operations only |

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-1k users | Current monolith structure is fine; auth-service and backend as separate services on same host or VM |
| 1k-100k users | Add JWKS key caching TTL tuning; ensure auth-service DB connection pool is sized for concurrent login load; consider read replica for backend DB |
| 100k+ users | auth-service becomes stateful bottleneck at login (session store is JDBC); migrate session store to Redis; consider separate auth-service instances behind load balancer with shared DB |

### Scaling Priorities

1. **First bottleneck:** auth-service JDBC session store at high concurrent login rate. Fix: switch to Redis session store (`spring.session.store-type: redis`).
2. **Second bottleneck:** backend JIT provisioning creates write contention on `user_accounts` for new users. Fix: existing `DataIntegrityViolationException` catch handles concurrent creation; read replica for non-provisioning queries.

## Sources

- Direct code inspection: `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java`
- Direct code inspection: `auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java`
- Direct code inspection: `auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java`
- Direct code inspection: `auth-service/src/main/java/de/goaldone/authservice/config/DefaultSecurityConfig.java`
- Direct code inspection: `backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java`
- Direct code inspection: `frontend/src/app/core/auth/auth.service.ts`
- Direct code inspection: `backend/src/main/resources/application.yaml`
- Direct code inspection: `auth-service/src/main/resources/application.yml`
- Spring Authorization Server official docs: https://docs.spring.io/spring-authorization-server/reference/
- Confidence: HIGH — all findings from direct code inspection of production code

---
*Architecture research for: GoalDone — Zitadel → custom auth-service migration*
*Researched: 2026-05-02*
