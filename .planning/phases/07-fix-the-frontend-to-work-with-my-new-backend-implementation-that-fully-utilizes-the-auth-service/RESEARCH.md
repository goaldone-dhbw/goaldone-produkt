# Phase 7 Research: Fix Frontend for Auth-Service Integration

**Research Date:** 2026-05-03  
**Status:** Complete analysis ready for planning

---

## 1. Implementation Approach

Phase 7 consists of five primary work streams that must be sequenced carefully:

### 1.1 API Client Regeneration (Prerequisite)
**Why First:** All downstream code changes depend on understanding the new API shapes from Phase 5.

1. Run `npm run generate-api` in frontend to regenerate from `api-spec/openapi.yaml`
2. Compare generated code in `frontend/src/app/api/` against current implementation
3. Identify breaking changes in:
   - Member management endpoint signatures (userId is now UUID, not string)
   - Response DTOs (MemberResponse.userId, MemberStatus enum)
   - Error responses (409, 403, 410 status codes documented)
4. Document all signature changes before proceeding to component fixes

**Key Findings from API Spec Review:**
- Member operations use UUID path parameters: `/organization/members/{userId}` where userId is UUID (not string)
- MemberResponse schema has:
  - `userId` (UUID) - Auth-service user ID
  - `accountId` (UUID, nullable) - Local account ID (null until invitation accepted)
  - `email`, `firstName`, `lastName` (optional)
  - `role` (MemberRole enum)
  - `status` (MemberStatus enum - ACTIVE or INVITED)
  - `createdAt` (timestamp)
- Member invite endpoint expects `InviteMemberRequest` DTO
- All endpoints require `X-Org-ID` header for org context

### 1.2 Auth Service Token Integration (Verification)
**Status:** Already implemented in auth.service.ts and auth.interceptor.ts, needs verification

Current auth service implementation:
- ✓ Reads `orgs` claim as array of `{id, slug, role}`
- ✓ Implements per-org role mapping via `getUserRoles()`
- ✓ Provides org context via `getActiveOrganization()`, `getOrganizations()`
- ✓ Deprecates old methods: `getUserOrganizationId()`, `getUserMemberships()`
- ✓ Token refresh: on-demand via `isTokenExpirySoon()` + 5-min buffer
- ✓ Revocation attempt on logout (best-effort)

**Verification Needed:**
1. End-to-end OIDC flow with live auth-service (env.js → discovery → token exchange → JWT decoding)
2. Token format validation: ensure `authorities`, `user_id`, `orgs` claims are correctly extracted
3. Multi-org provisioning: verify all orgs in JWT are loaded into OrgContextService
4. First login default: verify first org is selected automatically

### 1.3 Org Context Management (Verification & Enhancement)
**Status:** OrgContextService implemented, needs verification

Current implementation handles:
- ✓ Dialog-scoped org selection (cleared on close)
- ✓ Settings-page-scoped org selection (persists for page session)
- ✓ Default org resolution (first org from JWT)
- ✓ Multi-org detection via `hasMultipleOrgs()`
- ✓ Org context priority in authInterceptor: dialog > settings > default

**Verification Needed:**
1. Dialog org context lifecycle: cleared on ngOnInit, persists in session, cleared on ngOnDestroy
2. Settings org context lifecycle: persists across operations on page, cleared on navigation
3. Default org initialization: correctly set on first login from JWT orgs array

### 1.4 Component UI Updates (Member Management, Multi-Org Dialogs)
**Scope:** Update member management components to work with new API shapes and add org dropdowns

**Components to Update:**
1. **Member Management Dialog(s)** (if exists)
   - Invitation dialog: new invite flow
   - Role change dialog: update member role
   - Removal confirmation: remove member with error handling
   - API calls: match new UUID-based endpoints, handle 403/409/410 errors

2. **Add Task Dialog** (`add-task-dialog.component.ts`)
   - ✓ Already implements org dropdown (shows if multi-org)
   - ✓ Already manages dialog org context
   - ✓ Verify API call uses correct X-Org-ID header

3. **Add Worktime Dialog** (`add-worktime-dialog.component.ts`)
   - Add org dropdown (if not present)
   - Implement dialog org context management
   - Verify API call uses correct X-Org-ID header

4. **Company Settings Page** (if exists)
   - Add org dropdown for multi-org admins
   - Implement page-scoped org context
   - Update member list calls to use settings org context
   - Update admin operations to use settings org context

### 1.5 Frontend Test Updates (Critical Path)
**Scope:** Restore and enhance auth layer and component tests

**Auth Layer Tests (Already Good):**
- ✓ `auth.service.spec.ts`: Tests for token decoding, org role mapping, token refresh, token expiry
- ✓ `auth.interceptor.spec.ts`: Tests for Bearer token injection, X-Org-ID conditional header, org context priority

