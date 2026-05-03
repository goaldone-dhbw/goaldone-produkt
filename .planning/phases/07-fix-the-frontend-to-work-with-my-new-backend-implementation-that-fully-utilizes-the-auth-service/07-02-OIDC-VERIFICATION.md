# Phase 07-02 OIDC Verification Results

**Date:** 2026-05-04
**Status:** BUGS FOUND AND FIXED — Live browser testing still required for flow verification

---

## Verification Method

This document covers **static code analysis** of all auth-related files. Sections marked
`[REQUIRES LIVE TESTING]` cannot be verified without a running auth-service + backend.

Files reviewed:
- `frontend/src/assets/env.js.template`
- `frontend/src/app/core/auth/auth.service.ts`
- `frontend/src/app/core/auth/auth.interceptor.ts`
- `frontend/src/app/core/services/org-context.service.ts`
- `frontend/src/app/core/services/tenant.service.ts`
- `frontend/src/app/app.config.ts`
- `auth-service/src/main/.../config/TokenCustomizerConfig.java`
- `auth-service/src/main/.../config/AuthorizationServerConfig.java`
- `auth-service/src/main/.../security/CustomUserDetails.java`
- `auth-service/src/main/.../domain/Role.java`
- `auth-service/src/main/.../startup/ClientSeedingRunner.java`
- `docker/dev/docker-compose.yaml`
- `docker/prod/docker-compose.yaml`

---

## env.js Configuration

### Static Analysis Result: ✅ FIXED (was BROKEN)

**Bug found and fixed (commit `e296cc2`):**
- Template used `${AUTH_SERVICE_CLIENT_ID}` and `${AUTH_SERVICE_ISSUER_URI}` variable names
- docker-compose passes `OIDC_CLIENT_ID` and `OIDC_ISSUER_URI` to the frontend container
- **Effect before fix:** env.js always fell back to localhost defaults in Docker, ignoring the
  configured issuer URI and client ID
- **Fix:** Updated template to use `${OIDC_CLIENT_ID:-goaldone-frontend}` and
  `${OIDC_ISSUER_URI:-http://localhost:9000}` to match docker-compose env var names

Current template values (after fix):
- Client ID: `${OIDC_CLIENT_ID:-goaldone-frontend}` (default for local dev)
- Issuer URI: `${OIDC_ISSUER_URI:-http://localhost:9000}` (default for local dev)
- API Base Path: `${API_BASE_PATH:-http://localhost:8080/api/v1}` (default for local dev)

**Local dev note:** `env.js.template` is NOT automatically compiled to `env.js` during `npm start`.
For local development, create `frontend/src/assets/env.js` manually from the template with
substituted values, or rely on the fallback values in `auth.service.ts` and `auth.interceptor.ts`.

`[REQUIRES LIVE TESTING]` Verify that `window.__env` is populated correctly after deployment.

---

## Client ID Alignment

### Static Analysis Result: ✅ FIXED (was BROKEN)

**Second bug found and fixed (commit `e296cc2`):**
- `ClientSeedingRunner.java` defaulted to `goaldone-web` for `FRONTEND_CLIENT_ID`
- All frontend configs defaulted to `goaldone-frontend`
- **Effect before fix:** Local dev without Docker would fail OIDC flow (client not found in auth-service)
- **Fix:** Changed `ClientSeedingRunner.java` default from `goaldone-web` to `goaldone-frontend`

**Third bug found and fixed (commit `e296cc2`):**
- docker-compose auth-service containers did not receive `FRONTEND_CLIENT_ID` env var
- **Effect before fix:** Auth-service always seeded `goaldone-web` client in Docker regardless of
  `FRONTEND_CLIENT_ID` value in `.env` file
- **Fix:** Added `FRONTEND_CLIENT_ID: ${FRONTEND_CLIENT_ID:-goaldone-frontend}` to both
  `docker/dev/docker-compose.yaml` and `docker/prod/docker-compose.yaml` auth-service sections

