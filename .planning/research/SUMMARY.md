# Project Research Summary

**Project:** GoalDone — Zitadel to Custom Auth-Service Migration
**Domain:** Multi-tenant SaaS IdP replacement (Spring Authorization Server)
**Researched:** 2026-05-02
**Confidence:** HIGH

## Executive Summary

This migration replaces Zitadel as the identity provider with a custom Spring Authorization Server (`auth-service`) that the team already built. The auth-service is substantially complete: it issues JWTs with the correct claim structure (`authorities`, `user_id`, `primary_email`), handles invitations, password reset, and org/membership management. The problem is that the backend and frontend are still hardwired to Zitadel's claim vocabulary — every piece of auth-aware code reads Zitadel-specific claim names that will not exist in auth-service tokens. The migration is therefore a well-scoped code-replacement task, not a greenfield build.

The recommended approach is a phased, dependency-ordered cutover: first stabilize the auth-service token contract and persistence (RSA key, RegisteredClient DB), then update the backend JWT validation layer (converter, JIT provisioner, DB columns), then rewrite the Zitadel-coupled member management services, and finally flip the frontend config. Each phase is a prerequisite for the next. No dual-IdP period is needed or desirable — this is a complete cutover. The critical architectural decision that must be made before writing any code is how multi-org context is conveyed per token: either add an `active_org_id` claim (simpler) or redesign the backend to handle all-org-memberships from the JWT (more correct long term).

The top risks are silent failures: the JWT claim shape mismatch causes every role check to return empty with no exception logged; the `zitadel_sub` lookup failure causes 500s with no auth-time error; the in-memory RSA key means every auth-service restart globally logs out all users. All three are already in the codebase and will activate the moment traffic is switched. They are individually easy to fix but collectively blocking — none of the P1 features work until all three are addressed.

## Key Findings

### Recommended Stack

No new dependencies are required for the backend. The migration is purely configuration and code changes. The auth-service already has `spring-security-oauth2-authorization-server` (Spring Security 7 / Boot 4.0.6). The backend should align its Boot version from 4.0.5 to 4.0.6. The Zitadel Java SDK (`io.github.zitadel:client`) must be removed from the backend `pom.xml` — it becomes a dead dependency and a security surface once Zitadel is decommissioned.

**Core technologies:**
- Spring Boot 4.0.6: Runtime for both backend and auth-service — align both modules to this version
- Spring Security 7.0.x OAuth2 Authorization Server: Auth-service OAuth2/OIDC server — already declared in auth-service pom.xml
- Spring Security OAuth2 Resource Server (managed by Boot): Backend JWT validation — no version change needed, only config change
- `JwtGrantedAuthoritiesConverter`: Backend role extraction from flat `authorities` claim — replaces the current Zitadel-specific lambda converter
- WireMock 3.12.0: JWKS stub for integration tests — already present; stubs must be updated to auth-service claim shapes

**Key config changes (not new code):**
- `spring.security.oauth2.resourceserver.jwt.issuer-uri`: Change from Zitadel domain to auth-service URL (e.g. `http://localhost:9000`)
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`: Optionally set explicitly to `http://localhost:9000/oauth2/jwks` for startup independence
- RSA key: Must be externalized from `generateRsaKey()` to PEM files or env vars before any shared deployment

### Expected Features

**Must have for migration cutover (P1 — v1):**
- Backend JWT claim extractor updated — reads `authorities` flat list instead of Zitadel nested map; blocks all auth
- Backend JIT provisioning rewritten — reads `user_id` / `primary_email` from auth-service JWT; drops `zitadelSub` / `zitadelOrgId`
- Frontend `window.__env` updated — points to auth-service OIDC discovery; client ID changed from Zitadel numeric ID to string client ID
- RSA key externalized in auth-service — tokens survive restarts
- RegisteredClient for Angular frontend — with correct redirect URIs, `openid profile email offline_access` scopes, and `REFRESH_TOKEN` grant type
- Membership deletion wired — `MembershipManagementController` TODO completed in `UserManagementService`
- Membership role change wired — same TODO
- List org members endpoint — new endpoint, currently missing entirely
- `MemberInviteService` rewritten — stops calling `ZitadelManagementClient`, calls auth-service invitation API
- `MemberManagementService` rewritten — stops calling Zitadel for member list, role change, removal

