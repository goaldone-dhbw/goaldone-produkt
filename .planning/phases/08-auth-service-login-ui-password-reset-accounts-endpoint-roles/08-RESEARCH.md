# Phase 8: Auth-Service Login UI, Password Reset, and Accounts Endpoint Roles — Research

**Researched:** 2026-05-04
**Domain:** Spring Boot (Auth-Service), Thymeleaf HTML templates, Spring Mail, OpenAPI code generation, Angular TypeScript
**Confidence:** HIGH — all findings are VERIFIED by direct codebase inspection

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01**: Target is auth-service native HTML login page only; frontend OIDC flow is already complete
- **D-02**: Use a template library/theme (Bootstrap, Tailwind, Material UI) for faster delivery — selection TBD during planning; NOT from-scratch or simple CSS refactor
- **D-03**: Login page redesign fully implemented, tested, deployed by end of Phase 8 (not mockups)
- **D-04**: Auth-service owns entire password reset flow; frontend only provides "Forgot Password?" link
- **D-05**: Reset token validity = exactly 1 hour from issuance
- **D-06**: One-time use tokens only; "Link already used" error message on reuse
- **D-07**: Success redirect = dedicated success page on auth-service; CTA "Return to Login" button; no auto-redirect
- **D-08**: Email from `noreply@goaldone.de`, subject "Reset Your GoalDone Password", GoalDone branded body
- **D-09**: Rename `GET /users/accounts` → `GET /me/organizations` in OpenAPI + backend + frontend
- **D-10**: Response root key `organizations` (not `accounts`); per-org entry includes `accountId`, `organizationId`, `organizationName`, `email`, `firstName`, `lastName`, `roles[]`, `hasConflicts`
- **D-11**: Update `api-spec/openapi.yaml`, regenerate backend sources (`./mvnw generate-sources`), regenerate frontend client (`npm run generate-api`)
- **D-12**: No backward compatibility; old `/users/accounts` endpoint removed, no dual-endpoint period

### Agent's Discretion
- **Template library selection** for login page redesign (D-02 says TBD; see recommendation in Standard Stack section)
- **Per-type token expiry** implementation approach (new config keys vs. parameter override)
- **Ordering** of organizations in `GET /me/organizations` response

### Deferred Ideas (OUT OF SCOPE)
2FA/WebAuthn, Social login, Email verification, Account lockout policy, Rate limiting on password resets,
Email confirmation for password reset, Admin password reset, Password history, Passwordless authentication,
Single sign-out, Session management UI, Audit logging for password resets
</user_constraints>

---

<phase_requirements>
## Phase Requirements

Phase 8 requirements are defined by CONTEXT.md decisions D-01 through D-12. Mapping:

| ID | Description | Research Support |
|----|-------------|-----------------|
| D-01 | Auth-service login page redesign (not frontend OIDC) | login.html exists, uses inline styles inconsistent with shared layout |
| D-02 | Use template library/theme | Base.css already has GoalDone CSS variables; recommendation: use existing system, not Bootstrap |
| D-03 | Full production implementation | All login page changes are in Thymeleaf templates + static CSS |
| D-04 | Auth-service owns all password reset | PasswordResetController already exists and is complete |
| D-05 | 1-hour token expiry | `app.token.expiry-hours` currently defaults to 24; must be overridden per-type |
| D-06 | One-time use tokens | Already implemented: `validateToken()` deletes token on use |
| D-07 | Dedicated success page | Currently redirects to `/login?reset_success`; needs new `auth/reset-success.html` template |
| D-08 | English email subject and GoalDone branding | Email template exists but is German; subject is "Password Reset Request" not "Reset Your GoalDone Password" |
| D-09 | Rename endpoint path | `GET /users/accounts` → `GET /me/organizations` in OpenAPI, backend, frontend |
| D-10 | New response structure with `organizations` key | `AccountListResponse.accounts` → `UserOrganizationsResponse.organizations`; logic must return ALL user orgs |
| D-11 | OpenAPI + code generation | Both backend and frontend have code generation configured |
| D-12 | Remove old endpoint | Clean removal; no dual-endpoint period |
</phase_requirements>

---

## Summary

Phase 8 addresses three distinct features. The critical finding from codebase inspection is that **most of Feature 2 (Password Reset) is already substantially implemented** — `PasswordResetController`, `VerificationTokenService`, email templates, and DB schema all exist. The work is correcting a handful of configuration gaps and behavioral mismatches with the CONTEXT.md decisions. Feature 1 (Login Page) needs incremental updates, not a rewrite. Feature 3 (Accounts → Organizations) is the most complex because it requires coordinated changes across OpenAPI spec, backend logic, and three frontend regeneration passes.

