---
phase: "07"
plan: "04"
subsystem: "frontend-member-management"
tags: [member-management, angular, primeng, org-context, testing, api-client]
dependency-graph:
  requires: ["07-01", "07-02", "07-03"]
  provides: ["07-05"]
  affects:
    - "frontend/src/app/features/org-settings"
    - "frontend/src/app/features/tasks/add-task-dialog"
    - "frontend/src/app/features/working-hours/add-worktime-dialog"
    - "frontend/src/app/features/user-settings"
    - "frontend/src/app/core/services/tenant.service.ts"
    - "frontend/src/app/core/interceptors/tenant.interceptor.spec.ts"
    - "frontend/src/app/core/auth/auth.interceptor.ts"
    - "frontend/src/app/app.spec.ts"
tech-stack:
  added: []
  patterns:
    - "MemberManagementService API calls with xOrgID header"
    - "Angular Signals for reactive member list and UI state"
    - "Inline modal dialogs (role change, remove confirmation) using Angular signals"
    - "PrimeNG v19 Select component (renamed from Dropdown)"
    - "TestBed.runInInjectionContext for DI-dependent service instantiation in tests"
    - "provideHttpClient(withInterceptors) for functional interceptor testing"
key-files:
  created:
    - ".planning/phases/07-fix.../07-04-COMPONENT-UPDATES.md"
  modified:
    - "frontend/src/app/features/org-settings/org-settings.page.ts"
    - "frontend/src/app/features/org-settings/org-settings.page.html"
    - "frontend/src/app/features/tasks/add-task-dialog/add-task-dialog.component.ts"
    - "frontend/src/app/features/tasks/add-task-dialog/add-task-dialog.component.html"
    - "frontend/src/app/features/working-hours/add-worktime-dialog/add-worktime-dialog.component.html"
    - "frontend/src/app/features/user-settings/user-settings.page.ts"
    - "frontend/src/app/core/services/tenant.service.ts"
    - "frontend/src/app/core/services/tenant.service.spec.ts"
    - "frontend/src/app/core/interceptors/tenant.interceptor.spec.ts"
    - "frontend/src/app/core/auth/auth.interceptor.ts"
    - "frontend/src/app/app.spec.ts"
decisions:
  - "Role filter changed from ROLE_ADMIN to COMPANY_ADMIN — JWT orgs claim uses auth-service Role enum names"
  - "Inline modal dialogs (signal-based) preferred over separate dialog components for member operations"
  - "PrimeNG Select [formControl] binding preferred over [(ngModel)] since ReactiveFormsModule already imported"
  - "TestBed.runInInjectionContext used for testing DI services that call inject() in class field initializers"
  - "auth.interceptor.ts switchMap cast to any — pre-existing type mismatch, runtime behavior correct"
metrics:
  duration: "~90 minutes"
  completed: "2026-05-04"
  tasks_completed: 11
  files_modified: 11
---

# Phase 07 Plan 04: Member Management UI & Multi-Org Components — Summary

## One-Liner

Implemented full member CRUD UI in OrgSettingsPage (list/invite/role-change/remove via MemberManagementService), fixed PrimeNG v19 Dropdown→Select migration in 2 components, and fixed all 10 remaining test failures (91/91 passing, 0 TypeScript build errors).

## What Was Built

**Member management UI** in `OrgSettingsPage`:
- Real `listMembers(xOrgID)` API call replacing console.log stub
- Member table with name, email, role badge (USER/COMPANY_ADMIN), status badge (ACTIVE/INVITED), action buttons
- Inline invite form: firstName, lastName, email, role dropdown → `inviteMember(xOrgID, InviteMemberRequest)`
- Inline role-change modal: `changeMemberRole(xOrgID, userId, ChangeRoleRequest)`
- Inline remove-confirmation modal: `removeMember(xOrgID, userId)`
- All operations use `orgContextService.getSettingsOrg()` for X-Org-ID context
- Error handling per operation: 409 Conflict, 403 Forbidden, generic fallback

