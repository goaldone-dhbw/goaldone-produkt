# Phase 7: Fix the Frontend to Work with New Backend - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 07-fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service
**Areas discussed:** 7 (End-to-End Verification, Login Flow, Member Management, Multi-Org UI, API Alignment, Frontend Tests, Error Handling)

---

## Area 1: End-to-End Verification Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Manual walkthrough (Recommended) | User manually tests core workflows end-to-end in the browser (login → create task → invite member → switch org). Documents what works/breaks. | ✓ |
| Automated E2E tests | Write Cypress/Playwright tests that execute full user flows programmatically. Catches regressions automatically. | ✓ |
| Frontend unit/integration tests | Update existing Angular spec files to test auth service, interceptor, and component integration with new API shapes. | ✓ |
| Backend integration tests (already done) | Phase 6 restored backend tests. Frontend just needs to trust the backend API contracts. | |

**User's choice:** Manual walkthrough + Frontend unit/integration tests (both selected)
**Notes:** User opted for both manual testing and automated test updates. No separate Cypress/Playwright E2E suite — focus on manual verification + unit tests.

---

## Area 2: Login Flow & Token Lifecycle

| Option | Description | Selected |
|--------|-------------|----------|
| Re-verify end-to-end (Recommended) | Phase 7 manually tests: env.js config load → OIDC discovery → login redirect → token exchange → JWT decode → multi-org provisioning → backend access. Catches integration breaks. | ✓ |
| Trust Phase 4 testing | Phase 4 implemented and tested OIDC. Phase 7 assumes it works and focuses on downstream issues (API mismatches, UI alignment). | |
| Test only token refresh/expiry | Phase 4 covered initial login. Phase 7 focuses on the on-demand refresh logic in authInterceptor (edge case: token expires during request). | |

**User's choice:** Re-verify end-to-end (Recommended)
**Notes:** User wants comprehensive OIDC flow verification because Phase 7 is first time frontend meets the live backend. Full chain verification ensures integration works.

---

## Area 3: Member Management UI Alignment

| Option | Description | Selected |
|--------|-------------|----------|
| Full scope (Recommended) | Fix/verify all member management flows: invitation dialog → send → accept workflow; role change dialog → update; member removal with last-admin guard. Ensure all match new Phase 5 API signatures. | ✓ |
| Verification only | Manually test existing UI components against Phase 5 APIs. Document what breaks. Phase 7 documents the breaks; actual UI fixes deferred to next phase if needed. | |
| No member management work | Phase 5 completed backend. Phase 7 focuses on other frontend issues. Member UI fixes are out of scope. | |

**User's choice:** Full scope (Recommended)
**Notes:** User wants comprehensive member management integration. Phase 5 rewrote the backend; Phase 7 ensures frontend fully aligns.

---

## Area 4: Multi-Org UI Coverage

| Option | Description | Selected |
|--------|-------------|----------|
| Full implementation (Recommended) | Verify org dropdowns appear in: Add Task dialog, Add Worktime dialog, Company Settings (if multi-org admin). Verify org context persists/clears appropriately. Check for deprecated method usage (getUserOrganizationId, getUserMemberships). | ✓ |
| Critical paths only | Test only the most common multi-org workflows (e.g., switching org in Settings, creating task in primary org). Skip edge cases like dropdown appearance rules. | |
| Design review only | Phase 7 reviews where org dropdowns SHOULD appear based on Phase 4 decisions, but doesn't implement/fix them. Documents what needs fixing. | |

**User's choice:** Full implementation (Recommended)
**Notes:** User wants complete multi-org UI implementation including deprecated method refactoring.

---

## Area 5: API Signature Misalignment

| Option | Description | Selected |
|--------|-------------|----------|
| Proactive scan first (Recommended) | Before manual testing: Run 'npm run generate-api' to regenerate the TypeScript API client from openapi.yaml. Compare generated shapes against current usage in components. Fix all mismatches found. | ✓ |
| Reactive (test-driven) | Manual test the app. When something breaks (404, type error, response shape mismatch), fix it. No upfront scanning. | |
| Code review only | Grep for API service calls in components. Inspect them against openapi.yaml manually. Document mismatches. Defer fixes if acceptable. | |

**User's choice:** Proactive scan first (Recommended)
**Notes:** User wants systematic API alignment starting with API client regeneration. Ensures no surprises during testing.

---

## Area 6: Frontend Test Coverage

| Option | Description | Selected |
|--------|-------------|----------|
| Comprehensive (Recommended) | Update all auth-related spec files (auth.service.spec.ts, auth.interceptor.spec.ts) to test with auth-service token format. Update component specs to mock new API shapes. Target: all tests pass, reasonable coverage. | ✓ |
| Auth layer only | Focus on core auth tests (auth.service.spec.ts, auth.interceptor.spec.ts). Defer component/integration test updates to later phases. | |
| No test work | Phase 7 does manual verification. Test updates are lower priority. Can be deferred or picked up in a later bug-fix phase. | |

**User's choice:** Comprehensive (Recommended)
**Notes:** User wants all frontend tests updated and passing. This ensures quality and prevents regressions.

---

## Area 7: Error Handling & Edge Cases

| Option | Description | Selected |
|--------|-------------|----------|
| Implement error handling (Recommended) | Add user-facing error messages for common failures: 403 (no org access), 410 (link expired), 409 (conflict), network errors. Update interceptor/services to handle these gracefully. | ✓ |
| Document error scenarios | During manual testing, document what errors occur and what users see. Create issues for error handling improvements. Don't implement fixes in Phase 7. | |
| Use backend error contracts | Trust that Phase 6 backend errors are correct (RFC 7807 Problem Details). Frontend just displays them generically. No special per-error-type handling. | |

**User's choice:** Implement error handling (Recommended)
**Notes:** User wants proactive error handling with user-friendly messages for common auth/org failures.

---

## Summary of Phase 7 Scope

**What will be done:**
1. ✅ Manual end-to-end testing of core workflows (login, task creation, member management, multi-org operations)
2. ✅ Complete OIDC flow re-verification (env.js → auth-service → backend)
3. ✅ Member management UI fixes (invitations, role changes, removal)
4. ✅ Multi-org UI full implementation (org dropdowns, context management, deprecated method refactoring)
5. ✅ Proactive API client regeneration and signature alignment
6. ✅ Comprehensive frontend test updates (auth layer + component layer)
7. ✅ Error handling implementation with user-friendly messages

**What is not in scope:**
- Social login, 2FA, new frontend features
- Performance optimization, UI redesign
- Cypress/Playwright E2E test suite (manual + unit tests sufficient)

---

*Discussion gathered: 2026-05-03*