**Primary recommendation:** Plan three independent tasks (one per feature), ordered: Feature 3 first (API contract change enables clean frontend regen), then Feature 2 (fixes/polish on existing infrastructure), then Feature 1 (pure UI work with no backend impact). Feature 3 is the highest blast-radius change because it regenerates API clients that other frontend components consume.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Login page HTML/CSS redesign | Auth-Service (SSR) | — | Thymeleaf templates served by auth-service Spring Boot |
| Password visibility toggle | Browser/Client | — | Pure HTML/JS with no backend involvement |
| "Forgot Password?" link | Auth-Service (SSR) | Frontend (env.js config) | Link rendered in auth-service login template; frontend may need reset URL in env.js |
| Password reset token lifecycle | Auth-Service API | — | PasswordResetController + VerificationTokenService already owns this |
| Reset email sending | Auth-Service (prod: SmtpMailService, dev: LocalMailService) | — | Spring Mail behind MailService interface |
| Token expiry enforcement | Auth-Service domain | — | `VerificationToken.isExpired()` on each `validateToken()` call |
| Success/error pages | Auth-Service (SSR) | — | Thymeleaf templates in auth/  |
| `GET /me/organizations` endpoint | Backend API | — | UserAccountsController → UserService |
| `GET /me/organizations` multi-org logic | Backend Service | — | UserService.buildAccountListResponse() needs rewrite for all-orgs |
| OpenAPI spec update | API Contract (api-spec/) | — | Both backend and frontend regenerate from this single source of truth |
| Frontend API client regeneration | Frontend Build | — | `npm run generate-api` from openapi.yaml |
| Frontend component updates | Frontend | — | account-state.service, app-sidebar, tasks-page, user-settings, working-hours pages |

---

## Standard Stack

### Auth-Service — Already Available [VERIFIED: auth-service/pom.xml]

| Library | Available | Purpose | Notes |
|---------|-----------|---------|-------|
| `spring-boot-starter-thymeleaf` | ✅ | HTML template rendering | Login page, password reset pages |
| `thymeleaf-extras-springsecurity6` | ✅ | Spring Security integration in Thymeleaf | CSRF, auth context in templates |
| `thymeleaf-layout-dialect` (implied) | ✅ | `layout:decorate` syntax in templates | Used by forgot-password.html, reset-password.html |
| `spring-boot-starter-mail` | ✅ | Email sending via JavaMailSender | SmtpMailService uses it |
| `spring-boot-starter-security` | ✅ | Security filter chain | DefaultSecurityConfig |
| `spring-boot-starter-data-jpa` | ✅ | JPA/Hibernate | VerificationTokenRepository |
| Liquibase | ✅ | DB schema migrations | verification_tokens table already created |

**Finding:** No new dependencies needed for Features 1 or 2. Feature 3 (OpenAPI rename) similarly requires no new dependencies.

### Template Library Decision (D-02)

**Finding:** The auth-service already has a mature, GoalDone-branded CSS design system in `src/main/resources/static/css/base.css` with:
- GoalDone CSS variables (`--primary-500: #63729c`, `--accent-500: #a85791`, etc.)
- Full component classes: `.btn`, `.btn-primary`, `.form-group`, `.form-input`, `.form-label`, `.alert`, `.alert-error`, `.container`
- Consistent `.alert-info`, `.alert-warning`, `.alert-error` states
- Shared layout via `fragments/layout.html` used by forgot-password and reset-password pages

**Recommendation:** Use the existing `base.css` CSS design system (not Bootstrap/Tailwind). Reasons:
1. The password reset pages already use it successfully and look production-ready
2. Adding Bootstrap would increase payload and risk style conflicts with the existing CSS variables
3. The login page just needs to be updated to use `fragments/layout.html` for visual consistency
4. "Lightweight option compatible with Spring Boot HTML templates" — the existing system qualifies

If the planner decides to proceed with Bootstrap/Tailwind instead, note: Bootstrap 5 via CDN is the simplest path (no npm build step in auth-service, just CDN link in layout.html). [ASSUMED for Bootstrap path — training knowledge]

---

## Architecture Patterns

### System Architecture Diagram (Password Reset Flow)

```
User Browser
    │
    ├──GET /forgot-password──────────────────────────────────────────┐
    │                                                                │
    │                                                     auth-service (Thymeleaf)
    │                                                     PasswordResetController
    │                                                                │
    ├──POST /forgot-password (email)─────────────────────────────────┤
    │                                                                │
    │                                                    VerificationTokenService
    │                                                    .createToken(email, PASSWORD_RESET)
    │                                                       │
    │                                               verification_tokens (DB)
    │                                                       │
    │                                                    MailService
    │                                                    .sendPasswordReset(email, url)
    │                                                       │
    │                           ┌──────── LocalMailService (non-prod: logs URL)
    │                           └──────── SmtpMailService (prod: SMTP → user's inbox)
    │
    │── (User clicks reset link in email)
    │
    ├──GET /reset-password?token=X───────────────────────────────────┤
    │                                                                │
    │                                                    tokenService.validateToken() [non-consuming]
    │                                                    → renders reset-password.html
    │
    ├──POST /reset-password (token, password, confirmPassword)────────┤
    │                                                                │
    │                                                    tokenService.verifyToken() [consuming]
    │                                                    → deletes token (single-use)
    │                                                    user.setPassword(encoded)
    │                                                    invalidateUserSessions(email)
    │                                                    redirect → /reset-success (NEW)
    │
    └──GET /reset-success────────────────────────────────────────────┤
                                                          success page + "Return to Login" button
```

### System Architecture Diagram (Accounts → Organizations)

