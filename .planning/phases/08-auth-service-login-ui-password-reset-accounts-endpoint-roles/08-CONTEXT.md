# Phase 8: Auth-Service Login UI, Password Reset, and Accounts Endpoint Roles - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning
**Depends on:** Phase 7 (✅ Complete - 100/100 tests, 0 build errors)

<domain>
## Phase Boundary

Phase 8 completes three user-facing features after Phase 7's integration work:

1. **Auth-Service Login Page Redesign** — Modernize the auth-service's native login UI with branding, improved UX, and error messaging
2. **Self-Service Password Reset** — Enable users to reset their password via email link with 1-hour token expiration
3. **Accounts/Organizations Endpoint Refactoring** — Rename `/accounts` → `/me/organizations` endpoint and finalize response structure for role-based org membership display

Result: A polished, production-ready auth-service with modern login UX, self-service password recovery, and a standardized user organizations API.

</domain>

<decisions>
## Implementation Decisions

### 1. Auth-Service Login Page UI Redesign

**D-01: Scope is Auth-Service Login Page (Not Frontend Flow)**
- Target: Auth-service's native HTML login page (the page users see when logging into auth-service directly)
- Not affected: Frontend OIDC flow is already working end-to-end (Phase 7 complete)
- Improvements needed:
  - **Modernize styling & branding:** Update HTML/CSS to match GoalDone branding (colors, fonts, logo placement)
  - **Improve error messaging:** Clear error messages for invalid credentials, account locked, etc.
  - **Add password visibility toggle:** Show/hide password button in password field
  - **Match frontend appearance:** Login page should visually align with the GoalDone frontend aesthetic

**D-02: Use Template Library/Theme**
- Approach: Leverage a pre-built template library or theme (e.g., Bootstrap, Tailwind, Material UI) for consistency and faster delivery
- **Not:** From-scratch rewrite or simple CSS refactor
- Rationale: Ensures professional appearance, consistency with web standards, reduces custom CSS maintenance
- Selection: TBD during planning phase (recommend lightweight option compatible with Spring Boot HTML templates)

**D-03: Full Implementation in Phase 8**
- Delivery: Login page redesign is **fully implemented, tested, and deployed** by end of Phase 8
- Not exploratory: This is production-ready work, not mockups/design-only
- Testing: Manual verification that login page loads correctly, form works, errors display, password toggle functions

### 2. Self-Service Password Reset Flow

**D-04: Auth-Service Owns Entire Password Reset Flow**
- Responsibility: Auth-service handles **all** password reset operations:
  - Email sending with reset link
  - Token generation and validation
  - Password update (no backend involvement required)
  - Error handling and retry logic
- Frontend involvement: Only for "Forgot Password?" link → redirect to auth-service password reset page
- Rationale: Keeps identity management centralized; auth-service is the source of truth for user credentials

**D-05: Reset Link Expiration = 1 Hour**
- Validity window: Reset token valid for **exactly 1 hour** from issuance
- After expiration: Link no longer works; user must request a new reset
- Rationale: Balanced security/usability — enough time for users to check email and reset, without excessive window for token interception
- Implementation: Store token issuance timestamp in auth-service DB; validate on reset form submission

**D-06: One-Time Use Tokens Only**
- Security: Reset token can be **used exactly once**
- Enforcement: After password is updated, token is marked as "used" and cannot be reused
- Error handling: If user clicks reset link a second time with same token, display "Link already used" message
- Rationale: Prevents token reuse attacks; forces user to request new reset if needed
- No additional protections: Rate limiting and email confirmation deferred; one-time use is sufficient for Phase 8

**D-07: Success Redirect = Auth-Service Success Page**
- After reset completes: User sees success message page on auth-service (not auto-redirect)
- Success page content: "Password successfully reset. You can now log in with your new password."
- CTA: Button to "Return to Login" (redirect to auth-service login page)
- Manual navigation: User clicks button to log in; no automatic session created
- Rationale: Explicit acknowledgment of successful reset; clearer UX for users to manually verify login works

**D-08: Reset Email Template**
- From: `noreply@goaldone.de` (or configured sender)
- Subject: "Reset Your GoalDone Password"
- Body: Include user name (if available), reset link with token, expiration time (1 hour), and support contact
- Design: Consistent with GoalDone branding (matches login page redesign)
- Link format: `https://[auth-service-url]/reset-password?token=[UUID]`

### 3. Accounts → Organizations Endpoint Refactoring

