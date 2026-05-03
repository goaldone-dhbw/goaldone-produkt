---
phase: "07"
plan: "07-02"
subsystem: auth
tags: [oidc, auth-service, jwt, env-config, docker-compose, static-analysis]
dependency_graph:
  requires: ["07-01"]
  provides: ["07-03", "07-04", "07-05"]
  affects:
    - "frontend/src/assets/env.js.template"
    - "auth-service/src/main/.../startup/ClientSeedingRunner.java"
    - "docker/dev/docker-compose.yaml"
    - "docker/prod/docker-compose.yaml"
tech_stack:
  added: []
  patterns: ["static code analysis", "OIDC/JWT claim verification", "env.js runtime config"]
key_files:
  created:
    - ".planning/phases/07-fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service/07-02-OIDC-VERIFICATION.md"
  modified:
    - "frontend/src/assets/env.js.template"
    - "auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java"
    - "docker/dev/docker-compose.yaml"
    - "docker/prod/docker-compose.yaml"
decisions:
  - "orgs[].role values are USER/COMPANY_ADMIN/SUPER_ADMIN (Role enum names) — not ROLE_USER/ROLE_ADMIN as plan stated"
  - "authorities JWT claim contains ROLE_USER + ORG_{uuid}_{roleName} entries — frontend correctly ignores this and uses orgs[] claim"
  - "revokeToken() is a no-op — deferred to Plan 07-03 for proper D-04 compliance"
  - "TenantService deprecated method cleanup deferred to Plan 07-03"
metrics:
  duration: "35 minutes"
  completed: "2026-05-04"
  tasks_completed: 8
  files_changed: 5
---

# Phase 7 Plan 07-02: Auth Service Token Integration & OIDC Verification Summary

## One-liner

Static code analysis of complete OIDC stack (frontend auth service + JWT customizer + docker config) uncovered three configuration bugs that would break the auth flow in both Docker and local dev — all three fixed and committed.

## What Was Done

### Task 1: Verified env.js Configuration
- Found only `env.js.template` exists (no `env.js`) — correct, Docker entrypoint generates it via `envsubst`
- **CRITICAL BUG FOUND**: Template used `${AUTH_SERVICE_CLIENT_ID}` and `${AUTH_SERVICE_ISSUER_URI}` but docker-compose passes `OIDC_CLIENT_ID` and `OIDC_ISSUER_URI` — env.js always fell back to localhost defaults in Docker
- Fixed template variable names to match docker-compose: `OIDC_CLIENT_ID`, `OIDC_ISSUER_URI`

### Tasks 2-4: OIDC Flow, JWT Decode, AuthService.initialize() [Static Analysis]
- Confirmed `AuthorizationServerConfig.java` enables OIDC, requires PKCE, uses JdbcRegisteredClientRepository
- Confirmed `TokenCustomizerConfig.java` adds: `authorities` (Set<String>), `user_id` (UUID string), `orgs` (List<{id,slug,role}>), `emails`, `primary_email`, `super_admin` claims
- Confirmed `auth.service.ts` `initialize()`: reads `window.__env` at runtime, configures OAuthService, registers as APP_INITIALIZER
- Confirmed `getUserRoles()` correctly uses `orgs[].role` (not `authorities` claim) per Phase 4.2 decision
- Discovered and documented actual `Role` enum values: `USER`, `COMPANY_ADMIN`, `SUPER_ADMIN` (plan docs said `ROLE_USER`/`ROLE_ADMIN`)

### Task 5: Verified Token Refresh Logic
- Confirmed `isTokenExpirySoon()` uses correct 5-minute buffer: `(exp * 1000 - Date.now()) < 5 * 60 * 1000`
- Confirmed `auth.interceptor.ts` performs on-demand refresh before each request when token is near expiry
- Confirmed refresh failure is graceful: proceeds with current token (backend handles 401)

### Task 6: Verified Multi-Org Provisioning [Static Analysis]
- `OrgContextService`: `dialogOrgContext` and `settingsOrgContext` both initialize as `BehaviorSubject<null>` — cleared on init ✅
- `getDefaultOrg()` → `authService.getActiveOrganization()` → first org from JWT ✅
- `getOrganizations()` → `authService.getOrganizations()` → full orgs array from JWT ✅