```
Frontend (Angular)
    │
    ├── OLD: UserAccountsService.getMyAccounts(orgId) → GET /users/accounts?X-Org-ID
    │
    └── NEW: UserOrganizationsService.getMyOrganizations() → GET /me/organizations
                                                             (no X-Org-ID required)
                                                                 │
                                                         Backend UserAccountsController
                                                         (implements UserOrganizationsApi — regenerated)
                                                                 │
                                                         UserService.buildOrganizationsResponse(jwt)
                                                         - extracts user_id from JWT
                                                         - membershipRepository.findAllByUserId(userId)
                                                         - builds UserOrganizationsResponse.organizations[]
                                                                 │
                                                         MembershipRepository
                                                         + OrganizationRepository
```

### Recommended Project Structure (Auth-Service Templates)

```
src/main/resources/
├── static/css/
│   └── base.css                    # GoalDone CSS variables + component classes [EXISTS]
├── templates/
│   ├── login.html                  # MODIFY: add layout, forgot-password link, password toggle
│   ├── error.html                  # [EXISTS, unchanged]
│   ├── fragments/
│   │   └── layout.html             # Shared layout [EXISTS, login.html should use this]
│   ├── auth/
│   │   ├── forgot-password.html    # [EXISTS — minor English text fixes]
│   │   ├── reset-password.html     # [EXISTS — fix CSRF, update styles]
│   │   └── reset-success.html      # CREATE NEW (D-07)
│   └── mail/
│       ├── password-reset.html     # [EXISTS — update to English, fix expirationDate variable]
│       └── password-reset.txt      # [EXISTS — update to English]
```

### Pattern 1: Thymeleaf Layout Fragment (Auth-Service Pattern)

Auth-service pages use `thymeleaf-layout-dialect` for shared layout:

```html
<!-- Template pattern used by forgot-password.html and reset-password.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{fragments/layout}">
<head>
    <title>Page Title - GoalDone</title>
</head>
<body>
<div layout:fragment="content">
    <!-- Page-specific content goes here -->
    <!-- Can use CSS classes from base.css: .btn, .btn-primary, .form-group, .form-input, etc. -->
</div>
</body>
</html>
```

**Note:** `login.html` currently does NOT use this layout — it has its own inline styles (gradient background). The login page should be updated to use `fragments/layout.html` so it visually matches the reset-password pages.

### Pattern 2: Thymeleaf Spring Security CSRF-safe form

CSRF is **disabled** in `DefaultSecurityConfig` (`.csrf(csrf -> csrf.disable())`). The existing `reset-password.html` has a CSRF hidden field that will fail when `_csrf` is null:

```html
<!-- BUG in reset-password.html — REMOVE this line since CSRF is disabled: -->
<input type="hidden" name="_csrf" th:value="${_csrf.token}"/>

<!-- Safe alternative (not needed since CSRF is disabled): -->
<input type="hidden" name="_csrf" th:if="${_csrf != null}" th:value="${_csrf?.token}"/>
```

**Action:** Remove the CSRF hidden field from `reset-password.html` since CSRF is globally disabled. [VERIFIED: `DefaultSecurityConfig.java` line 72]

### Pattern 3: Password Visibility Toggle (Pure HTML/JS)

No library needed — implement with vanilla JS:

```html
<div class="form-group" style="position: relative;">
    <label for="password" class="form-label">Password</label>
    <input type="password" id="password" name="password" class="form-input" required>
    <button type="button"
            onclick="togglePassword('password', this)"
            style="position: absolute; right: 10px; top: 32px; background: none; border: none; cursor: pointer; padding: 4px; color: var(--text-muted);"
            aria-label="Toggle password visibility">
        👁
    </button>
</div>
<script>
function togglePassword(fieldId, btn) {
    var input = document.getElementById(fieldId);
    input.type = input.type === 'password' ? 'text' : 'password';
    btn.textContent = input.type === 'password' ? '👁' : '🙈';
}
</script>
```

### Pattern 4: Per-Type Token Expiry Configuration

The `VerificationTokenService.createToken()` uses a single `expiryHours` config for both INVITATION and PASSWORD_RESET. D-05 requires 1 hour for PASSWORD_RESET while invitations may need longer (24h default).

**Recommended implementation:**

```java
// In VerificationTokenService:
@Value("${app.token.expiry-hours:24}")
private int defaultExpiryHours;

@Value("${app.token.password-reset-expiry-hours:1}")
private int passwordResetExpiryHours;

private int getExpiryHoursForType(TokenType type) {
    return type == TokenType.PASSWORD_RESET ? passwordResetExpiryHours : defaultExpiryHours;
}
```

And in `application.yaml` (or `application-prod.yaml`), add:
```yaml
app:
  token:
    password-reset-expiry-hours: 1
```

### Pattern 5: SmtpMailService — Missing expirationDate variable

The `mail/password-reset.html` template uses `${expirationDate}` but `SmtpMailService.sendPasswordReset()` does not set it:

