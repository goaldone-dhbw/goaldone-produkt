# Phase 7 Planning Index

## Overview
Phase 7 breaks down into 6 sequential plans that integrate the frontend with the auth-service-based backend. Each plan has clear success criteria, concrete tasks, and atomic commit points.

**Total Duration:** Estimated 8-12 hours (experienced developer)

## Plan Sequence & Dependencies

### Wave 1: Prerequisites & Verification (Plans 07-01, 07-02)
Required before any component changes.

**Plan 07-01: API Regeneration & Verification** (0.5-1 hour)
- **Goal:** Regenerate TypeScript API client, identify breaking changes
- **Key Output:** 07-01-API-REGEN.md (breaking changes documented)
- **Success:** Member operations use UUID userIds, MemberResponse shape identified
- **Blockers:** None (can start immediately)

**Plan 07-02: Auth Service Token Integration & OIDC Verification** (2-3 hours)
- **Goal:** End-to-end verification of OIDC login flow, token format, JWT decoding
- **Key Output:** 07-02-OIDC-VERIFICATION.md (OIDC flow verified)
- **Success:** Login works, JWT claims validated, multi-org provisioning working
- **Blockers:** Plan 07-01 complete, auth-service running

---

### Wave 2: Test Infrastructure (Plan 07-03)
Required before component implementation.

**Plan 07-03: Frontend Component Test Updates** (3-4 hours)
- **Goal:** Update test fixtures for new API shapes, prepare component tests
- **Key Output:** 07-03-TEST-UPDATES.md (test infrastructure ready)
- **Success:** All auth layer tests pass, component test mocks updated for new API shapes
- **Blockers:** Plan 07-01 complete (breaking changes documented)

---

### Wave 3: Implementation (Plans 07-04, 07-05)
Can start after Wave 2 complete. Plans 07-04 and 07-05 can proceed in parallel.

**Plan 07-04: Member Management UI & Multi-Org Component Implementation** (4-6 hours)
- **Goal:** Update member management components, implement org dropdowns
- **Key Output:** 07-04-COMPONENT-UPDATES.md (components updated)
- **Success:** Member operations work, org dropdowns visible, no deprecated methods
- **Blockers:** Plan 07-03 complete (tests updated)
- **Can parallel with:** Plan 07-05 (error handling)

**Plan 07-05: Error Handling & User-Friendly Messaging** (2-3 hours)
- **Goal:** Implement error handling across auth layer and components
- **Key Output:** 07-05-ERROR-HANDLING.md (error handling complete)
- **Success:** User-friendly messages for 403, 410, 409, network errors
- **Blockers:** Plan 07-03 complete (test stubs prepared)
- **Can parallel with:** Plan 07-04 (component updates)

---

### Wave 4: Validation (Plan 07-06)
Last phase; required to verify all changes work end-to-end.

**Plan 07-06: Manual E2E Testing & Final Validation** (2-3 hours)
- **Goal:** Manual verification of all core workflows
- **Key Output:** 07-06-E2E-CHECKLIST.md, SUMMARY.md (deployment ready)
- **Success:** All workflows pass, tests passing, build succeeds
- **Blockers:** Plans 07-04 and 07-05 complete (implementation done)

---

## Critical Path Summary

```
07-01 (1h) → 07-02 (3h) → 07-03 (4h) → 07-04 & 07-05 (5-9h parallel) → 07-06 (3h)
Total: 8-12 hours minimum (sequential)
Parallel opportunity: 07-04 and 07-05 can run concurrently (-2 to -4 hours)
```

## Success Criteria (Phase-Level)

All 6 plans must complete to achieve Phase 7 success:

1. **API Integration (Plans 07-01, 07-03, 07-04)**
   - API client regenerated, breaking changes understood
   - Test mocks updated for new API shapes
   - Components use UUID userIds, MemberResponse correctly
   - No compilation errors

2. **Auth & Token Integration (Plans 07-02, 07-03, 07-05)**
   - OIDC flow verified end-to-end
   - Token format validated (authorities, user_id, orgs claims)
   - X-Org-ID header injected correctly
   - Token refresh works with 5-minute buffer

3. **Multi-Org Implementation (Plans 07-04)**
   - Org dropdowns visible for multi-org users/admins
   - Dialog org context clears on close
   - Settings org context persists and clears correctly
   - All org context managed via OrgContextService

