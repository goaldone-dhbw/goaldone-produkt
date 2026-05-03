---
phase: "08"
plan: "03"
subsystem: auth-service
status: COMPLETE
tags: [password-reset, auth-service, bug-fix, email-templates, thymeleaf]
dependency_graph:
  requires: []
  provides:
    - "PASSWORD_RESET tokens expire in 1 hour (D-05)"
    - "GET /reset-success public endpoint (D-07)"
    - "English reset email with GoalDone branding (D-08)"
    - "CSRF NPE fix in reset-password.html"
    - "Non-consuming checkToken on GET /reset-password"
  affects:
    - auth-service password reset flow
    - verification token expiry logic
    - email template rendering
tech_stack:
  added: []
  patterns:
    - "Per-type token expiry: getExpiryHoursForType(TokenType) dispatch"
    - "Non-consuming token check: checkToken() for GET, validateToken() for POST"
    - "Thymeleaf plain text: [(${var})] interpolation syntax"
key_files:
  created:
    - auth-service/src/main/resources/templates/auth/reset-success.html
  modified:
    - auth-service/src/main/java/de/goaldone/authservice/service/VerificationTokenService.java
    - auth-service/src/main/java/de/goaldone/authservice/controller/PasswordResetController.java
    - auth-service/src/main/resources/application.yaml
    - auth-service/src/main/java/de/goaldone/authservice/config/DefaultSecurityConfig.java
    - auth-service/src/main/java/de/goaldone/authservice/service/SmtpMailService.java
    - auth-service/src/main/resources/templates/mail/password-reset.html
    - auth-service/src/main/resources/templates/mail/password-reset.txt
    - auth-service/src/main/resources/templates/auth/reset-password.html
    - auth-service/src/main/resources/templates/auth/forgot-password.html
    - auth-service/src/test/java/de/goaldone/authservice/service/VerificationTokenServiceTests.java
    - auth-service/src/test/java/de/goaldone/authservice/controller/PasswordResetTests.java
    - auth-service/src/test/java/de/goaldone/authservice/service/EmailTemplateRenderingTest.java
decisions:
  - "D-04: Auth-service owns entire password reset flow — no changes needed"
  - "D-05: PASSWORD_RESET tokens expire in 1 hour via getExpiryHoursForType() dispatch"
  - "D-06: One-time tokens enforced by verifyToken() which deletes on use"
  - "D-07: POST /reset-password redirects to /reset-success (new success page)"
  - "D-08: Email subject 'Reset Your GoalDone Password', English body, #63729c branding"
  - "D-02 deviation: base.css used instead of Bootstrap/Tailwind (acknowledged in plan)"
metrics:
  duration: "~35 minutes"
  completed: "2026-05-04"
  tasks: 3
  files_changed: 13
---

# Phase 08 Plan 03: Fix Password Reset Bugs Summary

**One-liner:** Fixed 5 password reset bugs: per-type token expiry (1h), checkToken NPE, /reset-success redirect, English email with #63729c branding, and CSRF NPE removal.

## Objective

Fix all 5 known password reset bugs in the auth-service to implement decisions D-04 through D-08.

## Tasks Completed

### Task 1: VerificationTokenService + PasswordResetController + reset-success.html (TDD)
**Commits:** `25cfc9e` (RED tests), `3cd1ac5` (GREEN implementation)

- **VerificationTokenService**: Renamed `expiryHours` → `defaultExpiryHours`, added `passwordResetExpiryHours` (`@Value("${app.token.password-reset-expiry-hours:1}")`), and `getExpiryHoursForType(TokenType)` helper. PASSWORD_RESET tokens now expire in 1h, all others keep 24h.
- **application.yaml**: Added `app.token.expiry-hours: 24` and `app.token.password-reset-expiry-hours: 1`
- **PasswordResetController**: Fixed GET /reset-password to use `checkToken()` (non-consuming). Fixed POST success redirect to `redirect:/reset-success`. Added `@GetMapping("/reset-success")` handler.
- **DefaultSecurityConfig**: Added `/reset-success` to `permitAll()` (required for public success page)
- **reset-password.html**: Full rewrite — removed CSRF field (NPE fix), removed inline `<style>` block, English text, `.alert` classes, password toggle buttons with `aria-label`
- **reset-success.html**: Created with `layout:decorate`, `alert-info`, "Return to Login" button

### Task 2: SmtpMailService + Email Templates
**Commit:** `d61b768`

- **SmtpMailService**: Added `expirationDate` variable (1h from now formatted), `userName` variable. Changed subject to "Reset Your GoalDone Password".
- **mail/password-reset.html**: Full rewrite — English content, `#63729c` GoalDone brand color (not Bootstrap `#007bff`), "Reset My Password" CTA, `th:text="${expirationDate}"` display, English security reminder.
- **mail/password-reset.txt**: Full rewrite — English plain text with `[(${...})]` Thymeleaf interpolation.

### Task 3: forgot-password.html
**Commit:** `96106e6`