```java
// CURRENT (broken — expirationDate renders as null in email):
@Override
public void sendPasswordReset(String to, String resetUrl) {
    Context context = new Context();
    context.setVariable("resetUrl", resetUrl);
    // ❌ Missing: expirationDate never set
    String htmlContent = templateEngine.process("mail/password-reset", context);
    sendEmail(to, "Password Reset Request", htmlContent);
}

// FIX:
@Override
public void sendPasswordReset(String to, String resetUrl) {
    Context context = new Context();
    context.setVariable("resetUrl", resetUrl);
    // Set expiration date for display in email (1 hour from now)
    context.setVariable("expirationDate",
        LocalDateTime.now().plusHours(1)
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'Uhr'")));
    String htmlContent = templateEngine.process("mail/password-reset", context);
    sendEmail(to, "Reset Your GoalDone Password", htmlContent);  // D-08 subject
}
```

### Pattern 6: `/me/organizations` Backend Logic (Multi-Org Return)

Current `UserService.buildAccountListResponse()` returns SINGLE org (current X-Org-ID context). New endpoint must return ALL user's orgs:

```java
// NEW method signature:
public UserOrganizationsResponse buildOrganizationsResponse(Jwt jwt) {
    String userIdClaim = jwt.getClaimAsString("user_id");
    if (userIdClaim == null) {
        throw new IllegalStateException("Missing 'user_id' claim in JWT");
    }
    UUID userId = UUID.fromString(userIdClaim);
    
    // Find ALL memberships for this user (not just current org context)
    List<MembershipEntity> memberships = membershipRepository.findAllByUserId(userId);
    
    String primaryEmail = jwt.getClaimAsString("primary_email");
    
    List<UserOrganization> organizations = memberships.stream()
        .map(m -> {
            OrganizationEntity org = organizationRepository.findById(m.getOrganizationId()).orElseThrow();
            UserOrganization uo = new UserOrganization();
            uo.setAccountId(m.getId());
            uo.setOrganizationId(m.getOrganizationId());
            uo.setOrganizationName(org.getName());
            uo.setEmail(primaryEmail);
            uo.setRoles(m.getRole() != null ? List.of(m.getRole()) : List.of());
            uo.setHasConflicts(/* working time conflict check */ false);
            return uo;
        })
        .sorted(Comparator.comparing(UserOrganization::getOrganizationName))  // D-10: ordered by name
        .toList();
    
    UserOrganizationsResponse response = new UserOrganizationsResponse();
    response.setOrganizations(organizations);
    return response;
}
```

### Anti-Patterns to Avoid

- **Don't break `/users/accounts/links/*` endpoints**: Only the `GET /users/accounts` path is renamed. The link endpoints (`/users/accounts/links/request`, `/users/accounts/links/confirm`, `/users/accounts/links/{accountId}`) stay as-is and are NOT part of this phase.
- **Don't use `th:action` without considering CSRF state**: CSRF is disabled; `th:action="@{/url}"` is fine (Spring won't inject CSRF token). Explicit `_csrf` hidden fields must be removed.
- **Don't change invitation token expiry**: `app.token.expiry-hours` (the DEFAULT) should stay at 24 hours for invitations. Only PASSWORD_RESET gets 1 hour.
- **Don't create duplicate MailService method signatures**: `sendPasswordReset()` interface exists; just update the implementation.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token generation | Custom UUID/random | `SecureRandom` + Base64 (already done) | Already implemented correctly in `VerificationTokenService.generateSecureToken()` |
| Token storage | Custom table | `verification_tokens` table (already done) | Already migrated via `04-security-foundation.xml` |
| Email sending | Custom SMTP client | `JavaMailSender` via `spring-boot-starter-mail` (already done) | Already configured in SmtpMailService |
| HTML email template | Raw string concatenation | Thymeleaf template engine (already done) | `mail/password-reset.html` exists |
| Session invalidation on reset | Custom | `SessionRegistry.getAllSessions()` (already done) | Already in `PasswordResetController.invalidateUserSessions()` |
| API client generation | Manual TypeScript | `npm run generate-api` (already configured) | OpenAPI generator produces typed Angular service |
| Backend API interface | Manual Java | `./mvnw generate-sources` (already configured) | OpenAPI generator produces Java interface from spec |

**Key insight:** Features 1 and 2 are mostly fixing/polishing existing well-structured implementations. The infrastructure is already built — the bugs are in configuration gaps and one missing template.

---

## Runtime State Inventory