4. **Member Management (Plans 07-04, 07-05)**
   - Invite, role change, removal operations working
   - All operations use new UUID-based API
   - Error handling for 403, 409 responses
   - User-friendly error messages displayed

5. **Error Handling (Plan 07-05)**
   - 403 FORBIDDEN: permission error message
   - 410 GONE: link expired message
   - 409 CONFLICT: action conflict message
   - Network errors: connection error message
   - All errors shown via PrimeNG Toast

6. **Testing & Validation (Plans 07-03, 07-06)**
   - Unit tests pass (or near-complete with known failures)
   - Build succeeds without TypeScript errors
   - Manual E2E testing verifies all workflows
   - All success criteria met from Phase 7 CONTEXT.md

## Files to Review Before Planning Execution

1. **07-CONTEXT.md** — Phase 7 goals, 9 implementation decisions (D-01 through D-09), success criteria
2. **07-DISCUSSION-LOG.md** — Decision outcomes, all areas selected for full scope
3. **RESEARCH.md** — Research findings, 6 work streams, dependencies, complexity estimates
4. **ROADMAP.md** — Phase 7 in milestone context, dependencies on Phase 6.1

## Key Assumptions & Dependencies

**External Assumptions:**
- Auth-service is running and accessible locally (http://localhost:9000)
- Backend is running with Phase 5 implementation complete
- openapi.yaml is finalized (no breaking changes coming)

**Technical Assumptions:**
- MemberResponse structure finalized with userId (UUID), accountId, status fields
- X-Org-ID header mechanism implemented in authInterceptor
- OrgContextService exists and manages dialog/settings/default org context
- PrimeNG Toast/MessageService available for error notifications

**Knowledge Assumptions:**
- Developer familiar with Angular signals, RxJS, PrimeNG
- Understanding of OAuth2/OIDC flows and JWT claims
- Experience with TypeScript type generation from OpenAPI specs

## Risk Areas & Mitigation

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Hidden API changes beyond member management | HIGH | Plan 07-01 performs thorough audit |
| Token format mismatch with auth-service | MEDIUM | Plan 07-02 includes manual verification |
| Component tests reveal breaking changes | MEDIUM | Plan 07-03 updates mocks before implementation |
| Deprecated methods still in use | LOW | Plan 07-04 includes grep audit |
| Error handling incomplete | LOW | Plan 07-05 covers all identified error codes |

## Execution Checklist

Before starting execution:

- [ ] Read 07-CONTEXT.md for complete phase goals
- [ ] Review RESEARCH.md for work streams and dependencies
- [ ] Verify auth-service is running (http://localhost:9000 accessible)
- [ ] Verify backend is running (API calls succeed)
- [ ] Ensure frontend can start: `npm start` works
- [ ] Run `npm run test:ci` to establish baseline
- [ ] Run `npm run build` to confirm compilation works

## Running the Plans

All plans are designed to be executed via `/gsd:execute-phase 7` or individually:

```bash
# Execute all plans in sequence
/gsd:execute-phase 7

# Or execute individual plans
/gsd:execute-phase 7 --plan 07-01
/gsd:execute-phase 7 --plan 07-02
# etc.
```

Each plan includes:
- Clear step-by-step tasks (suitable for CLI copy-paste)
- Git commit messages (copy-paste ready)
- Verification steps (testable outcomes)
- Success/failure criteria (objective pass/fail)

## Post-Execution

After all 6 plans complete:

1. **Review Phase 7 Summary** (in 07-06, Task 14)
   - All workflows verified
   - Test results captured
   - Deployment readiness assessed

2. **Create Phase 7 Complete Commit**
   ```bash
   git add .planning/phases/07-*/
   git commit -m "docs(07): phase 7 complete - frontend fully integrated with auth-service"
   ```

3. **Next Steps**
   - Deploy to staging environment
   - Run smoke tests in staging
   - Schedule production deployment
   - Create post-Phase 7 backlog items (if any outstanding issues)

---

**Phase 7 Planning Complete**  
Created: 2026-05-03  
Total Plans: 6  
Total Estimated Duration: 8-12 hours  
Critical Path: Sequential (Wave 1 → 2 → 3 → 4)