**Bug fixes** (pre-existing, discovered during verification):
1. OrgSettingsPage `ROLE_ADMIN` → `COMPANY_ADMIN` role filter (JWT format)
2. TenantService `getUserMemberships()` → `getOrganizations()` (deprecated method)
3. `primeng/dropdown` → `primeng/select` in org-settings + add-task-dialog (PrimeNG v19)
4. `<p-dropdown>` → `<p-select>` in HTML templates; `[(ngModel)]` → `[formControl]` binding
5. add-worktime-dialog HTML: template variable `#orgSelect` instead of TS cast
6. user-settings.page.ts: missing `xOrgID` arg on `getMyAccounts()`
7. auth.interceptor.ts: `switchMap` return type cast to fix `HttpEvent` mismatch

**Test fixes (3 deferred from Plan 07-03):**
- `app.spec.ts`: Added `OAuthService` mock + `provideHttpClient/Testing`
- `tenant.interceptor.spec.ts`: Changed to `provideHttpClient(withInterceptors([tenantInterceptor]))` + `provideHttpClientTesting()`
- `tenant.service.spec.ts`: `TestBed.runInInjectionContext(() => new TenantService())`; mock updated from `getUserMemberships` → `getOrganizations`

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| T1 | Locate and audit member management components | (analysis) |
| T2 | Update OrgSettingsPage member list + loadSettingsForOrg | `63a082b` |
| T3 | Implement invite member form (inline in OrgSettingsPage) | `63a082b` |
| T4 | Implement change role dialog (inline in OrgSettingsPage) | `63a082b` |
| T5 | Implement remove confirmation dialog (inline in OrgSettingsPage) | `63a082b` |
| T6 | Verify add-task-dialog org dropdown (Select migration) | `a1e1f32` |
| T7 | Verify add-worktime-dialog org dropdown (template var fix) | `a1e1f32` |
| T8 | Verify OrgSettingsPage org dropdown (role filter + Select) | `63a082b` |
| T9 | Audit for deprecated method usage (TenantService fix) | `2a5309b` |
| T10 | Compilation and tests verified | `a1e1f32` |
| T11 | 07-04-COMPONENT-UPDATES.md created | `fc99efa` |

## Deviations from Plan

### Auto-fixed Issues (RULE 1 - Bug)

**1. [Rule 1 - Bug] OrgSettingsPage filtered by wrong role value**
- **Found during:** Task T2 (implementing member list)
- **Issue:** `org.role === 'ROLE_ADMIN'` — JWT uses `COMPANY_ADMIN` not `ROLE_ADMIN`
- **Fix:** Changed filter to `org.role === 'COMPANY_ADMIN'`
- **Files:** `org-settings.page.ts`
- **Commit:** `63a082b`

**2. [Rule 1 - Bug] auth.interceptor.ts switchMap type mismatch**
- **Found during:** Task T10 (build verification)
- **Issue:** Pre-existing TS2345 — switchMap return typed as `unknown`, incompatible with `HttpEvent<any>`
- **Fix:** Added `as any` cast on the pipe return (runtime behavior unchanged)
- **Files:** `auth.interceptor.ts`
- **Commit:** `a1e1f32`

**3. [Rule 1 - Bug] add-worktime-dialog HTML TS cast not supported in templates**
- **Found during:** Task T10 (build verification)
- **Issue:** `($event.target as HTMLSelectElement).value` — Angular template compiler rejects TS casts
- **Fix:** Template variable `#orgSelect` with `(change)="onOrgSelected(orgSelect.value)"`
- **Files:** `add-worktime-dialog.component.html`
- **Commit:** `a1e1f32`

### Auto-fixed Issues (RULE 2 - Missing Functionality)