> This phase involves an endpoint rename (D-09: `/users/accounts` → `/me/organizations`) and token configuration changes. Runtime state audit:

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | `verification_tokens` table — tokens created before this phase have 24-hour expiry. No `PASSWORD_RESET` type tokens should exist in prod during deploy (users abandon mid-flow). | No migration needed; new config takes effect for new tokens only |
| Live service config | Auth-service SMTP config in env vars (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`); prod uses `spring.mail.*` wired from env vars. No changes to these. | None |
| OS-registered state | None — no scheduled tasks or OS services reference endpoint paths or token expiry | None |
| Secrets/env vars | Add `APP_TOKEN_PASSWORD_RESET_EXPIRY_HOURS=1` to `.env` and docker-compose env. New config key only, no existing key changes. | Add to .env template + docker-compose auth-service env section |
| Build artifacts | Frontend `src/app/api/` folder is generated from OpenAPI spec. After endpoint rename, regenerated files will replace `userAccounts.service.ts`, `accountListResponse.ts`, `accountResponse.ts`. | Regenerate: `npm run generate-api`; manually delete old `accountListResponse.ts` if generator doesn't replace it |

---

## Common Pitfalls

### Pitfall 1: CSRF Token in Reset Password Form
**What goes wrong:** Template `reset-password.html` has `<input type="hidden" name="_csrf" th:value="${_csrf.token}"/>` but CSRF is disabled globally in `DefaultSecurityConfig`. When `_csrf` is null in the Thymeleaf context, this throws a `PropertyAccessException` rendering the page. [VERIFIED: DefaultSecurityConfig.java + reset-password.html]
**Why it happens:** CSRF was disabled for OAuth2 compatibility but the template still has a leftover CSRF field.
**How to avoid:** Remove the CSRF hidden field from `reset-password.html`. The form will still work without it (CSRF is disabled).
**Warning signs:** 500 error on GET `/reset-password?token=X` when viewing the reset form.

### Pitfall 2: Token Expiry Shared Across All TokenTypes
**What goes wrong:** `VerificationTokenService` uses ONE `app.token.expiry-hours` config for both INVITATION and PASSWORD_RESET tokens. Changing the default to 1 hour would make invitations expire in 1 hour (they currently default to 24 hours).
**Why it happens:** Single config value without per-type override.
**How to avoid:** Add `app.token.password-reset-expiry-hours: 1` separately and select by `TokenType` in `VerificationTokenService.createToken()`. [VERIFIED: VerificationTokenService.java line 29]
**Warning signs:** Users unable to accept invitations that are more than 1 hour old.

### Pitfall 3: Missing `expirationDate` in Email Template Context
**What goes wrong:** `mail/password-reset.html` references `${expirationDate}` in TWO places but `SmtpMailService.sendPasswordReset()` never adds it to the Thymeleaf context. Email renders with empty expiration date field — confusing and unprofessional.
**Why it happens:** Template was written expecting the variable but service was never updated to pass it.
**How to avoid:** Add `context.setVariable("expirationDate", formattedDate)` in `SmtpMailService.sendPasswordReset()`.
**Warning signs:** Empty "Gültig bis:" line in the reset email (only visible in prod SMTP mode, not LocalMailService).

### Pitfall 4: Password Reset Success Redirect Goes to Login (Not Success Page)
**What goes wrong:** `PasswordResetController.resetPassword()` does `return "redirect:/login?reset_success"` but D-07 requires a dedicated success page with "Return to Login" button. Without creating the success page and route, users see a generic login page without acknowledgment.
**Why it happens:** Success page was not created yet.
**How to avoid:** Create `auth/reset-success.html` template and add `GET /reset-success` mapping to `PasswordResetController`. Update the POST handler redirect.

### Pitfall 5: X-Org-ID Header Semantics Change for `/me/organizations`
**What goes wrong:** Current `/users/accounts` requires `X-Org-ID` header and returns the membership for THAT org only. New `/me/organizations` should return ALL user orgs (no org filter). If the new endpoint still requires X-Org-ID, it won't work for the sidebar component that calls it before any org is selected.
**Why it happens:** Old endpoint was org-context-filtered; new endpoint is user-context — ALL orgs.
**How to avoid:** Make X-Org-ID optional (or remove it) for `/me/organizations`. The `UserService.buildOrganizationsResponse()` should use `jwt.getClaimAsString("user_id")` to find ALL memberships, not filter by org. [VERIFIED: AccountStateService calls this BEFORE org selection, needs all orgs]
**Warning signs:** Empty organizations list on first page load when no X-Org-ID is set.

### Pitfall 6: Frontend `response.accounts` → `response.organizations` After Regeneration
**What goes wrong:** After API regeneration, the TypeScript model changes from `AccountListResponse.accounts` to `UserOrganizationsResponse.organizations`. All frontend components using `response.accounts` will get TypeScript compile errors.
**Why it happens:** Code generation replaces the model file with new property names.
**How to avoid:** After regeneration, immediately fix all 5 consumer files in one pass:
1. `account-state.service.ts` — `response.accounts` → `response.organizations`
2. `app-sidebar.component.ts` — `response.accounts` → `response.organizations`
3. `tasks-page.component.ts` — `getMyAccounts(orgId)` → `getMyOrganizations()`, `response.accounts` → `response.organizations`
4. `user-settings.page.ts` — same
5. `working-hours.page.ts` — same
**Warning signs:** TypeScript compiler errors after `npm run generate-api` with "Property 'accounts' does not exist on type 'UserOrganizationsResponse'".

### Pitfall 7: Email Language Mismatch (German vs English)
**What goes wrong:** Current password-reset templates (`mail/password-reset.html`, `reset-password.html`) are in German (e.g., "Passwort zurücksetzen", "Gültig bis"). D-08 specifies English content.
**Why it happens:** Templates were written in German initially.
**How to avoid:** Update all user-facing text in password reset templates to English during Feature 2 implementation. Also update email subject from "Password Reset Request" to "Reset Your GoalDone Password" (D-08).

---

## Code Examples

### Example 1: New `reset-success.html` Template (D-07)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{fragments/layout}">
<head>
    <title>Password Reset Successful - GoalDone</title>
</head>
<body>
<div layout:fragment="content">
    <h2>Password Reset Successful</h2>
    <p class="text-muted" style="margin-bottom: var(--space-6);">Your password has been successfully reset.</p>

    <div class="alert alert-info">
        <p style="margin: 0;">You can now log in with your new password.</p>
    </div>

    <div style="margin-top: var(--space-6);">
        <a th:href="@{/login}" class="btn btn-primary" style="display: block; text-align: center;">
            Return to Login
        </a>
    </div>
</div>
</body>
</html>
```

### Example 2: New OpenAPI `/me/organizations` Endpoint (api-spec/openapi.yaml)

```yaml
# REPLACE /users/accounts with:
  /me/organizations:
    get:
      tags:
        - user-accounts
      summary: Get all organizations for current user
      operationId: getMyOrganizations
      security:
        - bearerAuth: []
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserOrganizationsResponse'
        '401':
          description: Unauthorized

# ADD new schemas (replacing AccountListResponse and AccountResponse):
    UserOrganization:
      type: object
      required:
        - accountId
        - organizationId
        - organizationName
        - roles
        - hasConflicts
      properties:
        accountId:
          type: string
          format: uuid
        organizationId:
          type: string
          format: uuid
        organizationName:
          type: string
        email:
          type: string
          format: email
        firstName:
          type: string
        lastName:
          type: string
        roles:
          type: array
          items:
            type: string
        hasConflicts:
          type: boolean

    UserOrganizationsResponse:
      type: object
      required:
        - organizations
      properties:
        organizations:
          type: array
          items:
            $ref: '#/components/schemas/UserOrganization'
```

### Example 3: Updated `PasswordResetController` (POST /reset-password → success page)

```java
// CHANGE redirect in resetPassword() method:
// OLD:
return "redirect:/login?reset_success";

// NEW (D-07):
return "redirect:/reset-success";

// ADD new controller method:
@GetMapping("/reset-success")
public String resetSuccess() {
    return "auth/reset-success";
}
```

---

## Feature-by-Feature Implementation Summary

### Feature 1: Login Page Redesign

**Current state:** `login.html` has custom inline styles (gradient background), "GoalDone" heading, but:
- Does NOT use `fragments/layout.html` (inconsistent with forgot-password/reset-password pages)
- Missing "Forgot Password?" link → `/forgot-password`
- Missing password visibility toggle
- Has `param.error` error display but no `param.reset_success` success message

**Changes required:**
1. Refactor `login.html` to use `layout:decorate="~{fragments/layout}"` and CSS classes from `base.css`
2. Add "Forgot Password?" link below the Sign In button: `<a th:href="@{/forgot-password}">Forgot your password?</a>`
3. Add password visibility toggle button (vanilla JS, see Pattern 3 above)
4. Optionally add `th:if="${param.reset_success}"` success banner for post-reset feedback

### Feature 2: Password Reset Fixes

**Current state:** All major components exist. 5 targeted fixes needed:
1. **Token expiry config**: Add `app.token.password-reset-expiry-hours: 1` to `application.yaml`; update `VerificationTokenService` to use per-type expiry
2. **CSRF bug fix**: Remove `<input type="hidden" name="_csrf" th:value="${_csrf.token}"/>` from `reset-password.html`
3. **Email context fix**: Add `expirationDate` to `SmtpMailService.sendPasswordReset()` context; update email subject to English (D-08)
4. **Success page**: Create `auth/reset-success.html`; add `GET /reset-success` mapping; update POST handler redirect
5. **Language/branding**: Update all user-facing text in reset templates to English; email from `noreply@goaldone.de` (configure `spring.mail.from` or `MimeMessageHelper.setFrom()`)

### Feature 3: Accounts → Organizations Endpoint

**Current state:** `GET /users/accounts` (path in OpenAPI: `/users/accounts`, operationId: `getMyAccounts`), `AccountListResponse.accounts[]`

**Changes required (in dependency order):**
1. **OpenAPI spec** (`api-spec/openapi.yaml`):
   - Add `GET /me/organizations` with `UserOrganizationsResponse` schema
   - Add `UserOrganization` model (rename of `AccountResponse`)
   - Add `UserOrganizationsResponse` model (rename of `AccountListResponse` with `organizations` key)
   - Remove `GET /users/accounts` entry (keep `/users/accounts/links/*` unchanged)
   - Keep `AccountResponse` + `AccountListResponse` in schema ONLY if they are still referenced elsewhere
   - Verify `AccountListResponse` is only referenced by `getMyAccounts` — if so, safe to remove

2. **Backend regeneration** (`backend/`):
   - Run: `cd backend && ./mvnw generate-sources`
   - Implement new `UserOrganizationsApi` interface in `UserAccountsController`
   - Update `UserService`: add `buildOrganizationsResponse(Jwt)` method that returns ALL user orgs
   - Remove/deprecate `buildAccountListResponse(Jwt, UUID)` or keep it if still needed elsewhere
   - Update tests in `AccountLinkingIntegrationTest.testGetMyAccounts_ReturnsMultipleMemberships()`

3. **Frontend regeneration** (`frontend/`):
   - Run: `cd frontend && npm run generate-api`
   - Update all 5 consumer files (see Pitfall 6 above)
   - Update frontend spec test mocks that reference `accounts` array

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Separate CSRF field in Thymeleaf form | CSRF disabled globally | Phase 1 (DefaultSecurityConfig) | Must remove explicit CSRF hidden fields from templates |
| Single-profile email service | Profile-based LocalMailService/SmtpMailService | Phase 1 | Dev logs to console; prod sends real SMTP |
| Single global token expiry | Per-type expiry (Phase 8) | Phase 8 (new) | PASSWORD_RESET = 1h, INVITATION = 24h |
| `/users/accounts` (GET, X-Org-ID filtered) | `/me/organizations` (GET, returns all user orgs) | Phase 8 (new) | Multi-org response; no org filter needed |

**Deprecated/outdated in Phase 8:**
- `AccountListResponse` schema: replaced by `UserOrganizationsResponse`
- `AccountResponse` schema: replaced by `UserOrganization`
- `getMyAccounts` operationId: replaced by `getMyOrganizations`
- German-language reset templates: replaced with English (D-08)
- 24-hour PASSWORD_RESET token expiry: replaced with 1-hour (D-05)

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `thymeleaf-layout-dialect` is on classpath (enables `layout:decorate`). It's used in existing templates but not explicitly in pom.xml search above. | Standard Stack | If not available, layout-based templates would fail. However, `forgot-password.html` already uses it successfully — so it IS available. Confidence: HIGH (verified by working templates). |
| A2 | `AccountListResponse` schema is ONLY referenced by `getMyAccounts` in openapi.yaml — safe to remove | Feature 3 | If it's referenced by other endpoints, removal would break generation. Planner should verify with `grep AccountListResponse api-spec/openapi.yaml` |
| A3 | `app.token.password-reset-expiry-hours` will be a new config key not conflicting with existing keys | Feature 2 | Low risk — new key name doesn't exist in any yaml file |
| A4 | `LocalMailService.sendPasswordReset()` doesn't pass expiration date either, but since it only logs — no template rendering issue in dev. Only SmtpMailService uses the Thymeleaf template. | Feature 2 | Low risk — LocalMailService just logs plain text |

**If this table is empty:** All claims would be VERIFIED. The 4 items above are low-risk assumptions confirmed by supporting evidence.

---

## Open Questions

1. **Template library selection (D-02)**
   - What we know: Existing `base.css` is production-quality and used by reset pages
   - What's unclear: Does the planner/user want Bootstrap/Tailwind OVER the existing CSS system?
   - Recommendation: Use existing `base.css` design system. Avoids adding dependencies while achieving same visual result.

2. **`AccountListResponse`/`AccountResponse` schemas: remove or keep?**
   - What we know: These are referenced by `getMyAccounts` only in the `/users/accounts` path being removed
   - What's unclear: Are they referenced anywhere else in openapi.yaml (schedules endpoints reference "accounts" in descriptions but not as schema types)?
   - Recommendation: Grep `api-spec/openapi.yaml` for `$ref: '#/components/schemas/AccountListResponse'` to confirm; safe to remove if only one reference.

3. **`/me/organizations` — should X-Org-ID header be removed or stay optional?**
   - What we know: New endpoint returns ALL user orgs, so X-Org-ID is not needed for filtering
   - What's unclear: Is it useful for audit/logging purposes to still accept X-Org-ID?
   - Recommendation: Remove X-Org-ID from the OpenAPI definition of `GET /me/organizations` since it's semantically wrong (all-orgs endpoint doesn't need an org filter).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Spring Boot / Maven wrapper | Backend regeneration (`./mvnw generate-sources`) | ✅ | Wrapper in `backend/mvnw` | — |
