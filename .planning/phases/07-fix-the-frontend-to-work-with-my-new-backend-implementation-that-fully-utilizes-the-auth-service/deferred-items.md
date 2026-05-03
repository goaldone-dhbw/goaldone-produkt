# Phase 07-03 Deferred Items

**Logged by:** Plan 07-03 execution
**Date:** 2026-05-04

## Pre-Existing Test Failures (Out of Scope for 07-03)

These test failures existed before Plan 07-03 (they were masked by TypeScript compilation errors).
They are NOT caused by Plan 07-03 changes. Deferred to Plans 07-04 or 07-05.

### 1. app.spec.ts — OAuthService not provided

**File:** `frontend/src/app/app.spec.ts`
**Error:** `NG0201: No provider found for OAuthService. Path: AccountStateService -> TenantService -> AuthService -> OAuthService`
**Root Cause:** App component test does not import `OAuthModule.forRoot()` or provide `OAuthService` mock
**Fix needed:** Add `OAuthService` mock or import `OAuthModule.forRoot()` in test module

### 2. tenant.interceptor.spec.ts — Functional interceptor registered via old HTTP_INTERCEPTORS token

**File:** `frontend/src/app/core/interceptors/tenant.interceptor.spec.ts`
**Error:** `Expected one matching request for criteria "Match URL: /api/tasks", found none.`
**Root Cause:** `tenantInterceptor` is a functional interceptor (`HttpInterceptorFn`) but is registered
via the legacy `HTTP_INTERCEPTORS` token which does not work for functional interceptors in Angular 19.
**Fix needed:** Change registration to `provideHttpClient(withInterceptors([tenantInterceptor]))` in test module

### 3. tenant.service.spec.ts — inject() called outside injection context

**File:** `frontend/src/app/core/services/tenant.service.spec.ts`
**Error:** `NG0203: inject() function must be called from an injection context`
**Root Cause:** Tests use `new TenantService()` directly instead of `TestBed.inject(TenantService)`.
Since `TenantService` uses `inject(AuthService)` as a class field initializer, calling `new TenantService()`
outside Angular's DI context throws NG0203.
**Fix needed:** Replace `new TenantService()` with service instantiation via DI (`TestBed.inject(TenantService)`)
and mock `AuthService` in the test module

## Impact

These failures affect **10 of 91 tests**. The remaining **81 tests pass**.
