# Phase 07-05 Error Handling Implementation

**Date:** 2026-05-04
**Status:** COMPLETE

## Error Handling Infrastructure

- [x] `authInterceptor` maps status codes to user-friendly messages (exported `mapErrorToUserMessage`)
- [x] `ErrorNotificationService` wraps PrimeNG `MessageService` for toast display
- [x] Global `<p-toast />` in root `app.html` backed by root-level `MessageService`
- [x] All HTTP errors in interceptor caught and handled
- [x] Token refresh failures handled in `auth.service.ts` (token_refresh_error event)
- [x] 401 Unauthorized → logout + redirect to `/`

## Error Mappings Implemented

- [x] **Network error (status 0):** "Unable to connect to server. Check your connection and try again."
- [x] **403 FORBIDDEN:** "You don't have permission to access this resource. Contact your admin."
- [x] **409 CONFLICT:** "This action cannot be completed. Please refresh and try again."
- [x] **410 GONE:** "This link has expired. Please request a new one."
- [x] **5xx Server errors:** "Something went wrong. Please try again or contact support."
- [x] **401 Unauthorized:** Triggers logout + redirect (no user-facing message in interceptor)

## Components Updated

### OrgSettingsPage (`org-settings.page.ts`)
- [x] Injected `ErrorNotificationService`
- [x] Member list loading: `console.error` + toast on failure
- [x] Invite member: `console.error` with orgId context on failure
- [x] Change role: `console.error` with userId/orgId context on failure
- [x] Remove member: `console.error` with userId/orgId context on failure
- [x] Inline error signals preserved for in-form feedback (409, 403 specific messages)

### TasksPage (`tasks-page.component.ts`)
- [x] Injected `ErrorNotificationService`
- [x] Load tasks: `console.error` + toast on network error (status 0)
- [x] Save task: `console.error` + toast on save failure
- [x] Change status: `console.error` for update failures
- [x] Delete task: `console.error` for deletion failures

### WorkingHoursPage (`working-hours.page.ts`)
- Already uses `MessageService` directly with component-local `<p-toast />`
- No changes required — already has comprehensive toast error handling

## Global Toast Infrastructure

- `MessageService` added to `app.config.ts` global providers
- `<p-toast />` added to root `app.html` template
- `Toast` component imported in root `App` component
- `ErrorNotificationService` (`providedIn: 'root'`) available globally

## Test Coverage

- [x] 9 new unit tests for `mapErrorToUserMessage` in `auth.interceptor.spec.ts`
- [x] `MessageService` added to `app.spec.ts` and `tasks-page.component.spec.ts` providers
- [x] **Total tests: 100/100** (was 91/91, +9 new error mapping tests)
- [x] **Build: 0 TypeScript errors**

## Console Logging

- [x] `[AuthInterceptor]` prefix for all interceptor logs
- [x] `[OrgSettingsPage]` prefix for all settings page logs
- [x] `[TasksPage]` prefix for all task page logs
- [x] Error logs include relevant context (orgId, userId, status code, URL)
- [x] No silent failures — all HTTP errors are logged

## Architecture Decisions

- **Interceptor does NOT call ErrorNotificationService directly** — avoids double-reporting when components also handle errors with inline signals
- **OrgSettingsPage keeps inline error signals** — better UX for form context (user sees error near the form)
- **WorkingHoursPage unchanged** — already has complete toast-based error handling
- **mapErrorToUserMessage exported** — enables unit testing without Angular TestBed