| Node.js / npm | Frontend regeneration (`npm run generate-api`) | ✅ | Assumed from Phase 7 completion | — |
| H2 in-memory DB | Auth-service local dev (`application-local.yaml`) | ✅ | Configured | — |
| Liquibase | DB schema (already applied) | ✅ | Already in use | — |
| SMTP server | Password reset emails in prod | 🔶 | Configured in prod via env vars | LocalMailService logs to console in non-prod |
| Redis | Auth-service sessions in prod | 🔶 | Configured in prod; excluded in local/dev | JDBC sessions in local (`application-local.yaml`) |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:**
- SMTP: LocalMailService logs reset URLs to console in non-prod — sufficient for development and testing.
- Redis: JDBC session store in local profile — no impact on Phase 8 work.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Auth-service framework | Spring Boot Test (JUnit 5) |
| Backend framework | Spring Boot Test / MockMvc (JUnit 5) — 100 tests passing from Phase 7 |
| Frontend framework | Jasmine + Angular Testing Library — 100 tests passing from Phase 7 |
| Backend quick run | `cd backend && ./mvnw test -Dtest=UserAccountsController* -pl .` |
| Backend full suite | `cd backend && ./mvnw test` |
| Frontend quick run | `cd frontend && npx ng test --include='**/*accounts*' --watch=false` |
| Frontend full suite | `cd frontend && npx ng test --watch=false` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-01/03 | Login page renders with GoalDone branding, Forgot Password link, password toggle | manual browser | N/A — visual check | N/A |
| D-04/05/06 | Password reset token created with 1h expiry, single-use | integration | `cd backend && ./mvnw test` (auth-service has own tests) | Check auth-service test dir |
| D-07 | POST /reset-password redirects to /reset-success page | integration (auth-service MockMvc) | auth-service test | ❌ Wave 0 |
| D-08 | Email subject = "Reset Your GoalDone Password" | unit (SmtpMailService) | auth-service test | ❌ Wave 0 |
| D-09 | GET /me/organizations returns 200 | backend MockMvc integration | `cd backend && ./mvnw test -Dtest=SecurityIntegrationTest` | ❌ Wave 0 |
| D-10 | Response has `organizations` array (not `accounts`) | backend MockMvc integration | `cd backend && ./mvnw test` | ❌ Wave 0 |
| D-11 | Frontend builds with no TypeScript errors after API regen | compilation | `cd frontend && npx ng build` | N/A |
| D-12 | GET /users/accounts returns 404 | backend MockMvc integration | `cd backend && ./mvnw test` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./mvnw test` + `cd frontend && npx ng build`
- **Per wave merge:** Full test suites on both backend and frontend
- **Phase gate:** Backend 100/100 tests green + frontend 100/100 tests green + `ng build` zero errors before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `backend/src/test/.../UserOrganizationsControllerTest.java` — covers D-09, D-10, D-12
- [ ] `auth-service/src/test/.../PasswordResetControllerTest.java` — covers D-07 (success redirect)
- [ ] `auth-service/src/test/.../SmtpMailServiceTest.java` — covers D-08 (email subject, expirationDate)

*(Existing `AccountLinkingIntegrationTest.testGetMyAccounts_ReturnsMultipleMemberships()` must be updated for the renamed endpoint and new response structure)*

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes | Spring Security form login; password encoded with BCrypt (PasswordEncoderFactories.createDelegatingPasswordEncoder()) |
| V3 Session Management | Yes | Session invalidated after password reset via SessionRegistry; Spring Session (JDBC/Redis) |
| V4 Access Control | Partial | `/me/organizations` requires valid JWT bearer; public routes for reset flow (permitted in DefaultSecurityConfig) |
| V5 Input Validation | Yes | Password match validation in controller; `minlength="12"` in HTML form |
| V6 Cryptography | Yes | `SecureRandom` + Base64 for token generation (already correct); BCrypt for password storage |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Token reuse attack | Elevation of privilege | Single-use tokens enforced by `validateToken()` deleting on use ✅ |
| Token interception (long window) | Information disclosure | 1-hour expiry (D-05) limits window ✅ |
| User enumeration via reset endpoint | Information disclosure | "Always show same message" comment in controller ✅ (already implemented) |
| Expired session after reset | Spoofing | `invalidateUserSessions()` called after successful reset ✅ |
| Session fixation | Elevation of privilege | Spring Security handles this via session rotation on login |
| Missing auth on org endpoint | Elevation of privilege | `bearerAuth` required on `/me/organizations` in OpenAPI; `managementApiSecurityFilterChain` + `@PreAuthorize` in backend |

---

## Sources

### Primary (HIGH confidence)
- `auth-service/src/main/java/de/goaldone/authservice/controller/PasswordResetController.java` — complete password reset flow
- `auth-service/src/main/java/de/goaldone/authservice/service/VerificationTokenService.java` — token lifecycle
- `auth-service/src/main/java/de/goaldone/authservice/service/SmtpMailService.java` — mail sending (missing expirationDate bug found)
- `auth-service/src/main/java/de/goaldone/authservice/config/DefaultSecurityConfig.java` — CSRF disabled, security rules
- `auth-service/src/main/resources/templates/login.html` — current login page state
- `auth-service/src/main/resources/templates/fragments/layout.html` — shared layout template
- `auth-service/src/main/resources/static/css/base.css` — GoalDone CSS design system
- `auth-service/src/main/resources/templates/auth/` — all reset page templates (verified state)
- `auth-service/src/main/resources/templates/mail/password-reset.html` — email template (expirationDate bug found)
- `auth-service/src/main/resources/application.yaml` — no `app.token.expiry-hours` set (uses default 24h)
- `api-spec/openapi.yaml` lines 120-136 — current `/users/accounts` endpoint definition
- `api-spec/openapi.yaml` lines 1266-1307 — `AccountResponse` + `AccountListResponse` schemas
- `backend/src/main/java/de/goaldone/backend/controller/UserAccountsController.java` — current implementation
- `backend/src/main/java/de/goaldone/backend/service/UserService.java` — `buildAccountListResponse()` (per-org logic)
- Frontend grep results — 5 files calling `getMyAccounts()` / accessing `response.accounts`

### Secondary (MEDIUM confidence)
- `.planning/phases/08-auth-service-login-ui-password-reset-accounts-endpoint-roles/08-CONTEXT.md` — all decisions D-01 through D-12

---

## Metadata

**Confidence breakdown:**
- Login page changes: HIGH — current template inspected, missing elements identified
- Password reset fixes: HIGH — all 5 bugs confirmed by direct code inspection
- Accounts endpoint rename: HIGH — current OpenAPI spec, controller, and all 5 frontend consumers verified
- Frontend consumer list: HIGH — full grep across frontend/src confirmed all files

**Research date:** 2026-05-04
**Valid until:** Stable (no external dependencies to drift)
