# Phase 6: Backend Error Fix & Test Restoration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 06-backend-error-fix-and-test-restoration
**Areas discussed:** Error fix order, test restoration strategy, error fix approach, test coverage target, high-priority tests, auth-service integration scope, test quality standards

---

## Error Fix vs Test Restoration Order

| Option | Description | Selected |
|--------|-------------|----------|
| Fix errors first, then tests (Recommended) | Complete all 5 compilation errors, verify the backend starts cleanly, then restore and rewrite tests. Advantage: faster feedback that backend is runnable; clear separation of concerns. Disadvantage: longer wait before verifying fixes work via tests. | ✓ |
| Interleave fixes and tests | Fix an error, write tests for it immediately, then move to next error. Advantage: each error gets test coverage as it's fixed; TDD-style validation. Disadvantage: slower path to 'backend starts', context-switching between error and test mindset. | |
| Fix critical errors first, others alongside tests | Triage: fix the 'backend won't start' blockers (MemberManagementController override errors), get it running, then restore tests while fixing remaining errors. Advantage: hybrid — early feedback that core system works, then comprehensive test coverage. | |

**User's choice:** Fix errors first, then tests
**Rationale:** Clear separation of concerns. Get the backend starting cleanly first, then tackle test restoration as a separate effort.

---

## Test Restoration Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Resurrect from git + refactor (Recommended) | Checkout the deleted test files from `364b2af^` (the commit before deletion), inspect what they tested, and rewrite them for UserEntity/MembershipEntity instead of the old model. Advantage: preserves test intent and coverage footprint; faster than starting blank. Disadvantage: requires manual refactoring for each. | |
| Write fresh tests from scratch | Ignore deleted tests; write new tests that work with current entity model, focusing on the new API contracts (UUID member IDs, X-Org-ID header, auth-service calls). Advantage: tests are modern and aligned with current code. Disadvantage: may miss coverage areas the old tests had; slower to reach 100+ tests. | |
| Hybrid: resurrect high-priority ones, write others fresh | Restore tests for critical paths (auth, member management, org operations) from history; write others from scratch for new features or areas that changed heavily. Advantage: balances speed and coverage. Disadvantage: requires judgment about which tests to resurrect. | ✓ |

**User's choice:** Hybrid — resurrect high-priority ones, write others fresh
**Rationale:** Balance between preserving test intent and adapting to new models.

---

## Error Fix Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Sync with auto-generated interface first (Recommended) | Run `./mvnw generate-sources` to refresh the generated `MemberManagementApi` interface, then compare it with the current controller implementation. Match method signatures and parameter types. Advantage: guarantees we're aligned with spec; fast and mechanical. Disadvantage: doesn't teach you what the spec actually changed. | |
| Inspect OpenAPI spec, understand changes, then fix | Read the changes in `api-spec/openapi.yaml` to `MemberManagementApi` operations (userId type change, new endpoint, parameter updates). Understand the 'why' before touching the controller. Advantage: you understand the API contract changes; makes fixes more intentional. Disadvantage: slower than auto-sync. | |
| Both: spec first, then validate against auto-generated interface | Read the spec to understand intent, generate sources to see the interface, then implement the controller carefully. Advantage: most thorough; you understand intent AND contract. Disadvantage: takes longer. | ✓ |

**User's choice:** Both — inspect spec first, then validate against auto-generated interface
**Rationale:** Understanding the spec's intent prevents mistakes and ensures we're fixing for the right reasons.

---

## Test Coverage Target

