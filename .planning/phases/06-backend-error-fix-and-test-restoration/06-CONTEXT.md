# Phase 6: Backend Error Fix & Test Restoration - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase fixes all compilation errors in the backend and restores the test suite to achieve 100+ passing tests. The scope covers:

1. **Fix 5 compilation errors** preventing backend startup:
   - `MemberManagementController` missing `removeMember()` method override
   - Three other method override mismatches (`inviteMember`, `reinviteMember`, `changeMemberRole`)
   - `MemberResponse` no longer has `setZitadelUserId()` method (was removed during migration)

2. **Restore deleted tests** — 17 test files were deleted in commit `364b2af` ("remove outdated test files") because they referenced the old `UserAccountEntity`/`UserIdentityEntity` model. These must be adapted to the new `UserEntity`/`MembershipEntity` model.

3. **Write new auth-service integration tests** covering M2M client credentials, member operations via auth-service API, and role extraction from the new JWT format.

The result: Backend compiles, starts cleanly, and all tests (restored + new) pass.

</domain>

<decisions>
## Implementation Decisions

### Error Fix Strategy

- **D-01: Fix Errors First, Tests Second.** Complete all 5 compilation errors and verify the backend starts cleanly with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` BEFORE restoring tests. This provides early feedback that the migration is structurally sound.

- **D-02: Inspect Spec First, Validate Against Generated Interface.** When fixing `MemberManagementController` method signature errors:
  1. Read `api-spec/openapi.yaml` to understand what changed in the member management operations (e.g., userId parameter type changed from String to UUID in Phase 5)
  2. Run `./mvnw generate-sources` to refresh the `MemberManagementApi` generated interface
  3. Compare current controller implementation against the interface and align method signatures, parameter types, and return types
  4. Do not guess at fixes; use the spec and generated interface as the source of truth

### Test Restoration Strategy

- **D-03: Hybrid Resurrection + Fresh Writing.** 
  - **High-priority areas (resurrect from git + adapt to new model):**
    - JWT validation and role extraction (JitProvisioningServiceTest, CurrentUserResolverTest, security integration)
    - Member management operations (MemberManagementControllerIT, MemberManagementServiceTest, MemberInviteServiceTest)
    - Organization management (OrganizationManagementIntegrationTest)
  - **Lower-priority areas (write fresh or decide case-by-case):**
    - Account linking tests (secondary feature; decide if necessary)
    - Super admin tests (administrative feature; decide if necessary)
  - **New tests (write entirely fresh):**
    - M2M client credentials flow (token fetch, cache, refresh to auth-service)
    - Member operations via auth-service API (invite, role change, remove)
    - Role extraction from new auth-service JWT authorities claim
    - Integration acceptance flows (invited user joins org)
    - Last-admin guard error handling

- **D-04: Comprehensive Test Coverage Target.** Do NOT cap the test count at 100+. Restore all 17 deleted tests AND add comprehensive auth-service integration tests. Let the suite grow naturally to cover both legacy functionality and new integration points.

### Test Quality Standards

- **D-05: Use WireMock for Auth-Service Mocking.** Mock auth-service HTTP endpoints with WireMock (use existing `SharedWiremockSetup` pattern). Tests do not require a running auth-service instance.

- **D-06: Test Both Happy Path and Error Cases.** Every restored and new test covers:
  - Success scenarios (200, 201, etc.)
  - Error scenarios (4xx from auth-service, 5xx, network failures, race conditions)
  - Edge cases (last-admin guard, invited user already in org, invalid org membership)

- **D-07: Explicit `.authorities(...)` in JWT Helpers.** Important from Phase 2 memory: Custom `JwtAuthenticationConverter` is NOT auto-applied by MockMvc. All mock JWT tokens in tests must explicitly call `.authorities(...)` with the correct role set. Example:
  ```java
  jwt().authorities("COMPANY_ADMIN", "COMPANY_MEMBER")
  ```
  Never rely on implicit role extraction in test JWT setup.

- **D-08: Integration Tests with @SpringBootTest Pattern.** Follow existing test patterns:
  - Use `@SpringBootTest` for controller integration tests
  - Use `MockMvc` for HTTP simulation
  - Use `@ExtendWith(WireMockExtension.class)` for auth-service mocking
  - Use JUnit assertions and existing assertion style (AssertJ if present)

### Scope Boundaries

- **D-09: Phase 6 Does Not Expand Scope.** Phase 5 is complete. Phase 6 fixes errors and restores tests ONLY. No new features, no auth-service enhancements, no additional migrations.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 6 Scope & Requirements
- `.planning/ROADMAP.md` — Phase 6 goal and success criteria (backend starts cleanly, 100+ tests pass, no regressions)
- `.planning/REQUIREMENTS.md` — No new v1 requirements for Phase 6; focus is execution/restoration

### Prior Phase Decisions
- `.planning/phases/05-member-management-rewrite-and-cutover/05-CONTEXT.md` — M2M client credentials, UUID member IDs, auth-service API contracts, OpenAPI-first workflow
- `.planning/phases/04-frontend-auth-switch/04-CONTEXT.md` — JWT authorities claim format, role extraction patterns
- `memory/IT JWT Auth Pattern.md` — MockMvc JWT helper requires explicit `.authorities(...)`

### API Specification (Source of Truth)
- `api-spec/openapi.yaml` — Single source of truth for MemberManagementApi changes. Inspect first, then validate against generated interface.
- Backend generated interface: `backend/target/generated-sources/openapi/src/main/java/de/goaldone/backend/api/MemberManagementApi.java`

### Test Files to Resurrect
Git history reference (commit `364b2af^` has the deleted tests):
- `backend/src/test/java/de/goaldone/backend/service/JitProvisioningServiceTest.java` — JIT provisioning logic
- `backend/src/test/java/de/goaldone/backend/service/CurrentUserResolverTest.java` — User/org context resolution
- `backend/src/test/java/de/goaldone/backend/security/SecurityIntegrationTest.java` — JWT validation, role extraction
- `backend/src/test/java/de/goaldone/backend/controller/MemberManagementControllerIT.java` — Member operations via API
- `backend/src/test/java/de/goaldone/backend/service/MemberManagementServiceTest.java` — Member service logic
- `backend/src/test/java/de/goaldone/backend/service/MemberInviteServiceTest.java` — Invitation logic
- `backend/src/test/java/de/goaldone/backend/controller/OrganizationManagementIntegrationTest.java` — Organization CRUD and multi-org

### Existing Test Infrastructure
- `backend/src/test/java/de/goaldone/backend/SharedWiremockSetup.java` — WireMock extension for mocking HTTP; reuse this pattern
- `backend/src/test/java/de/goaldone/backend/scheduler/` — Existing test examples showing @SpringBootTest and assertion patterns (reference for style)

### Compilation Errors (Current State)
Run `./mvnw clean compile` to see:
1. `MemberManagementController` does not override abstract `removeMember(UUID, UUID)` from `MemberManagementApi`
2. `MemberManagementController.inviteMember` does not override method from `MemberManagementApi`
3. `MemberManagementController.reinviteMember` does not override method from `MemberManagementApi`
4. `MemberManagementController.changeMemberRole` does not override method from `MemberManagementApi`
5. `MemberResponse.setZitadelUserId(String)` — method does not exist (was removed in Phase 5)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **SharedWiremockSetup** (`backend/src/test/java/de/goaldone/backend/SharedWiremockSetup.java`) — Base for all auth-service HTTP mocking. Extend or reuse this for all new integration tests.
- **Existing test classes** (MoveSelectorTest, ChangeMoveTest, etc.) — Reference implementations showing @SpringBootTest, assertion patterns, and test organization.
- **MemberManagementController** — Controller structure is sound; only method signatures need alignment with the generated interface.

### Established Patterns
- **@SpringBootTest with MockMvc** — Standard pattern for controller integration tests. See existing scheduler tests.
- **WireMock for mocking external services** — Already in use. Extend `SharedWiremockSetup` for auth-service endpoints.
- **JWT helpers in tests** — Must explicitly include `.authorities(...)` due to custom JwtAuthenticationConverter not being auto-applied by MockMvc (Phase 2 learning).
- **Liquibase for schema changes** — Not in Phase 6 scope; schema is stable from Phase 5.

### Integration Points
- **JIT Provisioning (Phase 2)** — Tests must verify JIT logic works with new `UserEntity` and `MembershipEntity` models and new JWT claim format.
- **Member Operations** — Tests must verify backend calls to new auth-service management API endpoints via new `AuthServiceManagementClient`.
- **Role Extraction** — Tests must verify `authorities` claim (not old Zitadel URN) is correctly parsed from JWT and converted to Spring Security authorities.

</code_context>

<specifics>
## Specific Ideas

- **Commit Message Template:** When fixing errors, use commits like: `fix(06-XX): MemberManagementController method signature alignment` or `test(06-XX): restore and adapt JitProvisioningServiceTest for new UserEntity model`
- **Test File Naming:** Restored tests keep original names (e.g., `JitProvisioningServiceTest`). New auth-service integration tests use clear names like `MemberManagementAuthServiceIntegrationTest` or `AuthServiceM2MClientTest`.
- **Git History Checkout:** To inspect deleted tests, use: `git show 364b2af^:backend/src/test/java/de/goaldone/backend/service/JitProvisioningServiceTest.java` (no full checkout needed).
- **Build Verification:** After fixing errors, run `./mvnw clean compile` then `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` to verify startup.
- **Test Run Verification:** After restoring/writing tests, run `./mvnw test` and verify all tests pass with coverage output.

</specifics>

<deferred>
## Deferred Ideas

- **Phase 7+:** Further test expansion (performance tests, chaos engineering, load testing) — explicitly deferred.
- **Auth-service enhancements:** New features in auth-service (social login, 2FA, etc.) — out of scope for Phase 6; deferred to v2.
- **Documentation updates:** Updating CLAUDE.md or architecture docs to reflect new auth-service integration — can follow Phase 6 if needed.

</deferred>

---

*Phase: 06-backend-error-fix-and-test-restoration*
*Context gathered: 2026-05-03*