**Current alignment (after fix):**
| Component | Variable | Default Value |
|-----------|----------|---------------|
| env.js.template | `OIDC_CLIENT_ID` | `goaldone-frontend` |
| auth.service.ts (fallback) | — | `goaldone-frontend` |
| ClientSeedingRunner.java | `FRONTEND_CLIENT_ID` | `goaldone-frontend` |
| docker-compose auth-service env | `FRONTEND_CLIENT_ID` | `goaldone-frontend` |

---

## OIDC Flow

`[REQUIRES LIVE TESTING]`

### Static Analysis

Authorization server configuration confirmed:
- OIDC enabled via `authorizationServer.oidc(Customizer.withDefaults())`
- PKCE required: `requireProofKey(true)` in ClientSeedingRunner
- Public client (no secret): `ClientAuthenticationMethod.NONE` when no client secret set
- Scopes registered: `openid`, `profile`, `email`, `offline_access`
- Frontend requests: `openid profile email offline_access` (matches)
- Redirect URI seeded: `http://localhost:4200/callback` (matches `auth.service.ts` `redirectUri`)
- Response type: `code` (matches `auth.service.ts` `responseType: 'code'`)

### Live Testing Checklist
- [ ] Discovery document resolved at `{issuerUri}/.well-known/openid-configuration`
- [ ] Authorization redirect to auth-service login page
- [ ] Authorization code flow with PKCE completes
- [ ] Token exchange returns `access_token` + `refresh_token`
- [ ] Redirect back to `http://localhost:4200` (or configured redirectUri) successful
- [ ] No errors in browser console during flow

---

## Token Format

### Static Analysis: ✅ VERIFIED

Token customizer (`TokenCustomizerConfig.java`) adds these claims to the JWT:

| Claim | Type | Example Value |
|-------|------|---------------|
| `authorities` | `Set<String>` | `["ROLE_USER", "ORG_{uuid}_COMPANY_ADMIN"]` |
| `emails` | `List<String>` | `["user@example.com"]` |
| `primary_email` | `String` | `"user@example.com"` |
| `user_id` | `String` (UUID) | `"550e8400-e29b-41d4-a716-446655440000"` |
| `super_admin` | `boolean` | `false` |
| `orgs` | `List<Map>` | `[{"id": "uuid", "slug": "my-org", "role": "COMPANY_ADMIN"}]` |

**Important: `authorities` claim format differs from plan expectation:**
- Plan expected: `["ROLE_USER", "ROLE_ADMIN"]`
- Actual: `["ROLE_USER", "ROLE_SUPER_ADMIN", "ORG_{companyId}_{roleName}"]` where `roleName` is
  `USER`, `COMPANY_ADMIN`, or `SUPER_ADMIN` from the `Role` enum

**`orgs[].role` values (from `Role` enum `.name()`):**
- `USER` (not `ROLE_USER`)
- `COMPANY_ADMIN` (not `ROLE_ADMIN`)
- `SUPER_ADMIN`

**Frontend `getUserRoles()` uses `orgs[].role`, NOT the `authorities` claim** — confirmed correct per Phase 4.2 decision. The method returns `{ orgId: ["COMPANY_ADMIN"] }` or `{ orgId: ["USER"] }`.

`[REQUIRES LIVE TESTING]` Decode JWT and verify claims match above table.

---

## AuthService Methods

### Static Analysis: ✅ VERIFIED

**`initialize()`** — `auth.service.ts` lines 13-44:
- Reads `window.__env` for `issuerUri` and `clientId` at runtime
- Configures `OAuthService` with PKCE-compatible settings
- Handles `token_refresh_error` → clears storage + redirects to login
- Calls `loadDiscoveryDocumentAndTryLogin()` — returns Promise<boolean>
- Registered as `APP_INITIALIZER` in `app.config.ts` — runs before app boots ✅