**Component Tests (Need Updates):**
1. All component specs that call API services
2. Mock new API shapes (UUID userIds, MemberResponse with status)
3. Test org context resolution in dialogs and settings pages
4. Test error scenarios (403, 410, 409 responses)

### 1.6 Error Handling & User Messaging (Final)
**Scope:** Implement user-friendly error handling in authInterceptor and services

**Error Mapping:**
- `403 FORBIDDEN` → "You don't have permission to access this organization. Contact your admin."
- `410 GONE` → "This link has expired. Please request a new one."
- `409 CONFLICT` → "This action cannot be completed. Please refresh and try again."
- `4xx/5xx Network errors` → "Something went wrong. Please try again or contact support."
- Network/timeout errors → "Unable to connect to server. Check your connection and try again."

**Implementation Locations:**
1. `authInterceptor`: Add error handler with toast notifications
2. Service error handlers: Catch and map status codes to user messages
3. Toast component: Use PrimeNG Toast for user notification

---

## 2. Risk Areas & Complexity Hotspots

### 2.1 API Client Breaking Changes
**Risk Level:** HIGH

**Why:** UUID-based member IDs vs previous string IDs will break component code if not aligned.

**Mitigations:**
1. Regenerate API client early (prerequisite step 1)
2. Systematically review all API call sites in components
3. Update type signatures before component refactoring
4. Run tests to catch mismatches early

**Unknown:** Whether Phase 5 changed other endpoint signatures beyond member management.

### 2.2 Token Format Verification
**Risk Level:** MEDIUM

**Why:** Auth-service token format (authorities, user_id, orgs) differs from Zitadel. Mismatch will break auth layer.

**Mitigations:**
1. Manual token inspection: decode real token from auth-service and verify claims
2. Unit tests already verify claim extraction (good coverage)
3. Integration test: end-to-end login flow with live auth-service
4. Error: Log claim extraction failures for debugging

### 2.3 Deprecated Method Refactoring
**Risk Level:** MEDIUM

**Why:** Code still uses `getUserOrganizationId()` and `getUserMemberships()` methods marked as deprecated.

**Mitigations:**
1. Audit: `grep -r "getUserOrganizationId\|getUserMemberships" frontend/src/`
2. Replace in all component files with `getActiveOrganization()` or `getOrganizations()`
3. Verify no references remain after refactor

**Impact:** Incomplete refactoring leads to dead code and future confusion.

### 2.4 Multi-Org UI Visibility Logic
**Risk Level:** MEDIUM

**Why:** Org dropdowns must appear/disappear based on user membership count. Logic must be consistent across dialogs and settings.

**Mitigations:**
1. Centralize visibility logic in OrgContextService (already done)
2. Test both single-org and multi-org scenarios
3. Verify UI state transitions on org count changes

### 2.5 Org Context Persistence Boundaries
**Risk Level:** LOW (but easy to get wrong)

**Why:** Dialog context must clear on close, settings context must persist across page. Incorrect lifecycle causes context leaks.

**Mitigations:**
1. Unit tests verify ngOnInit/ngOnDestroy behavior (good)
2. Manual test: open dialog → select org → close → verify context cleared
3. Manual test: settings page → select org → perform action → verify context still active → navigate → verify cleared

### 2.6 Error Handling Message Display
**Risk Level:** LOW

**Why:** Toast notifications must appear for errors. Incorrect error mapping or missing error handlers cause silent failures.

**Mitigations:**
1. Implement error handler in authInterceptor
2. Test each error code (403, 410, 409) with manual requests
3. Verify toast appears and user sees message

---

## 3. Task Patterns & Implementation Templates

### 3.1 API Client Regeneration
```bash
npm run generate-api
# Outputs: frontend/src/app/api/api/*.service.ts, frontend/src/app/api/model/*.ts
```

**Review Checklist:**
- [ ] Compare MemberResponse DTO structure
- [ ] Compare MemberManagement endpoint signatures
- [ ] Check for new/removed endpoints
- [ ] Verify parameter types (UUID vs string)

### 3.2 Component Test Update Pattern

```typescript
// Old pattern (Zitadel)
const mockMember: MemberResponse = {
  zitadelUserId: 'some-string-id',
  email: 'user@example.com',
  role: 'ADMIN'
};

// New pattern (Auth-Service)
const mockMember: MemberResponse = {
  userId: 'uuid-string-here', // UUID
  accountId: 'account-uuid-or-null',
  email: 'user@example.com',
  firstName: 'John',
  lastName: 'Doe',
  role: 'ADMIN',
  status: 'ACTIVE', // or 'INVITED'
  createdAt: new Date().toISOString()
};
```

### 3.3 Org Dropdown Implementation Pattern

