# Roadmap: auth-service

## Phase 1: Foundation & Auth Server Core
**Goal:** Setup Spring Authorization Server skeleton, database schema (multi-email, memberships), and multi-email UserDetailsService.
**Plans:** 3 plans

Plans:
- [x] 01-foundation/01-01-PLAN.md — Setup project configuration and Liquibase schema.
- [x] 01-foundation/01-02-PLAN.md — Initialize Spring Authorization Server with JWKS and test client.
- [x] 01-foundation/01-03-PLAN.md — Implement multi-email UserDetailsService and JPA entities.

## Phase 2: Token Customization & Resource Server Integration
**Goal:** Implement custom JWT claims for Multi-Org support and bootstrap Super-Admin infrastructure.
**Plans:** 3 plans

Plans:
- [x] 02-token-customization-resource-server/02-01-PLAN.md — Schema updates (verified flag, company slug).
- [x] 02-token-customization-resource-server/02-02-PLAN.md — Super-Admin bootstrapping logic.
- [x] 02-token-customization-resource-server/02-03-PLAN.md — OAuth2TokenCustomizer for custom JWT claims.

## Phase 3: Management API
**Goal:** Administrative REST APIs for managing Users, Organizations, and Invitations.
**Plans:** 4 plans

Plans:
- [x] 03-management-api/03-01-PLAN.md — Configure M2M security and Invitation schema. (COMPLETED)
- [x] 03-management-api/03-02-PLAN.md — Implement User Management CRUD API. (COMPLETED)
- [x] 03-management-api/03-03-PLAN.md — Implement Organization Management CRUD API. (COMPLETED)
- [x] 03-management-api/03-04-PLAN.md — Implement Invitation Management API with business logic. (COMPLETED)

## Phase 4: Invitation & Reset Flows
**Goal:** Implement user-facing flows for joining organizations and account recovery with secure tokens and session management.
**Plans:** 4 plans

Plans:
- [x] 04-invitation-reset-flows/04-01-PLAN.md — Setup VerificationToken system and Spring Session JDBC. (COMPLETED)
- [x] 04-invitation-reset-flows/04-02-PLAN.md — Implement MailService and base UI styling. (COMPLETED)
- [x] 04-invitation-reset-flows/04-03-PLAN.md — Implement Invitation landing and acceptance flows. (COMPLETED)
- [x] 04-invitation-reset-flows/04-04-PLAN.md — Implement Password Reset flow with session invalidation. (COMPLETED)

## Phase 5: Advanced Features & Refinement
**Goal:** Polish and finalize all auth-service features with German language support and advanced account management.

Plans:
- [x] 05-advanced-features-refinement/05-01-PLAN.md — Account Linking Flow (independent linking with email confirmation). (COMPLETED)
- [x] 05-advanced-features-refinement/05-02-PLAN.md — Business Constraints (Last-Admin/Last-Super-Admin, domain restrictions). (COMPLETED)
- [x] 05-advanced-features-refinement/05-03-PLAN.md — Email Templates & German Language Support (14 tasks, completed 2026-05-01).
- [x] 05-advanced-features-refinement/05-04-PLAN.md — Invitation Management & Page Integration. (COMPLETED)
- [ ] 05-advanced-features-refinement/05-05-PLAN.md — (Optional) Domain-based Self-Registration (deferred to future milestone).

## Phase 6: Database Schema & Local Development Setup
**Goal:** Fix database schema validation errors and enable local development and testing without external Redis dependency.
**Plans:** 2 plans
**Status:** ✅ COMPLETED (2026-05-02) — All 2 plans verified, phase goal achieved

Plans:
- [x] 06-database-schema-and-local-dev/06-01-PLAN.md — Fix schema validation (missing acceptance_reason column) and apply migrations. (COMPLETED 2026-05-02)
- [x] 06-database-schema-and-local-dev/06-02-PLAN.md — Enable local development without Redis (H2 in-memory session storage). (COMPLETED 2026-05-02)

### Phase 7: Full integration test suite and deployment preparation

**Goal:** Deliver comprehensive integration test suite covering all critical OAuth/OIDC and user management flows, Docker containerization for production, and GitHub Actions CI/CD pipeline.
**Status:** ✅ COMPLETED (2026-05-02) — All 4 plans delivered and verified
**Depends on:** Phase 6
**Plans:** 4 plans

Plans:
- [x] 07-full-integration-test-suite-and-deployment-preparation/07-01-PLAN.md — Integration Test Infrastructure (TestContainers, base class, profiles). Wave 1.
- [x] 07-full-integration-test-suite-and-deployment-preparation/07-02-PLAN.md — Integration Test Coverage (OAuth, user/org/invitation/password/account/constraints). Wave 2.
- [x] 07-full-integration-test-suite-and-deployment-preparation/07-03-PLAN.md — Docker & Deployment (multi-stage Dockerfile, docker-compose, env config). Wave 2.
- [x] 07-full-integration-test-suite-and-deployment-preparation/07-04-PLAN.md — CI/CD Pipeline (GitHub Actions, coverage enforcement, image push). Wave 3.

### Phase 07.1: Fix all existing tests (INSERTED)

**Goal:** [Urgent work - to be planned]
**Requirements**: TBD
**Depends on:** Phase 7
**Plans:** 1/1 plans complete

Plans:
- [x] TBD (run /gsd:plan-phase 07.1 to break down) (completed 2026-05-02)

### Phase 8: Production logging and monitoring (docker logs with slf4j) and performance optimizations

**Goal:** [To be planned]
**Requirements**: TBD
**Depends on:** Phase 7
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 8 to break down)
