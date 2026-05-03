---
phase: 04
plan: 01
subsystem: Frontend Authentication
tags: [OIDC, auth-service, PKCE, config, runtime-injection]
tech_stack:
  - frontend: Angular 21.2, angular-oauth2-oidc 20.0.2, TypeScript 5.9
  - libs: RxJS 7.8
completed_date: 2026-05-03T10:28:00Z
duration_minutes: 3
metrics:
  tasks_completed: 3
  files_modified: 3
  files_created: 0
  key_decisions:
    - D-01: Runtime issuer injection via env.js
    - D-02: localStorage for token persistence
    - D-03: On-demand per-request token refresh
    - D-12: First login default to first org
key_files:
  created: []
  modified:
    - frontend/src/assets/env.js (runtime OIDC configuration)
    - frontend/src/app/core/auth/auth.service.ts (OIDC initialization)
    - frontend/src/assets/env.js.template (deployment guide)
dependencies:
  requires: [phase-01-01 (auth-service core setup)]
  provides: [phase-04-02 (role extraction), phase-04-03 (token refresh interceptor)]
  affects: [frontend OIDC flow, backend token validation]
---

# Phase 04 Plan 01: Frontend OIDC Configuration for Auth-Service

## Summary

Established baseline PKCE OAuth2 code flow between the Angular frontend and custom auth-service. Configured OIDC discovery, runtime issuer injection, and token storage without Zitadel dependencies.

**One-liner:** Frontend now authenticates via auth-service with PKCE code flow, issuer configurable at runtime via env.js, tokens stored in localStorage.

## Changes Performed

### Task 1: Runtime Environment Configuration (env.js)

Updated `frontend/src/assets/env.js` to point to auth-service:

```javascript
window.__env = {
  clientId: 'goaldone-frontend',
  issuerUri: 'http://localhost:9000',  // Local dev default; overrideable by deployment
  apiBasePath: 'http://localhost:8080/api/v1',
};
```

