---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_plan: 07-03 (next)
status: in-progress
stopped_at: Plan 07-02 complete — OIDC Static Analysis & Bug Fixes
last_updated: "2026-05-04T00:00:00.000+02:00"
last_activity: 2026-05-04 -- Phase 07 Plan 07-02 complete (OIDC Verification & Config Bug Fixes)
progress:
  total_phases: 9
  completed_phases: 4
  total_plans: 22
  completed_plans: 20
  percent: 79
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-02)

**Core value:** Users authenticate through custom auth-service with simplified multi-org identity model — Zitadel fully replaced
**Current focus:** Phase 07 — Plan 07-02 COMPLETE ✅ — OIDC Verification & Config Bug Fixes done; plan 07-03 next

## Current Position

Phase: 7 (fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service) — IN PROGRESS
Status: Plan 07-02 complete, plan 07-03 pending
Last activity: 2026-05-04 -- Plan 07-02 complete (OIDC Verification & Config Bug Fixes)
Current Plan: 07-03 (next)

Progress: [▓▓▓▓▓▓▓▓░░] 79% (6/7 phases done, 1 pending)

## Performance Metrics

**Velocity:**

- Total plans completed: 18
- Average duration: 1 hour
- Total execution time: 18 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-auth-hardening | 1/1 | 1h | 1h |
| 02-backend-jwt-validation | 4/4 | 4h | 1h |
| 03-database-schema-migration | 3/3 | 3h | 1h |
| 03.1-refine-org-context | 3/3 | 3h | 1h |
| 04-frontend-auth-switch | 4/4 | 4h | 1h |
| 05-member-management-rewrite-and-cutover | 5/5 | 5h | 1h |
| 06.1-ci-cd-pipeline-update | 4/4 | 4h | 1h |

**Recent Trend:**

- Last 5 plans: 06.1-01 → 06.1-02 → 06.1-03 → 06.1-04
- Trend: Stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Phase 1: RSA Key Persistence: Filesystem + ENV (implemented).
- Phase 1: Client Registration: Seeding from ENV into JDBC (implemented).
- Phase 1: Multi-org token context strategy: Option B (`orgs` claim array) implemented.
- Phase 1: Database compatibility: Use `BYTEA` for binary data in H2/Postgres.
- Phase 4.2: Role extraction from `orgs` claim role field (simpler than flat authorities array)
- Phase 4.2: Per-org role mapping in getUserRoles() returns { [orgId]: [roles] } object
- Phase 4.2: Dialog/page-scoped org selection via OrgContextService with BehaviorSubject
- Phase 4.2: Backward compatibility: deprecated getUserOrganizationId() and getUserMemberships()
- Phase 5: PK unification complete — org.id and user.id ARE the auth-service UUIDs; no authCompanyId/authUserId fields
- Phase 5: Zitadel SDK fully removed — no io.github.zitadel dependency in pom.xml
- API client was already in sync with openapi.yaml — no file content changes after 07-01 regeneration
  - MemberResponse confirmed: userId (UUID), accountId (nullable), status (INVITED|ACTIVE), createdAt
- Phase 07-02: orgs[].role values from JWT are USER/COMPANY_ADMIN/SUPER_ADMIN (Role enum names)
- Phase 07-02: env.js.template fixed — now uses OIDC_CLIENT_ID/OIDC_ISSUER_URI matching docker-compose
- Phase 07-02: ClientSeedingRunner default aligned to goaldone-frontend (matches frontend defaults)
- Phase 07-02: docker-compose auth-service containers now forward FRONTEND_CLIENT_ID

### Roadmap Evolution

- Phase 03.1 inserted after Phase 03: Refine Organization Context and Header Requirements (URGENT)
- Phase 06.1 inserted after Phase 06: CI/CD Pipeline Update — Auth-Service Deployment, Postgres Independence, and GitHub Actions Build (URGENT)
- Phase 7 added: Fix the frontend to work with my new backend implementation that fully utilizes the auth-service

### Pending Todos

None blocking. Phase 5 complete.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-05-04T00:00:00.000Z
Stopped at: Plan 07-02 complete — OIDC Static Analysis & Config Bug Fixes
Resume file: None