```typescript
// In component.ts
readonly showOrgDropdown = signal(false);
readonly selectedOrg = new FormControl<string | null>(null);

ngOnInit(): void {
  this.orgContextService.clearDialogOrg(); // (D-09)
  this.showOrgDropdown.set(this.orgContextService.hasMultipleOrgs());
}

onOrgSelected(orgId: string): void {
  this.orgContextService.setDialogOrg(orgId); // Interceptor will pick this up
}

ngOnDestroy(): void {
  this.orgContextService.clearDialogOrg(); // Clean up on close
}
```

### 3.4 Error Handling Pattern (authInterceptor)

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // ... existing token refresh logic ...
  
  return proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next).pipe(
    catchError((error: HttpErrorResponse) => {
      const message = mapErrorToUserMessage(error.status);
      if (message) {
        // Show toast via injected ToastService or LoggerService
        logger.warn(message);
      }
      return throwError(() => error);
    })
  );
};

function mapErrorToUserMessage(status: number): string | null {
  switch (status) {
    case 403:
      return "You don't have permission to access this organization.";
    case 410:
      return "This link has expired. Please request a new one.";
    case 409:
      return "This action cannot be completed. Please refresh and try again.";
    case 0: // Network error
      return "Unable to connect to server. Check your connection.";
    default:
      return status >= 500 ? "Something went wrong. Please try again." : null;
  }
}
```

### 3.5 Multi-Org Test Pattern

```typescript
describe('Add Task Dialog - Multi-Org', () => {
  it('should show org dropdown if user has 2+ orgs', () => {
    // Setup
    const orgs = [
      { id: 'org-1', slug: 'org-one', role: 'ADMIN' },
      { id: 'org-2', slug: 'org-two', role: 'MEMBER' }
    ];
    authService.getOrganizations = vi.fn(() => orgs);
    
    // Execute
    component.ngOnInit();
    
    // Verify
    expect(component.showOrgDropdown()).toBe(true);
  });
  
  it('should hide org dropdown if user has 1 org', () => {
    const orgs = [{ id: 'org-1', slug: 'org-one', role: 'ADMIN' }];
    authService.getOrganizations = vi.fn(() => orgs);
    
    component.ngOnInit();
    
    expect(component.showOrgDropdown()).toBe(false);
  });
  
  it('should clear dialog org context on close', () => {
    orgContextService.setDialogOrg('selected-org-id');
    
    component.ngOnDestroy();
    
    expect(orgContextService.getDialogOrg()).toBeNull();
  });
});
```

---

## 4. Dependencies & Execution Ordering

**Critical Dependency Chain:**

```
1. API Client Regeneration
   ↓
2. Identify Breaking Changes in Member Management
   ↓
3. Verify Auth Service Token Integration (end-to-end test)
   ↓
4. Update Component Tests (mock new API shapes)
   ↓
5. Update Component Implementations
   ├─ Member management components
   ├─ Add task dialog (org dropdown)
   ├─ Add worktime dialog (org dropdown)
   └─ Company settings page (org context)
   ↓
6. Implement Error Handling (authInterceptor + services)
   ↓
7. Manual E2E Testing (all workflows)
   ↓
8. Run Full Test Suite (unit + integration)
```

**Parallelizable Tasks (after API regen):**
- Update component specs (tests are independent)
- Verify auth service token format (manual test)
- Implement error handling messages (independent of component updates)

**Sequential Must-Haves:**
1. API regen before any component code changes
2. Token format verification before E2E testing
3. Component test updates before component implementation
4. All unit tests passing before manual E2E

---

## 5. Known Unknowns & Planning Questions

### 5.1 API Spec Uncertainty
**Question:** Have all Phase 5 API changes been finalized in `api-spec/openapi.yaml`?

**Why:** Need to ensure no further breaking changes are coming that would invalidate regen work.

**Resolution:** Review Phase 5 ROADMAP.md + latest openapi.yaml before proceeding.

### 5.2 Member Management UI Components
**Question:** Where are the member management dialogs/pages currently implemented?

**Why:** Need to locate the invite, role-change, and member-removal components to update them.

**Current Finding:** Only found `MemberManagement.service.ts` in generated API. No component found yet.

**Resolution:** Search for:
- `inviteMember` usage in components
- `changeMemberRole` usage in components
- `removeMember` usage in components
- Company settings page component (if exists)

### 5.3 Auth-Service OIDC Issuer URL
**Question:** What is the correct auth-service OIDC issuer URL for local dev, staging, prod?

**Why:** Needed to populate `env.js` correctly for end-to-end testing.

**Current Code:** Defaults to `http://localhost:9000`

**Resolution:** Reference Phase 1 auth-service setup documentation or .env-dev-example

### 5.4 Error Handling UI Pattern
**Question:** Should errors display as toast notifications or inline messages in dialogs?