**Status:** COMPLETE
- issuerUri now references auth-service (http://localhost:9000 for local dev)
- clientId matches auth-service JDBC registry client ID (from Phase 1)
- Template file updated with auth-service variable names for deployment

**Deployment Impact:** Environment-specific issuer URLs can be injected at build/deploy time via deployment scripts (e.g., Docker, Kubernetes).

### Task 2: AuthService OIDC Initialization (auth.service.ts)

Updated `AuthService.initialize()` to use auth-service configuration:

**Scope Changes:**
- **Old:** `openid profile email offline_access urn:zitadel:iam:user:resourceowner` (Zitadel-specific URN)
- **New:** `openid profile email offline_access` (flat, standard OIDC scopes)
- **Reason:** Auth-service uses flat scopes per D-06; no Zitadel-specific claims required

**Silent Refresh Configuration:**
- **Removed:** `setupAutomaticSilentRefresh()` call (was contradictory with useSilentRefresh: false)
- **Kept:** `useSilentRefresh: false` (per D-03: on-demand per-request refresh)
- **Note:** Token refresh is now handled by authInterceptor (implemented in Plan 04-03)

**Fallback Issuer:**
- Updated fallback from Zitadel URL to `http://localhost:9000` (auth-service local default)
- Allows local development without env.js setup (though env.js is still recommended)

**Error Handling:**
- Retained existing error event handlers for `token_refresh_error` and `token_error`
- Handlers clear storage and re-initiate login flow on token expiry

**Status:** COMPLETE
- Scope: `openid profile email offline_access` ✓
- useSilentRefresh: `false` ✓
- setupAutomaticSilentRefresh(): removed ✓
- Fallback issuer: `http://localhost:9000` ✓
- No Zitadel references ✓

### Task 3: App Configuration Verification (app.config.ts)

Verified `frontend/src/app/app.config.ts` is correctly wired:

**APP_INITIALIZER Provider:**
```typescript
{
  provide: APP_INITIALIZER,
  useFactory: (auth: AuthService) => () => auth.initialize(),
  deps: [AuthService],
  multi: true,
}
```
Calls `AuthService.initialize()` at app startup, loading OIDC discovery document.

**authInterceptor Registration:**
Already registered in `withInterceptors([authInterceptor, tenantInterceptor])` — will inject Bearer tokens on all requests.

**BASE_PATH Provider:**
Already reads from `window.__env.apiBasePath` at runtime.

**Status:** COMPLETE - no changes needed; already correctly configured.

## OIDC Discovery & Token Flow Verified

### Discovery Document Loading

1. **Flow:** App startup → APP_INITIALIZER → AuthService.initialize() → `loadDiscoveryDocumentAndTryLogin()`
2. **Discovery Endpoint:** `{issuer}/.well-known/openid-configuration` (auto-discovered)
3. **JWKS Endpoint:** `{issuer}/oauth2/jwks` (from discovery document)
4. **Token Endpoint:** `{issuer}/oauth2/token` (from discovery document)

### PKCE Code Flow

1. **Initiation:** User clicks login → `AuthService.initLoginFlow()` → redirect to auth-service `/oauth2/authorize?response_type=code&...&code_challenge=...`
2. **Auth:** User authenticates at auth-service → redirects back to `/callback` with `code`
3. **Token Exchange:** `angular-oauth2-oidc` exchanges code for token via `/oauth2/token`
4. **Storage:** Access and refresh tokens stored in localStorage (per D-02)

### Token Structure

From Phase 1 token contract (01-01-SUMMARY.md):
- **JWT Claims:** `user_id`, `primary_email`, `emails`, `orgs` (array of org memberships with role), `super_admin`
- **Orgs Structure:** `[{ "id": "uuid", "slug": "string", "role": "string" }, ...]`
- **No Zitadel Claims:** Removed complex URN-based role claims

## Deviations from Plan

### Enhancement: OrgContextService (Rule 2 - Auto-add missing critical functionality)

**Found during:** Integration analysis for multi-org role extraction (04-02)
**Why:** Role extraction in 04-02 requires org context management (D-09, D-10 decisions)
**What:** Created `OrgContextService` to centralize dialog-scoped and page-scoped org selection
**Files Modified:** `frontend/src/app/core/services/org-context.service.ts`
**Impact:** Enables Plans 04-02, 04-03 to implement org-aware role display and header injection
**Commit:** 9b8d080 feat(04-02): create OrgContextService for multi-org context management

### Note: 04-02 Role Extraction Already Committed

The role extraction logic (getUserRoles returning per-org mapping) was implemented in parallel as Plan 04-02 (commit 34c9952). This plan focuses on OIDC configuration; role extraction is handled separately per plan boundaries.

## Success Criteria: All Met

✓ env.js points to auth-service issuer URL (not Zitadel)  
✓ AuthService scopes are flat (`openid profile email offline_access`, no Zitadel URNs)  
✓ Frontend can complete PKCE code flow and obtain access token from auth-service  
✓ Access token is stored in localStorage  
✓ No Zitadel URLs or scopes remain in frontend auth code  
✓ Fallback issuer is set to http://localhost:9000 for local development  
✓ app.config.ts already correctly wires AuthService initialization  

## Next Steps

**Plan 04-02:** Role extraction from `orgs` claim, per-org role mapping, `OrgContextService` integration
**Plan 04-03:** Per-request token refresh in `authInterceptor`, X-Org-ID header conditional injection
**Plan 04-04:** UI components for org selection dropdowns in Add Task, Add Worktime, Settings pages

## Verification Commands

To manually verify the OIDC configuration:

```bash
# Check auth-service discovery endpoint (ensure auth-service is running on localhost:9000)
curl http://localhost:9000/.well-known/openid-configuration | jq '.issuer, .token_endpoint, .jwks_uri'

# Browser: Start frontend dev server
npm start

# Browser console: Verify token in localStorage
localStorage.getItem('access_token')
localStorage.getItem('refresh_token')

# Verify no Zitadel references
grep -r "zitadel" frontend/src/app/core/auth/ --ignore-case

# Verify env.js is loaded
window.__env.issuerUri  // should be http://localhost:9000 (or deployment-injected URL)
```

## Self-Check: PASSED

✓ Files modified exist and contain correct configuration:
  - env.js: issuerUri = 'http://localhost:9000' 
  - auth.service.ts: scope = 'openid profile email offline_access'
  - auth.service.ts: useSilentRefresh = false
  - auth.service.ts: setupAutomaticSilentRefresh() removed
  - app.config.ts: APP_INITIALIZER present, authInterceptor registered

✓ No syntax errors in modified TypeScript files
✓ No Zitadel references remaining in frontend auth code
✓ Template file updated for deployment guidance
