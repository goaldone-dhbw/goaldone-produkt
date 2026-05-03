---
phase: 8
slug: auth-service-login-ui-password-reset-accounts-endpoint-roles
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-03
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (backend/auth-service), Jest/Karma (frontend) |
| **Config file** | `backend/pom.xml`, `auth-service/pom.xml`, `frontend/jest.config.ts` |
| **Quick run command** | `cd auth-service && ./mvnw test -pl . -q` |
| **Full suite command** | `cd auth-service && ./mvnw test && cd ../backend && ./mvnw test && cd ../frontend && npm test -- --watchAll=false` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd auth-service && ./mvnw test -pl . -q`
- **After every plan wave:** Run full suite (auth-service + backend + frontend)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | D-09 | — | GET /me/organizations requires valid JWT | integration | `cd backend && ./mvnw test -Dtest=UserOrganizationsControllerTest -q` | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 1 | D-10 | — | Response contains `organizations` array with correct fields | integration | `cd backend && ./mvnw test -Dtest=UserOrganizationsControllerTest -q` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 1 | D-04 | — | POST /reset-password-request stores token, sends email | integration | `cd auth-service && ./mvnw test -Dtest=PasswordResetControllerTest -q` | ✅ | ⬜ pending |
| 08-02-02 | 02 | 1 | D-05 | — | Reset token expires after 1 hour | unit | `cd auth-service && ./mvnw test -Dtest=VerificationTokenServiceTest -q` | ✅ | ⬜ pending |
| 08-02-03 | 02 | 1 | D-06 | — | Used token cannot be reused | integration | `cd auth-service && ./mvnw test -Dtest=PasswordResetControllerTest -q` | ✅ | ⬜ pending |
| 08-03-01 | 03 | 2 | D-01 | — | Login page loads with GoalDone branding | manual | Browser test | N/A | ⬜ pending |
| 08-03-02 | 03 | 2 | D-01 | — | Password toggle shows/hides password field | manual | Browser test | N/A | ⬜ pending |
| 08-04-01 | 04 | 3 | D-11 | — | Frontend builds with 0 errors after API regen | automated | `cd frontend && npm run build -- --configuration=production` | N/A | ⬜ pending |
| 08-04-02 | 04 | 3 | D-11 | — | All frontend tests pass after endpoint rename | automated | `cd frontend && npm test -- --watchAll=false` | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/.../UserOrganizationsControllerTest.java` — stubs for D-09/D-10 (new `/me/organizations` path, `organizations` array response structure)
- [ ] Auth-service tests for token expiry (1h) already exist — verify/update `VerificationTokenServiceTest`

*Note: Auth-service has existing test infrastructure for password reset. Backend tests need Wave 0 stubs for new endpoint.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Login page renders GoalDone branding | D-01 | Visual/CSS verification | Open `http://localhost:9000/login` in browser; verify colors, fonts, logo |
| Password toggle works | D-01 | DOM interaction | Click eye icon; field type changes `password` ↔ `text` |
| Error messages display correctly | D-01 | Visual UX | Submit bad credentials; verify error message appears |
| Password reset email arrives | D-04 | SMTP integration | Request reset; check inbox for "Reset Your GoalDone Password" email |
| Reset link works end-to-end | D-05, D-06 | Full flow | Click reset link → new password → login succeeds |
| Expired token shows error | D-05 | Time-based | Wait >1h or manually expire token; verify error message |
| Reused token shows "already used" | D-06 | Token state | Use reset link twice; second attempt shows "Link already used" |
| Success redirect goes to success page | D-07 | UI flow | After reset, verify success page with "Return to Login" button |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