**Why:** Different UX patterns for different contexts (global errors vs dialog errors).

**Current Code:** No error handler implemented yet in authInterceptor.

**Resolution:** Discuss with product/design or use toast notifications as default (consistent with existing PrimeNG patterns).

### 5.5 Deprecated Method Audit Results
**Question:** How many files still call deprecated methods?

**Why:** Determines scope of refactoring work.

**Known:** Methods are marked @deprecated in auth.service.ts but actual usage unknown.

**Resolution:** Run audit: `grep -r "getUserOrganizationId\|getUserMemberships" frontend/src/`

---

## 6. Success Criteria & Verification Checklist

### 6.1 API Integration
- [ ] `npm run generate-api` completes without errors
- [ ] Generated API shapes match backend Phase 5 contracts
- [ ] Member operations use UUID userIds
- [ ] MemberResponse includes userId, accountId, status, email, firstName, lastName, role, createdAt

### 6.2 Auth Service Token Verification
- [ ] Manual login flow: env.js → auth-service discovery → token exchange
- [ ] Token decoding: `orgs` claim contains array of `{id, slug, role}`
- [ ] Org context: First org is selected as default after login
- [ ] Multi-org provisioning: All orgs from JWT are loaded into OrgContextService

### 6.3 Org Context Management
- [ ] Dialog org context clears on dialog close
- [ ] Settings org context persists across page operations
- [ ] Default org resolves correctly
- [ ] Org dropdown appears only for multi-org users

### 6.4 Component Updates
- [ ] Member management components use new UUID-based API
- [ ] Add task dialog has org dropdown (multi-org users)
- [ ] Add worktime dialog has org dropdown (multi-org users)
- [ ] Settings page has org dropdown for multi-org admins
- [ ] All org context selections are properly cleared on navigation

### 6.5 Error Handling
- [ ] 403 error displays: "You don't have permission to access this organization."
- [ ] 410 error displays: "This link has expired. Please request a new one."
- [ ] 409 error displays: "This action cannot be completed. Please refresh and try again."
- [ ] Network errors display generic message
- [ ] Errors are displayable as toast notifications

### 6.6 Test Coverage
- [ ] Auth service tests pass (token decoding, role extraction, org context)
- [ ] Auth interceptor tests pass (token injection, X-Org-ID header, org priority)
- [ ] All component tests pass (mocked new API shapes)
- [ ] Member management tests pass (UUID userIds, error scenarios)

### 6.7 Manual E2E Workflows
- [ ] Single-org user login → sees no org dropdown
- [ ] Multi-org user login → sees org dropdown in dialogs
- [ ] Multi-org user: create task with org selection → task created in selected org
- [ ] Multi-org user: switch settings page org → admin operations target correct org
- [ ] Member invite → user receives invitation email (verify auth-service integration)
- [ ] Member role change → role updates visible to user (on next login/token refresh)
- [ ] Member removal → member is removed from org
- [ ] Error scenarios: trigger 403, 410, 409 → user sees friendly error message

---

## 7. Estimated Complexity & Effort

### By Task Category

| Task | Complexity | Effort | Blockers |
|------|-----------|--------|----------|
| API Client Regen | Low | 0.5 hrs | None |
| Breaking Change Analysis | Medium | 1 hr | API regen complete |
| Auth Service Verification | Medium | 2 hrs | Live auth-service available |
| Org Context Verification | Low | 1 hr | None (code exists) |
| Member Management UI Update | High | 4 hrs | Component location identified |
| Add Task/Worktime Dialogs | Medium | 2 hrs | Org dropdown pattern clear |
| Settings Page Update | Medium | 2 hrs | Component exists |
| Test Updates (Auth Layer) | Low | 1 hr | Test patterns known |
| Test Updates (Components) | Medium | 3 hrs | API shapes finalized |
| Error Handling Implementation | Medium | 2 hrs | Error mapping clear |
| Manual E2E Testing | Medium | 3 hrs | All components updated |
| **Total** | | **21.5 hrs** | |

---

## 8. Entry Points & Next Steps

**Immediate Next Actions (for Planning):**

1. **Confirm API Spec Finality:** Review Phase 5 ROADMAP.md to confirm openapi.yaml is locked
2. **Locate Member Management Components:** Search codebase for member-related UI components
3. **Auth-Service Issuer URL:** Confirm correct issuer URL for dev/staging/prod in env.js
4. **Schedule Deprecated Method Audit:** `grep -r "getUserOrganizationId\|getUserMemberships" frontend/src/`

**Phase Execution Entry Point:**
1. Run `/gsd:execute-phase 07` with generated plan
2. Execute prerequisite: API client regeneration
3. Verify token format with live auth-service
4. Proceed with component updates in dependency order

---

*End of Research. Ready for Phase 7 Planning.*
