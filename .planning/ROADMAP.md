# Roadmap: Zitadel Migration → Custom Auth Service

## Overview

This milestone replaces Zitadel as the identity provider with the custom auth-service across five dependency-ordered phases. Phase 1 stabilizes the auth-service token contract so every downstream phase has a known, stable foundation. Phases 2-3 replace the backend's Zitadel coupling (JWT validation, JIT provisioning, DB schema). Phase 4 flips the frontend config. Phase 5 rewrites the member management layer and completes the cutover. No dual-IdP period is needed — this is a complete, atomic replacement.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Auth-Service Hardening** - Stabilize token contract: persistent RSA key, JDBC client registry, correct scopes/grants
- [ ] **Phase 2: Backend JWT Validation** - Rewire Spring Security resource server and JIT provisioner to auth-service claim shapes
- [ ] **Phase 3: Database Schema Migration** - Remove Zitadel columns via Liquibase; introduce auth_user_id and auth_company_id
- [ ] **Phase 03.1: Refine Organization Context and Header Requirements** (INSERTED)
- [ ] **Phase 4: Frontend Auth Switch** - Point Angular OIDC client at auth-service; update role and org extraction
- [ ] **Phase 5: Member Management Rewrite & Cutover** - Replace all Zitadel SDK calls with auth-service management API; final cleanup
- [ ] **Phase 6: Backend Error Fix & Test Restoration** - Fix all errors that hinder the backend from starting; restore 100+ tests removed by previous phases

## Phase Details

### Phase 1: Auth-Service Hardening
**Goal**: Auth-service emits stable, persistent JWTs with the correct claim shape that the backend and frontend can rely on
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, INFRA-04
**Success Criteria** (what must be TRUE):
  1. Auth-service can restart without invalidating existing tokens (RSA key persisted to file or env var; same `kid` across restarts)
  2. Angular frontend client is registered in auth-service DB with correct redirect URIs, `openid profile email offline_access` scopes, and `REFRESH_TOKEN` grant type
  3. JWKS endpoint (`/oauth2/jwks`) responds with the persisted public key and is cacheable
  4. Issued JWTs include `authorities`, `user_id`, `primary_email`, and `emails` claims with the correct values
  5. The multi-org token strategy decision is documented and implemented in `TokenCustomizerConfig` (e.g., `active_org_id` claim embedded)
**Plans**:
- [ ] phase-1/PLAN.md — Harden auth-service with persistent storage, keys, and claims

### Phase 2: Backend JWT Validation
**Goal**: Backend Spring Security validates auth-service tokens and correctly extracts roles and user identity for every request
**Depends on**: Phase 1
**Requirements**: JWT-01, JWT-02, JWT-03, JWT-04, JWT-05, JIT-01, JIT-02, JIT-03, JIT-04, DB-01, DB-02, DB-03, DB-05, DB-06, TEST-01, TEST-02
**Success Criteria** (what must be TRUE):
  1. Backend accepts a valid auth-service JWT and returns 200 on protected endpoints (no silent 403 from empty authorities)
  2. Role-based access control decisions pass for users with correct `authorities` claim values
  3. First login auto-provisions a `UserAccountEntity` from the auth-service `user_id` claim — no 500s, no silent failures
  4. Multi-org membership is handled: users with memberships in multiple organizations are provisioned correctly
  5. All integration tests pass using auth-service claim shapes and explicit `.authorities(...)` helpers
**Plans**: 4 plans
- [ ] 02-01-PLAN.md — Identifier Renaming (zitadel_sub -> auth_user_id)
- [ ] 02-02-PLAN.md — JWT Authentication & Claim Mapping (authorities + ROLE_ prefix)
- [ ] 02-03-PLAN.md — Bulk JIT Provisioning (multi-org 'orgs' claim)
- [ ] 02-04-PLAN.md — Multi-Org Authorization & Test Helpers

### Phase 3: Database Schema Migration
**Goal**: Finalize the database schema and entity model transition from Zitadel to auth-service model.
**Depends on**: Phase 2
**Requirements**: DB-01, DB-02, DB-03, DB-04, DB-05, DB-06
**Success Criteria** (what must be TRUE):
  1. Liquibase changesets run cleanly: Rename UserIdentityEntity -> UserEntity, UserAccountEntity -> MembershipEntity
  2. Normalize auth_user_id: Move it from memberships to users table
  3. User lookup by auth_user_id succeeds and is indexed for login latency
  4. CurrentUserResolver uses X-Org-ID header and auth_user_id from JWT
**Plans**: 4 plans
- [ ] 03-00-PLAN.md — OpenAPI Update (X-Org-ID header)
- [ ] 03-01-PLAN.md — Database Schema Migration (Liquibase)
- [ ] 03-02-PLAN.md — Entity and Repository Refactoring
- [ ] 03-03-PLAN.md — Context Resolution & Provisioning Update

### Phase 03.1: Refine Organization Context and Header Requirements (INSERTED)