**Should have post-cutover (P2 — v1.x):**
- Invitation with role assignment — extend `InvitationRequest` with `role` field; currently hardcoded to `Role.USER`
- Account linking PKCE flow fully wired — `AccountLinkingAuthenticationProvider.extractLinkingContext` is currently a null stub
- RegisteredClient persistence — move from `InMemoryRegisteredClientRepository` to `JdbcRegisteredClientRepository`
- Audit service wiring — `AuditService` exists as stub; call from critical flows

**Defer to v2+:**
- OAuth2 social login (Google, GitHub)
- Two-factor authentication
- Admin RBAC for management API (scope-based machine client access)
- Persistent sessions / remember me

**Already built and working (do not rebuild):**
- Login with email/password, logout, password reset
- JWT issuance with `authorities`, `user_id`, `primary_email` claims (via `TokenCustomizerConfig`)
- JWKS endpoint at `/oauth2/jwks`
- Full invitation flow (create, accept, activate user, account linking logic)
- Last-admin guard for membership mutations
- Invitation expiration cleanup job

### Architecture Approach

The system is a three-component OIDC architecture: the Angular frontend performs PKCE code flow against auth-service (port 9000), receives a JWT, and passes it as a Bearer token to the backend API (port 8080). The backend validates JWTs locally via cached JWKS (no per-request auth-service call), then JIT-provisions local shadow records for users and orgs. The backend calls auth-service management APIs for identity mutations (member invite, role change, removal) using a client-credentials flow. Auth-service owns identity; backend owns business data. The critical unresolved architectural question is how to represent "which org is this user currently acting in" in a token that now carries multi-org memberships.

**Major components:**
1. **auth-service** (port 9000): OAuth2/OIDC Authorization Server; owns User, Company, Membership, Invitation domain; issues JWTs; exposes JWKS and management API
2. **backend API** (port 8080): Stateless JWT resource server; JIT-provisions local user/org shadow records from JWT claims; enforces per-org data isolation; delegates identity mutations to auth-service
3. **frontend** (Angular): PKCE code flow via `angular-oauth2-oidc`; reads OIDC config from `window.__env` at runtime; attaches Bearer token via `authInterceptor`

**Key architectural decision required before Phase 1 completes:** Choose between (A) adding `active_org_id` claim to auth-service JWT (org selected at login, simpler for JIT provisioning) or (B) redesigning backend to provision `UserAccountEntity` for each org membership in the JWT. Option A is lower-risk for migration scope.

### Critical Pitfalls

1. **JWT claim shape mismatch — silent 403s everywhere** — Update `SecurityConfig.jwtAuthenticationConverter()` first, before any other backend work. The existing Zitadel-specific lambda returns empty on auth-service tokens with no log output. Test with a real auth-service-shaped JWT, not a mocked one.

2. **`zitadel_sub` column lookup breaks for all requests — 500s after login** — Rename column to `auth_user_id` via Liquibase changeset and update `UserAccountRepository`, `CurrentUserResolver`, and `JitProvisioningService` in lockstep. No existing user data needs migrating since no live Zitadel users exist (PROJECT.md: "None validated").

3. **In-memory RSA key pair — global logout on every auth-service restart** — Fix before any shared-environment deployment. Every restart generates a new key pair with a new random `kid`; all cached JWKS entries become invalid. Externalize to PEM files or a stable keystore.

4. **JIT provisioner reads Zitadel claims — silent provisioning failure** — `JitProvisioningService` reads `urn:zitadel:iam:user:resourceowner:id` which returns null from auth-service tokens, then attempts `createOrganization(null, null)`, hitting a NOT NULL constraint. The filter swallows `DataIntegrityViolationException` silently, leaving users with no local account and cascading 500s.

