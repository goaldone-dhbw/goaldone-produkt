# Phase 07-04 Component Updates

**Date:** 2026-05-04
**Status:** COMPLETE

## Components Updated

- [x] Org Settings Page (member list, invite, role change, removal)
- [x] Add Task Dialog (org dropdown — Select component fix)
- [x] Add Worktime Dialog (org dropdown — template variable fix)
- [x] User Settings Page (missing xOrgID parameter)

## API Calls Updated

| Component | API Call | Before | After |
|-----------|----------|--------|-------|
| OrgSettingsPage | `listMembers(xOrgID)` | console.log stub | real MemberManagementService call |
| OrgSettingsPage | `inviteMember(xOrgID, {email, firstName, lastName, role})` | not implemented | implemented with error handling |
| OrgSettingsPage | `changeMemberRole(xOrgID, userId, {role})` | not implemented | implemented with error handling |
| OrgSettingsPage | `removeMember(xOrgID, userId)` | not implemented | implemented with error handling |
| UserSettingsPage | `getMyAccounts(xOrgID)` | missing xOrgID arg (TS error) | passes `getDefaultOrg()?.id \|\| ''` |

## Deprecated Methods

| File | Old Method | Replacement |
|------|-----------|-------------|
| `tenant.service.ts` | `getUserMemberships()` | `getOrganizations()` (×2: computed signal + initializeActiveOrg) |
| All remaining | `getUserOrganizationId()`, `getUserMemberships()` | Still defined in auth.service.ts as deprecated wrappers — only callers fixed |

**Confirmed:** No non-deprecated callers of `getUserOrganizationId()` or `getUserMemberships()` remain in production code (only deprecated method bodies in auth.service.ts).

## PrimeNG Migration

| File | Before | After | Reason |
|------|--------|-------|--------|
| `org-settings.page.ts` | `import { Dropdown } from 'primeng/dropdown'` | `import { Select } from 'primeng/select'` | PrimeNG v19 renamed Dropdown→Select |
| `add-task-dialog.component.ts` | `import { Dropdown } from 'primeng/dropdown'` | `import { Select } from 'primeng/select'` | Same |
| `org-settings.page.html` | `<p-dropdown ...>` | `<p-select ...>` | Component renamed |
| `add-task-dialog.component.html` | `<p-dropdown [(ngModel)]>` | `<p-select [formControl]>` | Select+ReactiveFormsModule |
| `add-worktime-dialog.component.html` | `($event.target as X).value` | `#orgSelect (change)="onOrgSelected(orgSelect.value)"` | Angular templates don't support TS casts |

## Multi-Org Implementation

- [x] Dropdowns show for multi-org users/admins
- [x] Dialog context persists and clears correctly (clearDialogOrg on init and destroy)
- [x] Settings context persists and clears correctly (clearSettingsOrg on ngOnDestroy)
- [x] X-Org-ID header sent from dialog/settings org context via authInterceptor
- [x] OrgSettingsPage role filter fixed from `ROLE_ADMIN` → `COMPANY_ADMIN` (JWT format)

## Test Fixes (Deferred from 07-03)

| File | Failure Count | Root Cause | Fix Applied |
|------|--------------|-----------|-------------|
| `app.spec.ts` | 1 | No OAuthService mock in TestBed | Added OAuthService mock + provideHttpClient/Testing |
| `tenant.interceptor.spec.ts` | 3 | Functional interceptor via HTTP_INTERCEPTORS token | Changed to `provideHttpClient(withInterceptors([tenantInterceptor]))` + `provideHttpClientTesting()` |
| `tenant.service.spec.ts` | 6 | `new TenantService()` outside injection context; `getUserMemberships` mock | Changed to `TestBed.runInInjectionContext(() => new TenantService())`; updated mock to `getOrganizations` |

## Test Status

- Compilation: **PASS** (0 TypeScript errors)
- Test Results: **91/91 passing** (0 failing, up from 81/91)
- Ready for Plan 07-05: **YES**