- **forgot-password.html**: Replaced inline-styled success div (`background-color: #ebf8ff`) with `class="alert alert-info"`.
- Note: `reset-password.html` was fully rewritten in Task 1 (blocking issue — CSRF NPE prevented Task 1 tests from passing).

## Test Results

- 102 tests pass (including 9 new/updated tests for this plan)
- 1 pre-existing failure: `ClientSeedingIntegrationTest.shouldSeedDefaultClients` (unrelated to this plan — confirmed pre-existing by git stash test)

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `25cfc9e` | test | Add failing tests for per-type expiry and reset-success routing (RED) |
| `3cd1ac5` | fix | Per-type token expiry + checkToken fix + reset-success route + reset-password rewrite (GREEN) |
| `d61b768` | fix | SmtpMailService expirationDate + English email subject + rewrite email templates |
| `96106e6` | fix | forgot-password.html success message uses .alert .alert-info |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] User domain class has no setEmail() method**
- **Found during:** Task 1 RED phase test compilation
- **Issue:** Plan's test code used `new User(); mockUser.setEmail(email)` but `User` entity uses `List<UserEmail>` relationship, no direct `setEmail()` method
- **Fix:** Changed to `mock(User.class)` like the existing `testSessionInvalidationAfterReset` test does
- **Files modified:** `PasswordResetTests.java`
- **Commit:** `25cfc9e`

**2. [Rule 2 - Missing] /reset-success not in permitAll()**
- **Found during:** Task 1 GREEN phase — `testResetSuccessPage_returns200` got 302 redirect
- **Issue:** New GET /reset-success endpoint requires authentication (missing from `permitAll()` list)
- **Fix:** Added `/reset-success` to `permitAll()` in DefaultSecurityConfig
- **Files modified:** `DefaultSecurityConfig.java`
- **Commit:** `3cd1ac5`

**3. [Rule 3 - Blocking] CSRF field in reset-password.html blocks testResetPasswordForm test**
- **Found during:** Task 1 GREEN phase — template throws NPE when rendering `_csrf.token` (CSRF globally disabled)
- **Issue:** The CSRF field was always wrong (per plan's bug list), but previously the test was getting 302 before template rendering
- **Fix:** Did the full reset-password.html rewrite (Task 3 scope) in Task 1 to unblock the test. Task 3 only needed to fix forgot-password.html.
- **Files modified:** `reset-password.html`
- **Commit:** `3cd1ac5`

**4. [Rule 1 - Bug] EmailTemplateRenderingTest expected German text in password reset email**
- **Found during:** Task 2 full test suite run
- **Issue:** Existing email rendering tests checked for "Passwort-Zurücksetzen", "Sicherheit", "Teile diesen Link nicht" — all German content we replaced
- **Fix:** Updated 3 test assertions to check English equivalents ("Reset Your GoalDone Password", "Security reminder", "Do not share this link")
- **Files modified:** `EmailTemplateRenderingTest.java`
- **Commit:** `d61b768`

## Known Stubs

None — all data is wired (expirationDate computed in SmtpMailService, resetUrl from controller, token from VerificationTokenService).

## Threat Flags

None — all changes are within the trust boundaries described in the plan's threat model. No new network endpoints introduced beyond `/reset-success` (explicitly planned in D-07).

## Decisions Exercised

- **D-04**: Auth-service owns entire password reset flow ✅
- **D-05**: Reset token expires in exactly 1 hour via `passwordResetExpiryHours` ✅
- **D-06**: One-time use enforced via `verifyToken()` which deletes token on use ✅
- **D-07**: POST /reset-password redirects to `/reset-success`; new GET handler returns success page ✅
- **D-08**: Email subject "Reset Your GoalDone Password"; English body; `#63729c` GoalDone branding ✅

## Self-Check: PASSED

**Files verified:**
- ✅ `auth-service/src/main/resources/templates/auth/reset-success.html` — EXISTS
- ✅ `auth-service/src/main/java/de/goaldone/authservice/service/VerificationTokenService.java` — EXISTS
- ✅ `auth-service/src/main/java/de/goaldone/authservice/controller/PasswordResetController.java` — EXISTS
- ✅ `auth-service/src/main/resources/application.yaml` — EXISTS
- ✅ `auth-service/src/main/java/de/goaldone/authservice/service/SmtpMailService.java` — EXISTS
- ✅ `auth-service/src/main/resources/templates/mail/password-reset.html` — EXISTS
- ✅ `auth-service/src/main/resources/templates/auth/reset-password.html` — EXISTS
- ✅ `auth-service/src/main/resources/templates/auth/forgot-password.html` — EXISTS

**Commits verified:**
- ✅ `25cfc9e` — test(08-03): add failing tests for per-type expiry and reset-success routing
- ✅ `3cd1ac5` — fix(08-03): per-type token expiry + checkToken fix + reset-success route + reset-password rewrite
- ✅ `d61b768` — fix(08-03): SmtpMailService expirationDate + English email subject + rewrite email templates
- ✅ `96106e6` — fix(08-03): forgot-password.html success message uses .alert .alert-info

