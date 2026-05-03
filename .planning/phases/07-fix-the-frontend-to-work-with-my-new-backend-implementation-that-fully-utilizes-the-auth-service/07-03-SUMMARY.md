---
phase: "07"
plan: "03"
subsystem: "frontend-tests"
tags: [testing, vitest, angular, auth-service, api-client, org-context]
dependency-graph:
  requires: ["07-01", "07-02"]
  provides: ["07-04", "07-05"]
  affects: ["frontend/src/app/core/auth", "frontend/src/app/core/services", "frontend/src/app/features"]
tech-stack:
  added: []
  patterns: ["Angular TestBed mocking", "Vitest async patterns", "functional interceptor registration"]
key-files:
  created:
    - ".planning/phases/07-fix.../07-03-TEST-UPDATES.md"
    - ".planning/phases/07-fix.../deferred-items.md"
  modified:
    - "frontend/src/app/core/auth/auth.service.spec.ts"
    - "frontend/src/app/core/auth/auth.interceptor.spec.ts"
    - "frontend/src/app/core/services/org-context.service.spec.ts"
    - "frontend/src/app/shared/components/app-sidebar/app-sidebar.component.spec.ts"
    - "frontend/src/app/shared/components/app-sidebar/app-sidebar.component.ts"
    - "frontend/src/app/features/tasks/tasks-page.component.spec.ts"
    - "frontend/src/app/features/tasks/tasks-page.component.ts"
    - "frontend/src/app/features/working-hours/working-hours.page.spec.ts"
    - "frontend/src/app/features/working-hours/working-hours.page.ts"
decisions:
  - "Provided empty-string '' (not null/undefined) for xOrgID in component production code — interceptor overrides for POST/PUT, getMyAccounts needs real value"
  - "Converted BehaviorSubject observable tests from done() callbacks to synchronous — BehaviorSubject.next() is synchronous"
  - "Deferred tenant.service.spec.ts and tenant.interceptor.spec.ts fixes to Plan 07-04 — pre-existing issues unrelated to spec fixture updates"
metrics:
  duration: "~90 minutes"
  completed: "2026-05-04"
  tasks_completed: 9
  files_modified: 9
---

# Phase 07 Plan 03: Update Frontend Test Fixtures — Summary

## One-Liner

Fixed spec files to use auth-service JWT role format (`COMPANY_ADMIN`/`USER`), correct HttpRequest constructors (Angular 19 body arg), and OrgContextService mock providers; also fixed 3 production components with missing xOrgID API parameters.

## What Was Built

**Test fixture updates** for the Phase 07 spec files to align with:
- Auth-service JWT token format (role names changed from Zitadel format)
- Regenerated OpenAPI client requiring explicit `xOrgID` parameter on write operations
- Angular 19 `HttpRequest` POST/PUT constructors requiring body argument
- Vitest synchronous patterns (replacing obsolete `done()` callbacks)

**Result:** Test suite went from 0/91 passing (TypeScript compilation errors blocking all tests) to **81/91 passing**, with remaining 10 failures all pre-existing and out of scope.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| T1 | Review breaking changes from API regen | (analysis only) |
| T2 | Update auth.service.spec.ts for auth-service token format | `1e52dfb` |
| T3 | Update auth.interceptor.spec.ts for X-Org-ID header logic | `8e5cc16` |
| T4 | Update org-context.service.spec.ts role values + async patterns | `be691cd` |
| T5 | Add OrgContextService mock to app-sidebar.component.spec.ts | `a354382` |
| T6 | Add OrgContextService mock to tasks-page.component.spec.ts | `42e32ff` |
| T7 | Add OrgContextService + AccountStateService mocks to working-hours.page.spec.ts | `d1f209a` |

## Deviations from Plan

### Auto-fixed Issues (RULE 3 — Blocking)

**1. [Rule 3 - Blocking] Missing xOrgID parameter in app-sidebar.component.ts**
- **Found during:** Task T5 (adding OrgContextService mock to sidebar spec)
- **Issue:** `getMyAccounts()` called without required `xOrgID` argument (TS2554). All tests blocked by compilation errors.
- **Fix:** Injected `OrgContextService` via constructor, pass `getDefaultOrg()?.id || ''` as xOrgID
- **Files modified:** `app-sidebar.component.ts`
- **Commit:** `9db68c1`

