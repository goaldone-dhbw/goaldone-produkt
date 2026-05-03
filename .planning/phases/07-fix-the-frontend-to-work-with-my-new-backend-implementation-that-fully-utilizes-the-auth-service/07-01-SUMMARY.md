---
phase: "07"
plan: "07-01"
subsystem: frontend
tags: [api-regeneration, openapi, typescript, member-management, audit]
dependency_graph:
  requires: ["06.1-04"]
  provides: ["07-02", "07-03", "07-04", "07-05"]
  affects: ["frontend/src/app/api/**"]
tech_stack:
  added: []
  patterns: ["openapi-generator-cli typescript-angular", "API shape audit"]
key_files:
  created:
    - ".planning/phases/07-fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service/07-01-API-REGEN.md"
  modified: []
decisions:
  - "API client was already in sync with openapi.yaml — no file content changes after regeneration"
  - "userId in MemberResponse is a UUID string from auth-service (not numeric)"
  - "accountId is nullable for INVITED members who haven't logged in yet"
  - "OrgSettingsPage has stub loadSettingsForOrg() — full wiring deferred to Plan 07-03"
  - "Deprecated methods getUserOrganizationId/getUserMemberships still present — removal in Plan 07-02"
metrics:
  duration: "20 minutes"
  completed: "2026-05-04"
  tasks_completed: 6
  files_changed: 1
---

# Phase 7 Plan 07-01: API Regeneration & Verification Summary

## One-liner

Regenerated TypeScript API client from openapi.yaml (no content change — already in sync), audited all component usages against new MemberResponse/AccountResponse shapes, and documented breaking changes for downstream plans.

## What Was Done

### Task 1: Regenerated API Client
Ran `npm run generate-api` from `frontend/` against `api-spec/openapi.yaml`. The generator completed successfully. No file content changes were detected — the committed API client was already up-to-date with the openapi.yaml spec (generated files are committed to git, not gitignored).

### Task 2: Compared Regenerated Shapes vs Current Usage
- Inspected `memberResponse.ts`, `memberRole.ts`, `memberStatus.ts`, `memberListResponse.ts`
- MemberResponse confirmed: `userId (UUID, required)`, `accountId (nullable)`, `email`, `firstName?`, `lastName?`, `role: MemberRole`, `status: MemberStatus`, `createdAt`
- Grepped all non-spec, non-api `.ts` files for `MemberResponse`, `userId`, `accountId` usage
- No component directly uses `MemberResponse` types yet (org-settings page is stubbed)
- `accountId` used in tasks and worktime components matches `AccountResponse.accountId` (UUID)

### Task 3: Audited Member Management API Endpoints
Confirmed 5 member management endpoints in the generated service:
- `inviteMember(xOrgID, InviteMemberRequest)` → `POST /organization/members/invite`
- `reinviteMember(xOrgID, userId)` → `POST /organization/members/{userId}/reinvite`
- `listMembers(xOrgID)` → `GET /organization/members`
- `changeMemberRole(xOrgID, userId, ChangeRoleRequest)` → `PUT /organization/members/{userId}/role`
- `removeMember(xOrgID, userId)` → `DELETE /organization/members/{userId}`

All operations use `X-Org-ID` header for org context. `userId` path param is always UUID.

### Task 4: Checked Other API Changes Beyond Member Management
- `TaskResponse`: optional fields, includes `dependencyIds?: Array<string>` — components correctly handle optional fields
- `WorkingTimeResponse`: includes `conflicting: boolean` — referenced in working-hours page
- `AccountResponse`: includes `hasConflicts: boolean`, `roles: Array<string>` — correctly used in tasks-page and working-hours
- No breaking changes found in Task or WorkingTime APIs relative to current component code

### Task 5: Identified All Breaking Changes
Documented in `07-01-API-REGEN.md`:
- `OrgSettingsPage.loadSettingsForOrg()` is a stub (console.log only) — needs wiring in Plan 07-03
- Deprecated methods `getUserOrganizationId()` and `getUserMemberships()` still exist in AuthService
- `TenantService` calls deprecated `getUserMemberships()` on lines 23 and 37
- No member management UI connected yet — invite, role change, remove all need implementation

### Task 6: Created Planning Document
Created `07-01-API-REGEN.md` with complete findings:
- All confirmed API shapes
- All 5 member management endpoint signatures
- Breaking changes checklist
- Components requiring updates (org-settings, tenant service, auth service)
- Test fixtures requiring updates (auth.service.spec.ts, tenant.service.spec.ts)
- Prioritized work list for Plans 07-02 through 07-05

## Deviations from Plan

None — plan executed exactly as written. The API client was pre-generated and committed to git (not gitignored as stated in context — this is actually correct behavior for this project).

## Key Decisions

1. **API client committed to git** — The API client files in `frontend/src/app/api/` are tracked in git (not gitignored). The `.gitignore` inside that directory only excludes `wwwroot/*.js`, `node_modules`, `typings`, and `dist`. Regeneration confirmed no content drift.

2. **No breaking changes to existing component code** — Components that use `AccountResponse`, `TaskResponse`, and `WorkingTimeResponse` are already aligned with current API shapes. The only breaking gap is the `OrgSettingsPage` which has a stub for member management.

3. **Member management UI is entirely unimplemented** — `OrgSettingsPage` has the org dropdown and context logic, but `loadSettingsForOrg()` is just a `console.log`. Plans 07-02 and 07-03 must implement the member list, invite, role change, and remove flows.

## Known Stubs

| Stub | File | Line | Reason |
|------|------|------|--------|
| `loadSettingsForOrg()` console.log | `features/org-settings/org-settings.page.ts` | 86 | Member management API not yet wired — implementation deferred to Plan 07-03 |

## Threat Flags

None — this plan creates only a planning artifact document with no new network endpoints or auth paths.

## Self-Check: PASSED

- `07-01-API-REGEN.md` exists: ✅
- Commit `ae7bfa0` exists: ✅
- No unexpected file deletions: ✅