| Option | Description | Selected |
|--------|-------------|----------|
| 100+ tests, prioritize auth/JWT and member management | Aim to reach or exceed 100 total tests. Focus restoration on: (1) JWT validation and role extraction, (2) Member invite/reinvite/role change/remove operations, (3) Organization management. These are the migration's core changes. Advantage: covers highest-risk areas; 100+ is a clear target. Disadvantage: may under-invest in scheduler/appointment tests which are older and more stable. | |
| Restore all deleted tests + add new ones for auth-service integration | Resurrect all 17 deleted tests AND write new tests for auth-service M2M calls, token validation, etc. Let the count grow naturally. Advantage: maximum coverage; future-proofs against regressions. Disadvantage: could exceed 100+ significantly; slower to complete. | ✓ |
| Flexible target (80-120 tests), focus on critical path only | Don't aim for an exact number; restore tests that cover the critical path (auth, members, orgs, user accounts). Stop when critical areas are well-covered. Advantage: pragmatic; stops at 'good enough'. Disadvantage: less comprehensive than 100+. | |

**User's choice:** Restore all deleted tests + add new ones for auth-service integration
**Rationale:** Comprehensive coverage. No cap; let the suite grow naturally.

---

## High-Priority Tests (Hybrid Restoration)

| Option | Description | Selected |
|--------|-------------|----------|
| JWT & Auth tests | JIT provisioning, CurrentUserResolver, JWT validation, role extraction — foundational to everything | ✓ |
| Member management tests | MemberManagementControllerIT, MemberManagementServiceTest, MemberInviteServiceTest — core feature area | ✓ |
| Organization tests | OrganizationManagementIntegrationTest — org CRUD and multi-org authorization | ✓ |
| Super admin tests | SuperAdminIntegrationTest, SuperAdminServiceTest — admin features (invite/remove super admins) | ✓ |
| Account linking tests | AccountLinkingIntegrationTest, AccountLinkingServiceTest — account linking/unlinking (secondary feature) | ✓ |

**User's choice:** All of the above
**Rationale:** Comprehensive coverage. All deleted test files should be resurrected and adapted to new entity model.

---

## Auth-Service Integration Tests (Write Fresh)

| Option | Description | Selected |
|--------|-------------|----------|
| M2M client credentials flow | Test that backend authenticates to auth-service using OAuth2 client credentials (token fetch, caching, refresh) | ✓ |
| Member operations (invite/role/remove) | Test that backend calls auth-service to invite, change role, remove members (happy path + error cases) | ✓ |
| Member list retrieval | Test that backend fetches member list from new auth-service endpoint | ✓ |
| Invitation acceptance flow | Test flow from invited user accepting in auth-service → JIT provisioning creating local records | ✓ |
| Last-admin guard | Test that auth-service 409 error (last admin) is handled correctly and propagated to frontend | ✓ |
| Role extraction from new auth-service JWT | Test that backend extracts `authorities` claim correctly (not old Zitadel URN format) | ✓ |

**User's choice:** All of the above
**Rationale:** Comprehensive coverage of new auth-service integration points.

---

## Test Quality Standards

| Option | Description | Selected |
|--------|-------------|----------|
| Use WireMock for auth-service HTTP mocking | Mock auth-service endpoints with WireMock so tests don't need running auth-service. SharedWiremockSetup already exists. | ✓ |
| Test both happy path and error cases | Each feature: success case + error handling (4xx, 5xx from auth-service, network failure, etc.) | ✓ |
| Explicit .authorities(...) in JWT helpers | Remember: custom JwtAuthenticationConverter is NOT auto-applied in MockMvc; must explicitly add authorities to mock JWT | ✓ |
| Integration tests with @SpringBootTest | Use existing MockMvc + JWT helper pattern for controller integration tests | ✓ |

**User's choice:** All of the above
**Rationale:** Comprehensive test quality across all dimensions.

---

## Phase 5 Carryover

| Option | Description | Selected |
|--------|-------------|----------|
| No — Phase 5 is complete. Phase 6 focuses only on errors and tests. | Clean handoff; Phase 6 scope doesn't expand beyond error fix + test restoration | ✓ |
| Yes — note specific areas (type below) | If there are Phase 5 rough edges that Phase 6 should handle, tell me what they are | |

**User's choice:** Phase 5 is complete
**Rationale:** Clean scope boundary. Phase 6 does not inherit any unfinished Phase 5 work.

---

*Discussion completed: 2026-05-03*
