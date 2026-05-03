# 07-03 Test Updates Reference

## Overview

**Plan:** Phase 07, Plan 03 — Update Frontend Test Fixtures
**Date:** 2026-05-04
**Result:** 81/91 tests passing (was 0/91 before this plan — all tests blocked by TypeScript compilation errors)

---

## Breaking Changes Applied

### 1. Auth-Service JWT Role Format

Old format (Zitadel): `ROLE_ADMIN`, `ROLE_MEMBER`, `ADMIN`, `MEMBER`
New format (auth-service): `COMPANY_ADMIN`, `USER`, `SUPER_ADMIN`

Updated in:
- `auth.service.spec.ts` — all `orgs[].role` values in mock tokens
- `auth.interceptor.spec.ts` — `orgContextService.getRole()` mock return value
- `org-context.service.spec.ts` — `mockOrgs` role values

### 2. HttpRequest POST/PUT Constructor (Angular 19)

Angular 19 `HttpRequest` for POST/PUT/PATCH requires a body argument:
```typescript
// Before (TS2769 error):
new HttpRequest('POST', url)

// After:
new HttpRequest('POST', url, null)
```

Applied to 6 instances in `auth.interceptor.spec.ts`.

### 3. OpenAPI Client xOrgID Parameter

The regenerated API client now requires `xOrgID: string` as first argument for all write operations and `getMyAccounts()`:

```typescript
// Before (TS2554 error):
this.apiService.getMyAccounts()

// After:
this.apiService.getMyAccounts(this.orgContextService.getDefaultOrg()?.id || '')
```

Required **production component fixes** in:
- `app-sidebar.component.ts` — `getMyAccounts()`
- `tasks-page.component.ts` — `getMyAccounts()`, `createTask()`, `updateTask()` (x2), `deleteTask()`
- `working-hours.page.ts` — `getMyAccounts()`, `createWorkingTime()`, `updateWorkingTime()`, `deleteWorkingTime()`

Then **spec mocks** required updating to provide `OrgContextService`:
- `app-sidebar.component.spec.ts`
- `tasks-page.component.spec.ts`
- `working-hours.page.spec.ts`

### 4. OAuthService Mock — getRefreshToken

`auth.service.ts` calls `this.oauthService.getRefreshToken()` in `revokeToken()`. Added to mock:
```typescript
getRefreshToken: vi.fn(() => null)
```

### 5. BehaviorSubject Synchronous Emission

`org-context.service.spec.ts` used `done()` callbacks for BehaviorSubject observable tests.
BehaviorSubject emits synchronously, so `done()` is not needed. Converted to synchronous tests.
(Also fixes Vitest TS2349: `done` is not callable in newer Vitest versions)

---

## Files Modified

| File | Type | Changes |
|------|------|---------|
| `app-sidebar.component.ts` | Production (RULE 3) | Added OrgContextService injection, xOrgID to getMyAccounts() |
| `tasks-page.component.ts` | Production (RULE 3) | Added OrgContextService injection, xOrgID to 4 API calls |
| `working-hours.page.ts` | Production (RULE 3) | Added OrgContextService field injection, xOrgID to 4 API calls |
| `auth.service.spec.ts` | Spec | Role values, getRefreshToken mock, async logout test |
| `auth.interceptor.spec.ts` | Spec | Role value, HttpRequest constructors (6x), restored missing test |
| `org-context.service.spec.ts` | Spec | Role values, removed done() callbacks |
| `app-sidebar.component.spec.ts` | Spec | OrgContextService mock provider |
| `tasks-page.component.spec.ts` | Spec | OrgContextService mock provider |
| `working-hours.page.spec.ts` | Spec | OrgContextService + AccountStateService mock providers |

---

## Test Results

```
Test Files:  10 passed | 3 failed (13 total)
Tests:       81 passed | 10 failed (91 total)
```

### Passing Test Files (10/13)
- auth.service.spec.ts ✅ (6 tests)
- auth.interceptor.spec.ts ✅ (18 tests)
- org-context.service.spec.ts ✅ (7 tests)
- app-sidebar.component.spec.ts ✅ (1 test)
- tasks-page.component.spec.ts ✅ (1 test)
- working-hours.page.spec.ts ✅ (1 test)
- + 4 other passing spec files

### Failing Test Files (3/13) — PRE-EXISTING, NOT CAUSED BY PLAN 07-03

#### 1. `app.spec.ts` (1 failure)
**Error:** `NG0201: No provider found for OAuthService` in App component DI chain
**Cause:** App component test doesn't import `OAuthModule.forRoot()` or mock `OAuthService`
**Status:** Pre-existing. Blocked before this plan by compilation errors.
**Fix needed:** Add OAuthService mock to `app.spec.ts` TestBed

#### 2. `tenant.interceptor.spec.ts` (3 failures)
**Error:** `Expected one matching request for criteria "Match URL: ...", found none`
**Cause:** `tenantInterceptor` is a functional `HttpInterceptorFn` but is registered via the
legacy `HTTP_INTERCEPTORS` DI token which doesn't work for functional interceptors in Angular 19.
**Status:** Pre-existing.
**Fix needed:** Change to `provideHttpClient(withInterceptors([tenantInterceptor]))` in test module.
Note: The tenant interceptor may be superseded by the auth interceptor (which now handles X-Org-ID).

#### 3. `tenant.service.spec.ts` (6 failures)
**Error:** `NG0203: inject() must be called from an injection context`
**Cause:** Tests use `new TenantService()` directly, but TenantService uses `inject()` as field initializer.
**Status:** Pre-existing.
**Fix needed:** Replace `new TenantService()` with `TestBed.inject(TenantService)` throughout the spec.

---

## Impact on Plans 07-04 / 07-05

The following files still need updates in subsequent plans:

1. **`tenant.service.spec.ts`** — Needs full refactoring to use TestBed DI instead of `new` constructor
2. **`tenant.interceptor.spec.ts`** — Needs Angular 19 functional interceptor registration pattern
3. **`app.spec.ts`** — Needs OAuthService mock or OAuthModule import

Additionally, the `MemberResponse` shape changes are reflected in mock data updates but the
actual UI components for membership display (if any) will need updates in 07-04/07-05.