### Task 7: Verified X-Org-ID Header Injection [Static Analysis]
- Header injected for: all POST/PUT/DELETE, plus GET `/organization/members`
- Priority correctly implemented: dialog org → settings org → default org (first JWT org)
- GET list endpoints correctly omit X-Org-ID header

### Additional Bugs Found and Fixed
- **ClientSeedingRunner default mismatch**: `goaldone-web` vs `goaldone-frontend` — fixed
- **Docker-compose auth-service missing FRONTEND_CLIENT_ID**: auth-service seeds wrong client in Docker — fixed for both dev and prod

### Task 8: Created OIDC Verification Document
- Created `07-02-OIDC-VERIFICATION.md` with full static analysis findings
- Documented JWT claim format with actual values
- Marked all live browser testing sections as `[REQUIRES LIVE TESTING]`
- Listed known limitations (token revocation no-op, deprecated TenantService methods)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] env.js.template used wrong environment variable names**
- **Found during:** Task 1 static analysis
- **Issue:** Template substituted `${AUTH_SERVICE_CLIENT_ID}` and `${AUTH_SERVICE_ISSUER_URI}` — docker-compose sets `OIDC_CLIENT_ID` and `OIDC_ISSUER_URI`. Result: env.js always had localhost fallback values in Docker.
- **Fix:** Updated `frontend/src/assets/env.js.template` to use `${OIDC_CLIENT_ID:-goaldone-frontend}` and `${OIDC_ISSUER_URI:-http://localhost:9000}`
- **Files modified:** `frontend/src/assets/env.js.template`
- **Commit:** `e296cc2`

**2. [Rule 1 - Bug] ClientSeedingRunner default client ID mismatch**
- **Found during:** Task 1 static analysis
- **Issue:** `ClientSeedingRunner.java` defaulted to `goaldone-web` but frontend (env.js.template + auth.service.ts fallback) defaulted to `goaldone-frontend`. Local dev without Docker → client not found error.
- **Fix:** Changed `@Value("${FRONTEND_CLIENT_ID:goaldone-web}")` → `@Value("${FRONTEND_CLIENT_ID:goaldone-frontend}")`
- **Files modified:** `auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java`
- **Commit:** `e296cc2`

**3. [Rule 2 - Missing Configuration] Auth-service docker-compose missing FRONTEND_CLIENT_ID**
- **Found during:** Task 1 static analysis
- **Issue:** Docker-compose auth-service containers (dev + prod) did not forward `FRONTEND_CLIENT_ID`. Auth-service always seeded `goaldone-web` in Docker regardless of `.env` configuration.
- **Fix:** Added `FRONTEND_CLIENT_ID: ${FRONTEND_CLIENT_ID:-goaldone-frontend}` to both docker-compose files
- **Files modified:** `docker/dev/docker-compose.yaml`, `docker/prod/docker-compose.yaml`
- **Commit:** `e296cc2`

**4. [Scope note] Plan said "no code changes" — three bugs required fixing**
- Plan 07-02 stated "No code changes to commit in this plan — verification artifact only." Static analysis revealed real bugs that would prevent the OIDC flow from working in any environment. Auto-fixed per Rule 1/2.

## Known Stubs

| Stub | File | Line | Reason |
|------|------|------|--------|
| `revokeToken()` is a no-op | `core/auth/auth.service.ts` | 202-216 | Logs "revocation attempted" but no HTTP call — D-04 compliance deferred to Plan 07-03 |
| `TenantService.getUserMemberships()` calls | `core/services/tenant.service.ts` | 23, 37 | Deprecated method — works correctly but cleanup deferred to Plan 07-03 |

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. Bug fixes align existing configuration; no new attack surface added.

## Self-Check: PASSED

- `07-02-OIDC-VERIFICATION.md` exists: ✅
- Commit `e296cc2` (bug fixes) exists: ✅
- Commit `96c5b13` (verification doc) exists: ✅
- No unexpected file deletions: ✅
- `env.js.template` updated: ✅
- `ClientSeedingRunner.java` updated: ✅
- `docker/dev/docker-compose.yaml` updated: ✅
- `docker/prod/docker-compose.yaml` updated: ✅