**4. [Rule 2 - Missing] user-settings.page.ts missing xOrgID arg**
- **Found during:** Task T10 (build verification)
- **Issue:** `getMyAccounts()` called without required `xOrgID` parameter (TS2554)
- **Fix:** Injected `OrgContextService`, pass `getDefaultOrg()?.id || ''`
- **Files:** `user-settings.page.ts`
- **Commit:** `a1e1f32`

### Auto-fixed Issues (RULE 3 - Blocking)

**5. [Rule 3 - Blocking] primeng/dropdown not found in PrimeNG v19**
- **Found during:** Task T10 (build failed)
- **Issue:** PrimeNG v19 renamed `Dropdown` → `Select`, `primeng/dropdown` package removed
- **Fix:** Changed imports to `import { Select } from 'primeng/select'` in 2 files; updated `<p-dropdown>` → `<p-select>` in HTML; changed `[(ngModel)]` to `[formControl]` for add-task-dialog
- **Files:** `org-settings.page.ts`, `add-task-dialog.component.ts`, `org-settings.page.html`, `add-task-dialog.component.html`
- **Commit:** `a1e1f32`

**6. [Rule 3 - Blocking] TenantService used deprecated getUserMemberships()**
- **Found during:** Task T9 (deprecated method audit)
- **Issue:** Two calls to `getUserMemberships()` in TenantService
- **Fix:** Replaced with `getOrganizations()`
- **Files:** `tenant.service.ts`
- **Commit:** `2a5309b`

## Key Technical Decisions

1. **Inline member dialogs:** Role change and remove confirmation implemented as inline conditional divs with `@if (roleChangeTarget())` / `@if (removeTarget())` signal guards — simpler than creating separate dialog components; no additional imports needed.

2. **PrimeNG Select [formControl]:** The add-task-dialog already imported `ReactiveFormsModule` but not `FormsModule`. `[formControl]` binding works with the existing module setup without adding `FormsModule`.

3. **TestBed.runInInjectionContext:** Angular 19 requires `inject()` to run within DI context. `TestBed.runInInjectionContext(() => new TenantService())` provides the context while still using the mocked providers registered in `TestBed.configureTestingModule`.

4. **provideHttpClient(withInterceptors):** The only correct way to register functional interceptors (`HttpInterceptorFn`) for testing in Angular 19. The old `HTTP_INTERCEPTORS` token only works with class-based interceptors.

## Test Results

```
Test Files:  13 passed | 0 failed (13 total)
Tests:       91 passed | 0 failed (91 total)
```

**Before Plan 07-04:** 81/91 passing (10 failures)
**After Plan 07-04:** 91/91 passing (+10 tests fixed, 100% green)

**Build:** `npm run build` passes with 0 TypeScript errors (1 bundle size warning — pre-existing, out of scope).

## Known Stubs

None — all member management operations call real API endpoints.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. Member management UI uses existing backend API endpoints already secured via JWT auth interceptor.

## Self-Check: PASSED

### Files verified to exist:
- [x] `frontend/src/app/features/org-settings/org-settings.page.ts` ✅
- [x] `frontend/src/app/features/org-settings/org-settings.page.html` ✅
- [x] `frontend/src/app/core/services/tenant.service.ts` ✅
- [x] `frontend/src/app/core/services/tenant.service.spec.ts` ✅
- [x] `frontend/src/app/core/interceptors/tenant.interceptor.spec.ts` ✅
- [x] `frontend/src/app/app.spec.ts` ✅
- [x] `07-04-COMPONENT-UPDATES.md` ✅

### Commits verified:
- [x] `2a5309b` fix(07-04): replace deprecated getUserMemberships with getOrganizations in TenantService ✅
- [x] `c25c6c9` test(07-04): fix 3 deferred test failures from Plan 07-03 ✅
- [x] `63a082b` feat(07-04): implement member management UI in OrgSettingsPage ✅
- [x] `a1e1f32` fix(07-04): fix pre-existing build errors blocking compilation ✅
- [x] `fc99efa` docs(07-04): create 07-04-COMPONENT-UPDATES.md ✅
