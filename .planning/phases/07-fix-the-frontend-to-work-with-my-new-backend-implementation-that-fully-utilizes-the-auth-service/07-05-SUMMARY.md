---
phase: "07"
plan: "05"
subsystem: frontend
tags: [error-handling, toast, auth-interceptor, user-experience, primeng]
dependency_graph:
  requires: [07-04]
  provides: [global-error-handling, error-notification-service, 401-redirect]
  affects: [auth.interceptor, org-settings, tasks-page, app-config]
tech_stack:
  added: [ErrorNotificationService]
  patterns: [functional-interceptor-error-handling, primeng-toast-pattern, console-logging-with-context]
key_files:
  created:
    - frontend/src/app/core/services/error-notification.service.ts
    - .planning/phases/07-fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service/07-05-ERROR-HANDLING.md
  modified:
    - frontend/src/app/core/auth/auth.interceptor.ts
    - frontend/src/app/core/auth/auth.interceptor.spec.ts
    - frontend/src/app/app.config.ts
    - frontend/src/app/app.html
    - frontend/src/app/app.ts
    - frontend/src/app/app.spec.ts
    - frontend/src/app/features/org-settings/org-settings.page.ts
    - frontend/src/app/features/tasks/tasks-page.component.ts
    - frontend/src/app/features/tasks/tasks-page.component.spec.ts
decisions:
  - "Interceptor logs errors but does NOT call ErrorNotificationService directly — avoids double-reporting with inline component error signals"
  - "mapErrorToUserMessage() exported from interceptor for testability without Angular TestBed"
  - "OrgSettingsPage keeps both inline error signals AND console.error — inline for form UX, logs for debugging"
  - "WorkingHoursPage unchanged — already uses MessageService directly with component-local toast"
metrics:
  duration: "45 minutes"
  completed: "2026-05-04"
  tasks_completed: 10
  files_changed: 9
  tests_before: 91
  tests_after: 100
---

# Phase 7 Plan 05: Error Handling & User-Friendly Messaging Summary

**One-liner:** Global error handling with HTTP status mapping (403/409/410/5xx/network), PrimeNG Toast via ErrorNotificationService, and 401→logout redirect in functional authInterceptor.

## What Was Built

### ErrorNotificationService (NEW)
Created `frontend/src/app/core/services/error-notification.service.ts`:
- `showError(message, title?)` — 5-second error toast
- `showSuccess(message, title?)` — 3-second success toast  
- `showWarn(message, title?)` — 4-second warning toast
- Wraps PrimeNG `MessageService` (root-provided)
- `providedIn: 'root'` — injectable in any component or service

### Global Toast Infrastructure
- `MessageService` added to `app.config.ts` global providers
- `<p-toast />` added to root `app.html` template
- `Toast` imported in root `App` component (`app.ts`)

### authInterceptor Error Handling (ENHANCED)
Added to `auth.interceptor.ts`:
- Exported `mapErrorToUserMessage(error: HttpErrorResponse): string | null`
  - Status 0 (network) → "Unable to connect to server..."
  - 403 → "You don't have permission to access this resource..."
  - 409 → "This action cannot be completed..."
  - 410 → "This link has expired..."
  - 5xx → "Something went wrong..."
  - 401 → null (handled separately)
- `handleHttpError()` function:
  - **401**: calls `authService.logout()` + `router.navigateByUrl('/')` 
  - **Others**: logs mapped message via `console.warn` or `console.error`
  - Always rethrows error for component-level handling
- `catchError` added to ALL request paths (token valid + token refresh paths)
- `Router` injected for 401 redirect

### OrgSettingsPage (ENHANCED)
- Injected `ErrorNotificationService`
- `console.error('[OrgSettingsPage]', ...)` added to all 4 error handlers with context
- Toast shown on member list load failure
- Inline error signals preserved (no UX regression)

### TasksPage (ENHANCED)
- Injected `ErrorNotificationService`
- `console.error('[TasksPage]', ...)` added to all error paths with context
- Toast shown on task save failure and network errors
- `MessageService` added to test providers

## Test Results

| Metric | Before | After |
|--------|--------|-------|
| Tests passing | 91/91 | 100/100 |
| New tests added | — | 9 (mapErrorToUserMessage) |
| Build errors | 0 | 0 |
| Bundle warning | pre-existing | pre-existing (unchanged) |

## Commits

| Commit | Description |
|--------|-------------|
| `6837a55` | feat(07-05): create ErrorNotificationService and global toast infrastructure |
| `dd42443` | refactor(07-05): implement error handling in authInterceptor |
| `f10a36a` | test(07-05): add mapErrorToUserMessage tests to auth interceptor spec |
| `0d6e309` | refactor(07-05): enhance error handling in OrgSettingsPage and TasksPage |
| `2ab6fe5` | docs(07-05): add error handling implementation documentation |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing MessageService in test providers**
- **Found during:** Task 9 (running tests)
- **Issue:** After adding `Toast` to `App` component and `ErrorNotificationService` to `TasksPage`, their test specs failed with `NG0201: No provider for MessageService`
- **Fix:** Added `MessageService` to providers in `app.spec.ts` and `tasks-page.component.spec.ts`
- **Files modified:** `app.spec.ts`, `tasks-page.component.spec.ts`
- **Commits:** `f10a36a` (spec file included), `0d6e309`

### Architectural Adjustments

**WorkingHoursPage unchanged**: The plan mentioned updating the add-worktime-dialog, but:
1. `AddWorktimeDialogComponent` only emits events (no direct API calls)
2. `WorkingHoursPage` already has comprehensive toast error handling via its own local `MessageService`
3. Changing it would require refactoring from local to global `MessageService` (risky, could affect existing behavior)
- **Decision:** Leave as-is. Documented in 07-05-ERROR-HANDLING.md.

**Interceptor does NOT call ErrorNotificationService**: The plan suggested using toast from interceptor, but:
- OrgSettingsPage already shows inline errors → would cause double-reporting
- Components handle their own error display appropriately
- Interceptor logs to console; components call `ErrorNotificationService` explicitly
- **Decision:** Interceptor = log only; Components = toast + inline (as appropriate)

## Known Stubs

None — all error handling is fully wired.

## Threat Flags

None — no new network endpoints, auth paths, or security-sensitive changes introduced.

## Self-Check: PASSED

Files created/verified:
- [x] `frontend/src/app/core/services/error-notification.service.ts` — FOUND
- [x] `.planning/phases/.../07-05-ERROR-HANDLING.md` — FOUND
- [x] `frontend/src/app/core/auth/auth.interceptor.ts` — MODIFIED
- [x] `frontend/src/app/app.config.ts` — MODIFIED (MessageService added)

Commits verified:
- [x] `6837a55` — FOUND (ErrorNotificationService)
- [x] `dd42443` — FOUND (authInterceptor)
- [x] `f10a36a` — FOUND (spec tests)
- [x] `0d6e309` — FOUND (components)
- [x] `2ab6fe5` — FOUND (docs)

Test results: 100/100 passing, 0 build errors ✅