**`getUserRoles()`** — Returns `{ [orgId]: [role] }` map from `orgs` JWT claim.
- Handles missing token: returns `{}`
- Handles missing/invalid `orgs` claim: returns `{}`
- Correctly uses `org.id` and `org.role` ✅

**`getOrganizations()`** — Returns `Array<{id, slug, role}>` from `orgs` JWT claim.
- Returns `[]` if token missing or `orgs` not an array ✅

**`getActiveOrganization()`** — Returns first org or null ✅

**`isTokenExpirySoon()`** — 5-minute buffer check using `exp` claim ✅

**`refreshToken()`** — Delegates to `OAuthService.refreshToken()`, returns Promise<boolean> ✅

**`revokeToken()`** — Currently a no-op (logs "revocation attempted" but doesn't call revocation endpoint). Token cleared client-side on logout regardless. This is a known limitation.

`[REQUIRES LIVE TESTING]`:
- [ ] `initialize()` completes without error in browser console
- [ ] `getUserRoles()` returns correct authorities after login
- [ ] `getOrganizations()` returns orgs array from JWT
- [ ] `getActiveOrganization()` returns first org

---

## Token Refresh Logic (5-Minute Buffer)

### Static Analysis: ✅ VERIFIED

**`auth.interceptor.ts`** implements correct on-demand refresh:
1. Checks `hasValidAccessToken()` — if no token, lets request through
2. Checks `isTokenExpirySoon()` — if within 5-min buffer, calls `refreshToken()` first
3. After refresh (success or failure), proceeds with `proceedWithAuthorization()`

**`isTokenExpirySoon()`** in `auth.service.ts`:
```typescript
const bufferMs = 5 * 60 * 1000; // 5 minute buffer
return (decodedToken.exp * 1000 - Date.now()) < bufferMs;
```
Logic is correct. ✅

`[REQUIRES LIVE TESTING]`:
- [ ] Token refresh fires before a request when token is near expiry
- [ ] Network tab shows POST to `/oauth/token` during near-expiry request

---

## Multi-Org Provisioning

### Static Analysis: ✅ VERIFIED

**`OrgContextService`** (`org-context.service.ts`):
- `dialogOrgContext` initializes as `BehaviorSubject<null>` — cleared on service creation ✅
- `settingsOrgContext` initializes as `BehaviorSubject<null>` — cleared on service creation ✅
- `getDefaultOrg()` delegates to `authService.getActiveOrganization()` (first org) ✅
- `getOrganizations()` delegates to `authService.getOrganizations()` (all orgs from JWT) ✅
- `hasMultipleOrgs()` returns `true` when user has 2+ org memberships ✅
- `validateOrgAccess(orgId)` validates org membership from JWT ✅

**`TenantService`** (`tenant.service.ts`):
- Uses deprecated `getUserMemberships()` which correctly delegates to `getOrganizations()` ✅
- Auto-selects single org if user has exactly one membership ✅
- Restores from `sessionStorage` for multi-org users, defaults to first org ✅

`[REQUIRES LIVE TESTING]`:
- [ ] After login with multi-org user, all orgs visible via `orgContextService.getOrganizations()`
- [ ] First org selected as default (`orgContextService.getDefaultOrg()`)
- [ ] `orgContextService.getDialogOrg()` returns `null` on fresh login
- [ ] `orgContextService.getSettingsOrg()` returns `null` on fresh login

---

## X-Org-ID Header Injection

### Static Analysis: ✅ VERIFIED

**`auth.interceptor.ts`** — `proceedWithAuthorization()`:

1. Authorization header: `Bearer {token}` added for all API requests (URL must start with `apiBasePath`) ✅
2. X-Org-ID header added **only** for:
   - `POST`, `PUT`, `DELETE` requests (destructive)
   - Any request to `/organization/members` endpoint
   - GET list endpoints do NOT receive X-Org-ID header (returns all-org data) ✅

**Priority order** in `resolveOrgIdForRequest()`:
```
dialog org → settings org → default org (first org from JWT)
```
Implemented correctly ✅

**Edge case**: If no valid org ID resolved (user has no orgs), X-Org-ID header is omitted.

`[REQUIRES LIVE TESTING]`:
- [ ] Network tab shows `Authorization: Bearer {token}` on API requests
- [ ] Network tab shows `X-Org-ID: {orgId}` on POST/PUT/DELETE requests
- [ ] No `X-Org-ID` on plain GET list requests
- [ ] Priority: dialog org overrides settings org overrides default org

---

## Issues Found and Fixed

### 1. [Rule 1 - Bug] env.js.template used wrong environment variable names

**Found during:** Task 1 (static code analysis)
**Issue:** Template substituted `${AUTH_SERVICE_CLIENT_ID}` and `${AUTH_SERVICE_ISSUER_URI}` but
docker-compose passes `OIDC_CLIENT_ID` and `OIDC_ISSUER_URI` to the frontend container. In Docker,
env.js always fell back to `http://localhost:9000` and `goaldone-frontend` regardless of what was
configured.
**Fix:** Updated `frontend/src/assets/env.js.template` to use correct variable names.
**Commit:** `e296cc2`

### 2. [Rule 1 - Bug] ClientSeedingRunner default client ID mismatch

**Found during:** Task 1 (static code analysis)
**Issue:** `ClientSeedingRunner.java` used `goaldone-web` as default `FRONTEND_CLIENT_ID`, but all
frontend config files and env.js.template defaulted to `goaldone-frontend`. Local development
without explicit env var configuration would fail OIDC flow (client_id not registered).
**Fix:** Changed `ClientSeedingRunner.java` default from `goaldone-web` to `goaldone-frontend`.
**Commit:** `e296cc2`

### 3. [Rule 2 - Missing Configuration] Auth-service docker-compose missing FRONTEND_CLIENT_ID

**Found during:** Task 1 (static code analysis)
**Issue:** docker-compose (dev + prod) auth-service service definitions did not pass
`FRONTEND_CLIENT_ID` env var. The auth-service would always seed `goaldone-web` in Docker,
regardless of what `FRONTEND_CLIENT_ID` was set to in the `.env` file.
**Fix:** Added `FRONTEND_CLIENT_ID: ${FRONTEND_CLIENT_ID:-goaldone-frontend}` to auth-service
environment in both `docker/dev/docker-compose.yaml` and `docker/prod/docker-compose.yaml`.
**Commit:** `e296cc2`

---

## Known Limitations (Not Bugs)

### Token Revocation is a No-Op
`revokeToken()` in `auth.service.ts` logs "revocation attempted" but does not call the authorization
server's revocation endpoint. The `angular-oauth2-oidc` library may expose `revokeTokenAndLogout()`
or a direct revocation API — this should be wired up in Plan 07-03 for proper D-04 compliance.
Tokens are cleared client-side on logout, so the security impact is limited (tokens can still be
used until natural expiry).

### TenantService uses deprecated AuthService methods
`tenant.service.ts` calls `getUserMemberships()` which is deprecated (delegates to `getOrganizations()`).
No functional impact. Cleanup deferred to Plan 07-03.

---

## Next Steps (before Plan 07-03)

1. **Live OIDC flow test** — Run auth-service + backend + frontend locally and verify complete login flow
2. **Verify JWT claims** — Decode token in browser and confirm `user_id`, `orgs`, `authorities` format
3. **Wire token revocation** — Implement actual HTTP call to revocation endpoint in `revokeToken()`
4. **Clean up deprecated TenantService calls** — Replace `getUserMemberships()` with `getOrganizations()`

---

*Static analysis complete. Three bugs fixed in commit `e296cc2`.*
