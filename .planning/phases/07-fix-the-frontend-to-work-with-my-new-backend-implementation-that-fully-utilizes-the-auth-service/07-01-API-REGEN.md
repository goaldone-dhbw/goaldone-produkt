# Phase 07-01 API Regeneration Findings

**Date:** 2026-05-04
**Status:** Complete

---

## Regenerated Files

`npm run generate-api` executed successfully from `frontend/` against `api-spec/openapi.yaml`.
No file content changes were detected — the committed API client was already in sync with openapi.yaml.

Key generated files inspected:

| File | Purpose |
|------|---------|
| `src/app/api/model/memberResponse.ts` | MemberResponse shape |
| `src/app/api/model/memberRole.ts` | MemberRole enum |
| `src/app/api/model/memberStatus.ts` | MemberStatus enum |
| `src/app/api/model/memberListResponse.ts` | MemberListResponse wrapper |
| `src/app/api/api/memberManagement.service.ts` | Member management API service |
| `src/app/api/model/accountResponse.ts` | AccountResponse shape |
| `src/app/api/model/taskResponse.ts` | TaskResponse shape |
| `src/app/api/model/workingTimeResponse.ts` | WorkingTimeResponse shape |

---

## Confirmed API Shapes

### MemberResponse (Confirmed)

```typescript
interface MemberResponse {
  userId: string;         // required — UUID from auth-service
  accountId?: string | null; // optional — local account ID, null until invitation accepted
  email: string;          // required
  firstName?: string | null;
  lastName?: string | null;
  role: MemberRole;       // required — USER | COMPANY_ADMIN
  status: MemberStatus;   // required — INVITED | ACTIVE
  createdAt: string;      // required — ISO timestamp
}
```

### MemberRole Enum

```typescript
type MemberRole = 'USER' | 'COMPANY_ADMIN';
```

### MemberStatus Enum

```typescript
type MemberStatus = 'INVITED' | 'ACTIVE';
```

### AccountResponse (Confirmed)

```typescript
interface AccountResponse {
  accountId: string;         // local account UUID
  organizationId: string;
  organizationName: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  roles: Array<string>;      // e.g. ['ROLE_USER', 'ROLE_ADMIN']
  hasConflicts: boolean;
}
```

### TaskResponse (Confirmed)

```typescript
interface TaskResponse {
  id?: string;
  title?: string;
  description?: string;
  duration?: number;
  deadline?: string;
  status?: TaskStatus;
  cognitiveLoad?: CognitiveLoad;
  dontScheduleBefore?: string;
  customChunkSize?: number;
  dependencyIds?: Array<string>;
}
```

### WorkingTimeResponse (Confirmed)

```typescript
interface WorkingTimeResponse {
  id: string;
  accountId: string;
  organizationId: string;
  days: Array<DayOfWeek>;
  startTime: string;          // HH:mm format
  endTime: string;            // HH:mm format
  createdAt: string;
  conflicting: boolean;
}
```

---

## Member Management Endpoint Signatures

All endpoints use `X-Org-ID` header (not path parameter) for organization context:

| Operation | Method | Path | Parameters |
|-----------|--------|------|------------|
| `inviteMember` | POST | `/organization/members/invite` | `xOrgID: string`, `InviteMemberRequest` |
| `reinviteMember` | POST | `/organization/members/{userId}/reinvite` | `xOrgID: string`, `userId: string (UUID)` |
| `listMembers` | GET | `/organization/members` | `xOrgID: string` |
| `changeMemberRole` | PUT | `/organization/members/{userId}/role` | `xOrgID: string`, `userId: string (UUID)`, `ChangeRoleRequest` |
| `removeMember` | DELETE | `/organization/members/{userId}` | `xOrgID: string`, `userId: string (UUID)` |

**Important:** Path param `userId` is a UUID from the auth-service (not a local numeric ID).

---

## Breaking Changes Identified