5. **`MemberManagementService` and `MemberInviteService` are compile-time coupled to Zitadel SDK** — Removing `io.github.zitadel:client` from `pom.xml` without rewriting both services first causes a compilation failure. Both use `ZitadelManagementClient` directly via `@RequiredArgsConstructor` with no interface layer. Rewrite before removing the dependency.

6. **`env.js` hardcodes Zitadel issuer and numeric client ID** — `issuerUri: 'https://sso.dev.goaldone.de'` and `clientId: '368981534071324679'` must both be updated before any OIDC flow testing. Wrong issuer causes CORS/404 on discovery; wrong client ID causes `invalid_client` at token exchange.

7. **Scope mismatch prevents refresh token issuance** — Frontend requests `offline_access` scope but auth-service `RegisteredClient` is missing `OidcScopes.EMAIL`, `offline_access` scope, and `AuthorizationGrantType.REFRESH_TOKEN` grant type. Without a refresh token, users are silently logged out when the access token expires.

8. **Multi-org authorization context** — `validateCallerBelongsToOrg()` relies on `UserAccountEntity.organizationId` (single FK). Auth-service tokens carry memberships for all orgs. Users with multiple org memberships cannot access all of them, and the isolation check may have an authorization bypass risk if not redesigned.

## Implications for Roadmap

Based on the dependency graph derived from all four research dimensions, the following phase structure is required. Each phase is a hard prerequisite for the next.

### Phase 1: Auth-Service Hardening (Token Contract Stabilization)

**Rationale:** Everything downstream depends on a stable, persistent token. The backend and frontend cannot be migrated until the token format does not change between restarts and the issuer is reachable. This phase has no upstream dependencies — it can start immediately.

**Delivers:**
- Externalized RSA key pair (PEM files or env var) with stable `kid`; tokens survive restarts
- `JdbcRegisteredClientRepository` replacing in-memory; Angular client registered with correct redirect URIs, `openid profile email offline_access` scopes, `REFRESH_TOKEN` grant type
- Decision and implementation of `active_org_id` claim in `TokenCustomizerConfig` (or documented multi-account alternative)
- OIDC discovery document verified at `/.well-known/openid-configuration`
- `StartupValidator` and `ZitadelConfig` deleted from backend (infrastructure cleanup can start here)

**Avoids:** Pitfall 3 (in-memory RSA), Pitfall 7 (StartupValidator calling dead Zitadel), Pitfall 8 (scope/grant type mismatch)

**Research flag:** Standard Spring Authorization Server patterns — well documented. No additional research needed. The `active_org_id` design decision is a product/architecture call, not a research gap.

### Phase 2: Backend JWT Validation Migration

**Rationale:** Once auth-service emits stable tokens, the backend resource server can be updated to validate them. This phase is almost entirely code-replacement, no new features.

**Delivers:**
- `SecurityConfig.jwtAuthenticationConverter()` rewritten to use `JwtGrantedAuthoritiesConverter` reading `authorities` flat claim with empty prefix
- `application.yaml` (`issuer-uri` updated to auth-service URL; `jwk-set-uri` set explicitly for local dev robustness)
- Zitadel SDK dependency removed from `pom.xml` (safe only after member services are also rewritten — coordinate with Phase 5)
- `JitProvisioningService` rewritten: reads `user_id` and `primary_email` from auth-service JWT; no Zitadel org claims
- `CurrentUserResolver` updated to use `auth_user_id` instead of `zitadel_sub`
- All integration tests updated to use auth-service JWT claim shapes and `.authorities(...)` explicitly

**Avoids:** Pitfall 1 (silent claim mismatch), Pitfall 5 (JIT provisioner reads Zitadel claims), Pitfall 6 (Zitadel SDK compile coupling)

**Research flag:** Standard patterns — `JwtGrantedAuthoritiesConverter` is well-documented. JIT provisioner rewrite logic depends on Phase 1's org-context decision.

### Phase 3: Database Migration (Liquibase)

**Rationale:** Can be worked on in parallel with Phase 2 but must be deployed before Phase 2 code goes live. DB column renames must precede code changes that use the new names in production. Because there are no live Zitadel users to migrate, this is a clean-break migration, not a dual-write.

