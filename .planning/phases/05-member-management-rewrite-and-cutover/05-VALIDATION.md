---
phase: 5
slug: member-management-rewrite-and-cutover
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-03
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + MockMvc |
| **Config file** | backend/pom.xml (surefire plugin) |
| **Quick run command** | `cd backend && ./mvnw test -pl . -Dtest=MemberManagement* -DfailIfNoTests=false` |
| **Full suite command** | `cd backend && ./mvnw test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./mvnw test -DfailIfNoTests=false`
- **After every plan wave:** Run `cd backend && ./mvnw test && cd ../auth-service && ./mvnw test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | MEM-01 | — | Invite via auth-service API | integration | `./mvnw test -Dtest=MemberInviteServiceTest` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | MEM-04 | — | Role change via auth-service | integration | `./mvnw test -Dtest=MemberManagementServiceTest#changeRole*` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 1 | MEM-05 | — | Remove member via auth-service | integration | `./mvnw test -Dtest=MemberManagementServiceTest#removeMember*` | ❌ W0 | ⬜ pending |
| 05-02-03 | 02 | 1 | MEM-07 | — | Last admin guard enforced by auth-service 409 | integration | `./mvnw test -Dtest=MemberManagementServiceTest#lastAdmin*` | ❌ W0 | ⬜ pending |
| 05-03-01 | 03 | 2 | MEM-08 | — | Member list returns active + pending | integration | `./mvnw test -Dtest=MemberManagementServiceTest#listMembers*` | ❌ W0 | ⬜ pending |
| 05-04-01 | 04 | 2 | AUTHZ-04 | — | COMPANY_ADMIN can manage members | MockMvc | `./mvnw test -Dtest=MemberManagementControllerTest` | ❌ W0 | ⬜ pending |
| 05-04-02 | 04 | 2 | AUTHZ-05 | — | Non-admin gets 403 | MockMvc | `./mvnw test -Dtest=MemberManagementControllerTest#unauthorized*` | ❌ W0 | ⬜ pending |
| 05-05-01 | 05 | 3 | INFRA-01 | — | Zitadel SDK removed, project compiles | compilation | `./mvnw compile` | ✅ (implicit) | ⬜ pending |
| 05-05-02 | 05 | 3 | TEST-06 | — | RBAC authorization tests pass | MockMvc | `./mvnw test -Dtest=*AuthorizationTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/de/goaldone/backend/service/MemberManagementServiceTest.java` — stubs for MEM-04, MEM-05, MEM-07, MEM-08
- [ ] `backend/src/test/java/de/goaldone/backend/service/MemberInviteServiceTest.java` — stubs for MEM-01, MEM-02
- [ ] `backend/src/test/java/de/goaldone/backend/controller/MemberManagementControllerTest.java` — stubs for AUTHZ-04, AUTHZ-05, TEST-06

*Existing infrastructure covers framework needs (JUnit 5 + Spring Boot Test already in pom.xml).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Invitation email sent | MEM-01 | Requires mail server or mock | Verify via mail trap or log output |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