**Goal:** Unify organization context delivery via X-Org-ID header; remove legacy path parameters; implement header-JWT correlation for per-org RBAC; provide frontend infrastructure for multi-org selection
**Requirements**: AUTHZ-01, AUTHZ-02, AUTHZ-03, DB-04, FE-06
**Depends on:** Phase 3
**Plans:** 3 plans
- [ ] 03.1-01-PLAN.md — Backend API & Security Infrastructure (OpenAPI, TenantContext, Custom Expressions)
- [ ] 03.1-02-PLAN.md — Backend Controllers/Services Refactoring & Integration Tests
- [ ] 03.1-03-PLAN.md — Database Schema Migration & Frontend Multi-Org Infrastructure

### Phase 4: Frontend Auth Switch
**Goal**: Angular frontend authenticates against auth-service end-to-end and correctly reads roles and org context from auth-service tokens
**Depends on**: Phase 2
**Requirements**: FE-01, FE-02, FE-03, FE-04, FE-05, FE-06, TEST-05
**Success Criteria** (what must be TRUE):
  1. User can log in via PKCE code flow against auth-service (OIDC discovery resolves, token exchange succeeds)
  2. Authenticated user stays logged in across tab refreshes (refresh token issued and consumed)
  3. `AuthService.getUserRoles()` returns roles from the `authorities` claim (not Zitadel URN keys)
  4. Role-gated UI elements render correctly for authenticated users with the appropriate role
**Plans**: 4 plans
- [x] 04-01-PLAN.md — OIDC Configuration & Token Lifecycle (issuer swap, env.js, silent refresh removal)
- [x] 04-02-PLAN.md — Role & Org Extraction (authorities claim mapping, OrgContextService)
- [x] 04-03-PLAN.md — Token Refresh & X-Org-ID Header Injection (authInterceptor enhancement)
- [ ] 04-04-PLAN.md — Multi-Org UI Components & End-to-End Testing (dialogs, settings, main page)
**UI hint**: yes

### Phase 5: Member Management Rewrite & Cutover
**Goal**: All member management operations run through auth-service management API; Zitadel SDK is fully removed; multi-org authorization is correct
**Depends on**: Phase 4
**Requirements**: MEM-01, MEM-02, MEM-03, MEM-04, MEM-05, MEM-06, MEM-07, MEM-08, AUTHZ-01, AUTHZ-02, AUTHZ-03, AUTHZ-04, AUTHZ-05, TEST-03, TEST-04, TEST-06, INFRA-01, INFRA-02, INFRA-03, INFRA-05
**Success Criteria** (what must be TRUE):
  1. COMPANY_ADMIN can invite a member to their organization via auth-service invitation API (invitation email sent)
  2. Invited user accepts invitation and is added to the organization with the specified role
  3. COMPANY_ADMIN can change a member's role and remove a member; last-admin guard blocks invalid removal
  4. Non-admin users receive 403 on all member management endpoints
  5. Users can access only the organizations they are members of; per-org role checks enforce data isolation
  6. Backend compiles and all tests pass with Zitadel SDK (`io.github.zitadel:client`) removed from `pom.xml`
**Plans**: 5 plans
- [ ] 05-01-PLAN.md — Auth-service management API completion (scope guard, bulk members, membership TODOs, InvitationRequest role)
- [ ] 05-02-PLAN.md — OpenAPI spec update + Backend M2M client infrastructure (AuthServiceClientConfig, AuthServiceManagementClient)
- [ ] 05-03-PLAN.md — Core service rewrites (MemberInviteService, MemberManagementService, MembershipDeletionService)
- [ ] 05-04-PLAN.md — Remaining service rewrites (OrganizationManagementService, SuperAdminService, UserService) + StartupValidator
- [ ] 05-05-PLAN.md — Liquibase PK unification + Entity updates + Zitadel SDK removal + Integration tests

### Phase 6: Backend Error Fix & Test Restoration
**Goal**: Fix all errors that hinder the backend from starting; restore the test suite from 38 to 100+ tests that were removed in previous phases
**Depends on**: Phase 5
**Requirements**: TBD
**Success Criteria** (what must be TRUE):
  1. Backend starts cleanly without errors: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` succeeds
  2. All compilation errors are resolved
  3. Test suite restored: 100+ tests (previously had 100+, currently 38)
  4. All tests pass: `./mvnw test` returns 0 exit code
  5. No regressions in frontend or infrastructure
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Auth-Service Hardening | 1/1 | In progress | - |
| 2. Backend JWT Validation | 4/4 | Not started | - |
| 3. Database Schema Migration | 4/4 | Not started | - |
| 03.1 Refine Organization Context and Header Requirements | 3/3 | Completed | 2026-05-03 |
| 4. Frontend Auth Switch | 4/4 | Not started | - |
| 5. Member Management Rewrite & Cutover | 0/? | Not started | - |
| 6. Backend Error Fix & Test Restoration | 0/? | Not started | - |