**Delivers:**
- Liquibase changeset: rename `user_accounts.zitadel_sub` to `auth_user_id` (VARCHAR/UUID, NOT NULL, UNIQUE)
- Liquibase changeset: rename `organizations.zitadel_org_id` to `auth_company_id`
- Repository method renames: `findByZitadelSub` to `findByAuthUserId`, `findByZitadelOrgId` to `findByAuthCompanyId`
- Entity field renames: `UserAccountEntity`, `OrganizationEntity`
- Decision on `UserIdentityEntity`: keep for profile abstraction or drop for single-account model

**Avoids:** Pitfall 2 (`zitadel_sub` orphaned FK causing 500s on all business requests)

**Research flag:** Standard Liquibase patterns. No research needed. Additive-first strategy (add nullable column, migrate values, add NOT NULL, drop old) is safest even for a clean cutover.

### Phase 4: Frontend Auth Switch

**Rationale:** Frontend depends on auth-service having a stable token and registered client (Phases 1-2). This phase has the lowest code complexity — mostly config changes — but is a user-visible go-live gate.

**Delivers:**
- `env.js` (and environment variants): `issuerUri` updated to auth-service URL; `clientId` updated to registered auth-service client ID
- `auth.service.ts`: `getUserRoles()` reads `authorities` claim; `getUserOrganizationId()` reads `active_org_id`; Zitadel-specific scope removed from scope list
- E2E smoke test: OIDC discovery resolves, authorization code flow completes, refresh token works, role-gated UI elements render for authenticated users

**Avoids:** Pitfall 4 (frontend role extraction breaks), Pitfall 10 (env.js hardcodes Zitadel issuer/client ID), Pitfall 8 (scope mismatch, silent logout on access token expiry)

**Research flag:** `angular-oauth2-oidc` config changes are standard. No research needed. The `active_org_id` claim strategy from Phase 1 must be reflected here.

### Phase 5: Backend Member Management Rewrite

**Rationale:** This is the most complex phase and the last blocker before cutover. It depends on auth-service management API being stable (Phase 1) and the backend token layer being correct (Phase 2). The Zitadel SDK can only be fully removed once both this phase and Phase 2 are complete.

**Delivers:**
- `MemberManagementService` rewritten: replaces `ZitadelManagementClient` calls with `AuthServiceManagementClient` REST calls to auth-service `/api/v1/mgmt/**`
- `MemberInviteService` rewritten: calls auth-service invitation API; removes `ZitadelManagementClient` invitation methods
- `MembershipManagementController` TODOs completed: membership deletion and role change wired to service implementations
- New list-members endpoint (backed by auth-service or local membership mirror)
- `validateCallerBelongsToOrg()` redesigned for multi-org JWT model — no longer relies solely on `UserAccountEntity.organizationId` FK
- `MemberManagementControllerIT` test suite migrated from WireMock Zitadel stubs to auth-service stubs; all 14 cases pass
- Backend uses `mgmt-client` client-credentials OAuth2 flow to authenticate management API calls
- Zitadel SDK (`io.github.zitadel:client`) fully removed from `pom.xml`

**Avoids:** Pitfall 6 (Zitadel SDK compile coupling), Pitfall 9 (multi-org authorization context loss)

**Research flag:** Auth-service management API contract must be fully enumerated before this phase begins. Verify that auth-service controllers cover all required operations: list members by org, change member role, remove member, send invitation. If any are missing, auth-service work must be added to Phase 5 scope. The `mgmt-client` client-credentials setup and `mgmt:admin` scope enforcement on auth-service endpoints needs verification.

### Phase Ordering Rationale

- Phase 1 must come first because RSA key stability and the `active_org_id` claim decision are inputs to every subsequent phase.
- Phase 3 (DB) can be parallelized with Phase 2 but the Liquibase changeset must be deployed before Phase 2 code goes live.
- Phase 4 (frontend) can only be tested once Phases 1-2 are complete; the code changes themselves can be written any time.
- Phase 5 (member management) is last because it requires auth-service management API stability, the correct backend token layer, and the multi-org authorization redesign — and it is the largest rewrite.
- No parallel running of Zitadel and auth-service is planned; cutover is atomic at Phase 5 completion.