### Member Management (High Impact — Affects Phase 07-02+)

- [x] `userId` is a UUID string from auth-service (not a numeric integer)
- [x] `accountId` is nullable (null for INVITED users who haven't logged in yet)
- [x] `status` field exists: `INVITED | ACTIVE`
- [x] `firstName`, `lastName` are nullable (INVITED users may not have profile yet)
- [x] `createdAt` field exists (ISO timestamp)
- [x] No `orgId` on path — org is set via `X-Org-ID` header only
- [x] `reinviteMember` endpoint exists (was it always there? Added in Phase 5)

### Other Endpoints

- [x] `WorkingTimeResponse.conflicting` field — bool flag for overlapping schedules (used in working-hours page)
- [x] `AccountResponse.hasConflicts` — bool flag for conflicting working times
- [x] `TaskResponse` has optional `dependencyIds` array (new in Phase 5)
- [x] `TaskCreateRequest.accountId` — required for task creation, must be a valid UUID

---

## Components Requiring Updates

| Component | File | Issues |
|-----------|------|--------|
| `OrgSettingsPage` | `features/org-settings/org-settings.page.ts` | Stub implementation — `loadSettingsForOrg()` is a TODO console.log only; no `MemberManagementService` injection; member list table missing |
| `AuthService` (deprecated methods) | `core/auth/auth.service.ts` | `getUserOrganizationId()` and `getUserMemberships()` are deprecated but still exist; `TenantService` depends on `getUserMemberships()` |
| `TenantService` | `core/services/tenant.service.ts` | Calls deprecated `getUserMemberships()` on lines 23 and 37 |

**Note:** Tasks page, Working Hours page, and Add dialogs appear correctly aligned with the current API shapes — `accountId` fields match `AccountResponse.accountId` (UUID).

---

## Test Fixtures Requiring Updates

| Spec File | Issues |
|-----------|--------|
| `core/auth/auth.service.spec.ts` | Token format: mock uses plain `'test-token'`; getOrganizations() and getUserRoles() not tested with auth-service JWT format (`authorities`, `orgs` claims) |
| `core/auth/auth.interceptor.spec.ts` | Mock-based test; verify X-Org-ID conditional injection for `GET /organization/members` (member endpoint but GET should still include X-Org-ID per spec) |
| `core/services/tenant.service.spec.ts` | Calls deprecated `getUserMemberships()` — will need update when deprecated methods removed |
| `features/tasks/tasks-page.component.spec.ts` | Fixtures use `accountId: '8836327e-...'` (UUID format) — already correct |
| `features/working-hours/working-hours.page.spec.ts` | Check `WorkingTimeResponse` mock shapes match current interface |

---

## Summary: What Plans 07-02+ Must Address

### Plan 07-02 (OIDC Flow Verification)
- Verify `AuthService.getOrganizations()` returns proper data from JWT `orgs` claim
- Verify `getUserRoles()` extracts from `authorities` claim
- Replace `getUserOrganizationId()` / `getUserMemberships()` usage in `TenantService`
- Test auth service with auth-service JWT token format

### Plan 07-03 (Member Management UI)
- Wire up `OrgSettingsPage.loadSettingsForOrg()` to call `MemberManagementService.listMembers()`
- Inject `MemberManagementService` in `OrgSettingsPage`
- Render member table with `MemberResponse` fields (userId, email, firstName, lastName, role, status)
- Add invite member button → calls `inviteMember`
- Add reinvite button for INVITED status members
- Add change role functionality → calls `changeMemberRole`
- Add remove member → calls `removeMember`

### Plan 07-04 (Error Handling)
- Handle `403` (Forbidden), `409` (Conflict), `410` (Gone), `502` (Upstream) responses
- User-friendly messages via PrimeNG toast

### Plan 07-05 (Test Restoration)
- Update `auth.service.spec.ts` with auth-service JWT token format
- Update component specs with correct API mock shapes
- Remove deprecated method test dependencies