**2. [Rule 3 - Blocking] Missing xOrgID parameters in tasks-page.component.ts**
- **Found during:** Task T6 (adding OrgContextService mock to tasks-page spec)
- **Issue:** `createTask()`, `updateTask()` (x2), `deleteTask()`, `getMyAccounts()` all missing xOrgID (TS2554)
- **Fix:** Injected `OrgContextService` via constructor, added `getActiveOrgId()` helper, passed to all API calls
- **Files modified:** `tasks-page.component.ts`
- **Commit:** `679f9c3`

**3. [Rule 3 - Blocking] Missing xOrgID parameters in working-hours.page.ts**
- **Found during:** Task T7 (adding OrgContextService mock to working-hours spec)
- **Issue:** `createWorkingTime()`, `updateWorkingTime()`, `deleteWorkingTime()`, `getMyAccounts()` all missing xOrgID (TS2554)
- **Fix:** Added `inject` to Component imports, injected `OrgContextService` via field injection, added `getActiveOrgId()` helper, passed to all API calls
- **Files modified:** `working-hours.page.ts`
- **Commit:** `f041829`

## Key Technical Decisions

1. **xOrgID empty string fallback:** For `getMyAccounts()` in non-write context, using `getDefaultOrg()?.id || ''` — the API client accepts empty string (throws only on null/undefined). For write operations, the auth interceptor overrides the header anyway.

2. **Synchronous BehaviorSubject tests:** Replaced `done()` callback pattern with synchronous assertions in org-context.service.spec.ts. BehaviorSubject fires synchronously — no async infrastructure needed.

3. **HttpRequest POST constructor:** Angular 19 `HttpRequest` requires body as 3rd arg for POST/PUT. Old `new HttpRequest('POST', url)` → TS2769. Fixed to `new HttpRequest('POST', url, null)`.

4. **Auth-service role names:** JWT tokens from auth-service use `USER`, `COMPANY_ADMIN`, `SUPER_ADMIN` — NOT the Zitadel format (`ROLE_USER`, `ROLE_ADMIN`, etc.). All mock tokens updated.

## Test Results

```
Test Files:  10 passed | 3 failed (13 total)
Tests:       81 passed | 10 failed (91 total)
```

**Before Plan 07-03:** 0/91 passing (TypeScript compilation errors blocked all tests)
**After Plan 07-03:** 81/91 passing (+81 tests now working)

### Remaining Failures (Pre-Existing, Out of Scope)

| File | Failures | Root Cause | Deferred To |
|------|----------|------------|-------------|
| `app.spec.ts` | 1 | No OAuthService mock in App component TestBed | 07-04 |
| `tenant.interceptor.spec.ts` | 3 | Functional interceptor registered via legacy HTTP_INTERCEPTORS token | 07-04 |
| `tenant.service.spec.ts` | 6 | `new TenantService()` called outside Angular injection context | 07-04 |

See `deferred-items.md` for full details.

## Known Stubs

None — all changes are real implementations, not stubs.

## Threat Flags

None — no new security-relevant surfaces introduced. Test-only changes + internal xOrgID parameter wiring.

## Self-Check: PASSED

### Files verified to exist:
- [x] `frontend/src/app/core/auth/auth.service.spec.ts` ✅
- [x] `frontend/src/app/core/auth/auth.interceptor.spec.ts` ✅
- [x] `frontend/src/app/core/services/org-context.service.spec.ts` ✅
- [x] `frontend/src/app/shared/components/app-sidebar/app-sidebar.component.spec.ts` ✅
- [x] `frontend/src/app/features/tasks/tasks-page.component.spec.ts` ✅
- [x] `frontend/src/app/features/working-hours/working-hours.page.spec.ts` ✅
- [x] `07-03-TEST-UPDATES.md` ✅
- [x] `deferred-items.md` ✅

### Commits verified:
- [x] `9db68c1` fix(07-03): add OrgContextService injection + xOrgID to sidebar getMyAccounts ✅
- [x] `679f9c3` fix(07-03): add OrgContextService injection + xOrgID params to task API calls ✅
- [x] `f041829` fix(07-03): add OrgContextService injection + xOrgID params to working-hours API calls ✅
- [x] `1e52dfb` test(07-03): update auth.service tests for auth-service token format ✅
- [x] `8e5cc16` test(07-03): update auth.interceptor tests for X-Org-ID header logic ✅
- [x] `be691cd` test(07-03): fix done callbacks and update role values in org-context spec ✅
- [x] `a354382` test(07-03): add OrgContextService mock to app-sidebar component spec ✅
- [x] `42e32ff` test(07-03): add OrgContextService mock to tasks-page component spec ✅
- [x] `d1f209a` test(07-03): add OrgContextService + AccountStateService mocks to working-hours spec ✅