### Research Flags

Phases needing deeper investigation during planning or execution:

- **Phase 1 (design decision):** The `active_org_id` claim strategy is unresolved. This is an architectural decision that must be made explicit in Phase 1 planning before any JIT provisioner code is written. Options: (A) select active org at login and embed `active_org_id` in token, (B) embed all org memberships in `authorities` and let backend extract per-request. Option A is recommended for migration scope.
- **Phase 5 (API contract):** Auth-service management API request/response shapes, error codes, and `mgmt-client` auth mechanism need explicit documentation before service rewrites begin. WireMock stubs must be updated from Zitadel shapes to auth-service shapes before tests can validate the new implementation.

Phases with well-established patterns (no additional research needed):

- **Phase 2:** Spring Security JWT resource server configuration is extensively documented.
- **Phase 3:** Liquibase column rename is a standard migration pattern.
- **Phase 4:** `angular-oauth2-oidc` PKCE config is straightforward once issuer and client ID are known.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All findings from direct pom.xml and code inspection; Spring Authorization Server docs are authoritative |
| Features | HIGH | Based on direct codebase analysis of both auth-service and backend; status (DONE/PARTIAL/MISSING) verified line by line |
| Architecture | HIGH | System boundaries confirmed from code; one unresolved design decision (multi-org token context) is a known gap, not a research failure |
| Pitfalls | HIGH | All pitfalls grounded in specific file/line references; failure modes are deterministic, not speculative |

**Overall confidence:** HIGH

### Gaps to Address

- **Multi-org token context strategy (blocker for Phase 1):** Research identified two options but cannot prescribe the right one without a product/architecture decision. Must be resolved in Phase 1 kickoff before any JIT provisioner code is written.
- **Auth-service management API contract (blocker for Phase 5):** The management API (`/api/v1/mgmt/**`) is implemented but not fully documented. Before Phase 5 begins, enumerate all required operations and verify auth-service controllers cover them. If any are missing, auth-service work must be added to Phase 5 scope.
- **`AccountLinkingAuthenticationProvider.extractLinkingContext` stub (P2 risk):** The account linking PKCE flow returns null. Deferred to P2, but if real users are invited to a second org before v1.x, this is a runtime failure path, not a graceful degradation. Needs explicit scoping decision.
- **Email verification for self-registration (scope question):** `TokenType` enum has no `EMAIL_VERIFICATION` value; the self-registration email verification flow is not wired. If self-registration is in scope for v1 (not confirmed), this is a missing P1 feature in the auth-service.

## Sources

### Primary (HIGH confidence — direct code inspection)
- `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` — JWT converter, resource server config
- `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java` + `JitProvisioningService.java` — provisioning logic, Zitadel claim names
- `backend/src/main/java/de/goaldone/backend/service/CurrentUserResolver.java` — `findByZitadelSub` pattern
- `auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java` — in-memory RSA key, InMemory client repo
- `auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java` — emitted claims: `authorities`, `user_id`, `primary_email`, `emails`
- `auth-service/src/main/java/de/goaldone/authservice/` — domain, controller, service packages (invitation, password reset, membership)
- `frontend/src/app/core/auth/auth.service.ts` — `getUserRoles()`, `getUserOrganizationId()`, scope config
- `frontend/src/assets/env.js` — hardcoded Zitadel issuer and client ID
- `backend/src/main/java/de/goaldone/backend/service/MemberManagementService.java` + `MemberInviteService.java` — full Zitadel SDK coupling

### Primary (HIGH confidence — official documentation)
- Spring Security Docs: OAuth2 Resource Server JWT — issuer-uri vs jwk-set-uri, JwtAuthenticationConverter patterns
- Spring Security Docs: Spring Authorization Server Getting Started — Maven coordinates, JWKS defaults, scope/grant type configuration
- Spring Blog: Authorization Server moving to Spring Security 7.0 — version consolidation impact

---
*Research completed: 2026-05-02*
*Ready for roadmap: yes*