**D-09: Rename Endpoint `/accounts` → `/me/organizations`**
- Endpoint name change:
  - Old: `GET /accounts` (confusing naming)
  - New: `GET /me/organizations` (explicit: returns current user's organization memberships)
- Rationale: Clearer intent; signals "current user" scope via `/me/` prefix; aligns with REST conventions for resource-scoped APIs
- Backend implementation: Single endpoint; GET `/me/organizations` returns all orgs user is member of

**D-10: Response Structure (Finalized)**
- Returns array of org memberships with user/org context:
```json
{
  "organizations": [
    {
      "accountId": "2b8b74d7-a3f7-49e7-b397-6980280f2cbc",
      "organizationId": "a08867be-c683-40b3-a477-b491e6eb0ec7",
      "organizationName": "system-admin",
      "email": "admin@goaldone.de",
      "firstName": null,
      "lastName": null,
      "roles": ["COMPANY_ADMIN"],
      "hasConflicts": false
    }
  ]
}
```
- Key design:
  - Root key is `organizations` (not `accounts`)
  - User fields (`email`, `firstName`, `lastName`) appear **at org level** (per-org user context)
  - `roles` is an array (supports future multi-role scenarios)
  - `hasConflicts` indicates whether user has pending/unresolved actions in this org
- Ordering: Orgs ordered by `organizationName` (alphabetic) or by membership date (TBD during planning)

**D-11: OpenAPI Spec Update Required**
- Update `api-spec/openapi.yaml`:
  - Remove `GET /accounts` endpoint definition
  - Add `GET /me/organizations` endpoint definition
  - Add new `UserOrganization` model (or rename `AccountResponse`)
  - Update request/response documentation
- Regenerate: After spec update, run:
  - Backend: `./mvnw generate-sources` (regenerate API interfaces/models)
  - Frontend: `npm run generate-api` (regenerate TypeScript API client)
- API versioning: No version change needed; endpoint replacement is considered breaking (but occurs once, cleanly)

**D-12: No Backward Compatibility**
- Old `/accounts` endpoint: **Deprecated and removed** (Phase 7 frontend already uses new structure internally)
- No dual-endpoint period: Single clean transition
- Impact: Any external clients using `/accounts` must update; internal frontend is already Phase 7 compatible

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core Requirements & Roadmap
- `.planning/ROADMAP.md` — Phase 8 goal and dependencies
- `.planning/REQUIREMENTS.md` — v1 requirements covering auth, member management, authorization (still in progress; Phase 8 is new feature, not core requirement yet)

### Prior Phase Decisions (Critical for Phase 8)
- `.planning/phases/07-fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service/07-CONTEXT.md` — Frontend integration decisions, auth service patterns, org context management
- `.planning/phases/05-member-management-rewrite-and-cutover/05-CONTEXT.md` — Member management API contracts (referenced for role handling)
- `.planning/phases/04-frontend-auth-switch/04-CONTEXT.md` — OIDC flow patterns (reset flow may integrate with these)
- `.planning/phases/02-backend-jwt-validation/02-CONTEXT.md` — JWT token structure, authorities claim format

### Auth-Service Implementation Artifacts
- `auth-service/src/main/java/de/goaldone/authservice/` — Auth-service Spring Boot application
- `auth-service/src/main/resources/templates/` — HTML templates for login, consent, error pages (to be updated)
- `auth-service/src/main/java/de/goaldone/authservice/config/SecurityConfig.java` — Security configuration
- Auth-service database schema — User table (email, password hash, first/last name, created_at, updated_at)

### Backend Implementation Artifacts
- `api-spec/openapi.yaml` — API specification (must be updated for `/me/organizations` endpoint)
- `backend/src/main/java/de/goaldone/backend/controller/AccountController.java` (or similar) — Current `/accounts` endpoint implementation
- Backend tests for `/accounts` endpoint — Must be updated to test `/me/organizations`

### Frontend Integration Points
- `frontend/src/app/core/auth/auth.service.ts` — May need updates for password reset redirect or org context
- `frontend/src/app/features/` — Components that call `/accounts` endpoint (must update to `/me/organizations`)
- `frontend/src/assets/env.js` — May need auth-service password reset URL configuration

### Email & Templating
- Auth-service email sending capability (SMTP configuration, template library)
- Password reset email template (to be created in Phase 8)
- Email styling guide (align with GoalDone branding)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Login page template** — Existing auth-service login page (HTML/CSS) can be refactored or replaced with template library
- **Email sending infrastructure** — Auth-service may already have email capabilities (Spring Mail); password reset can reuse
- **Form validation** — Both auth-service and backend use form validation; password reset form reuses existing patterns
- **Error handling** — Backend exception/error response patterns can inform auth-service password reset error responses
- **JDBC client registry** — Auth-service already uses JDBC for client storage (from Phase 1); password reset tokens can use similar DB storage

### Established Patterns
- **Template library usage:** Frontend uses PrimeNG; auth-service login can use Bootstrap, Tailwind, or similar lightweight framework
- **Email templates:** Spring Boot supports Thymeleaf, FreeMarker, or simple string templates; recommend Thymeleaf for consistency with Spring
- **UUID identifiers:** All entities use UUIDs; reset token should be UUID v4
- **Timestamp-based expiration:** Consistent with auth-service token expiration logic (Phase 1)
- **Token storage:** JDBC DB storage for password reset tokens (parallel to registered clients from Phase 1)

### Integration Points
- **Auth-service startup:** Password reset feature must not break OIDC flow
- **Email configuration:** .env variables for SMTP settings (host, port, from address, credentials)
- **API contract changes:** OpenAPI spec update → code generation in both backend and frontend
- **Frontend API calls:** Password reset link from auth-service; `/me/organizations` endpoint call from frontend components

</code_context>

<specifics>
## Specific Ideas for Planning

### Login Page Redesign
- **Template library selection:** Evaluate Bootstrap 5, Tailwind CSS, or Material Design Lite for lightweight, maintainable login page
- **Color scheme:** Extract GoalDone primary/secondary colors from frontend theme; apply to login page
- **Error message styling:** Use PrimeNG alert styles or equivalent (consistent with frontend)
- **Password toggle icon:** Use PrimeIcons (same as frontend) or Font Awesome for consistency
- **Responsive design:** Ensure login page works on mobile (responsive grid, touch-friendly buttons)
- **Accessibility:** WCAG 2.1 AA compliance (labels, contrast, keyboard navigation)

### Password Reset Flow
- **Token generation:** Use `UUID.randomUUID()` for reset token; store in `password_reset_tokens` table
- **Token fields:** id (UUID), user_id (UUID), token_hash (bcrypt), created_at, used_at (null until reset)
- **Email sending:** Use Spring Mail with Thymeleaf template; async (non-blocking) sending
- **Reset form:** POST `/reset-password` with `token` (URL param) and `newPassword` (form field)
- **Validation:** Check token exists, not expired, not already used; validate password strength
- **Cleanup:** Periodic cleanup of expired/used tokens (cron job or lazy deletion)

### API Endpoint Refactoring
- **OpenAPI update:** Define `GET /me/organizations` with 200 response, 401 Unauthorized (no auth header), 403 Forbidden (user deleted)
- **Model definition:** Create `UserOrganization` or `OrganizationMembership` DTO; add to openapi.yaml
- **Backend migration:** Update `AccountController` → `UserOrganizationController` (or add method to existing controller)
- **Frontend update:** Update all references to `/accounts` in API calls; verify test mocks
- **Deprecation period:** Optional v2 roadmap item: keep `/accounts` for one release with deprecation header, then remove

### Testing Strategy
- **Login page:** Manual browser test (Chrome, Firefox, Safari); verify form submission, errors, password toggle
- **Password reset:** Manual end-to-end test (request reset → email arrives → click link → new password → login with new password works)
- **Org endpoint:** Unit tests for `/me/organizations` response structure; integration tests with multi-org users; API contract tests with frontend

### Frontend Changes
- **Password reset link:** Add "Forgot password?" link to login page (if frontend hosts auth-service login, or update env.js with reset URL)
- **Org endpoint calls:** Update all `.getAccounts()` calls to `.getOrganizations()` (after API client regeneration)
- **Response parsing:** Update components expecting `accounts` array to expect `organizations` array

</specifics>

<deferred>
## Deferred Ideas (Post-Phase 8)

- **2FA/WebAuthn:** Advanced account security features deferred to v2
- **Social login:** Google, GitHub OAuth providers deferred to v2
- **Email verification:** Verify email on account creation deferred (assume emails valid at user creation)
- **Account lockout policy:** Lock account after N failed login attempts deferred; simple password validation sufficient for Phase 8
- **Rate limiting on password reset requests:** Allow unlimited requests per email (monitor abuse via logs); explicit rate limiting deferred to v2
- **Email confirmation for password reset:** Require user to confirm reset via secondary email deferred; simple link sufficient
- **Admin password reset:** SUPER_ADMIN forcing password reset on user account deferred to v2 admin console
- **Password history:** Prevent reuse of previous passwords deferred to v2
- **Passwordless authentication:** FIDO2, magic links, etc. deferred to v2
- **Single sign-out:** Logout across all sessions deferred to v2
- **Session management UI:** View/revoke active sessions deferred to v2 admin console
- **Audit logging for password resets:** Track who reset when deferred to v2 audit trail

</deferred>

---

*Phase: 08-auth-service-login-ui-password-reset-accounts-endpoint-roles*
*Context gathered: 2026-05-04*
*Status: Ready for planning — all gray areas resolved, implementation decisions locked*
