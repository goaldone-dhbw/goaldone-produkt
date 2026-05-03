# Phase 4: Frontend Auth Switch - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

The core goal of this phase is to migrate the Angular frontend from Zitadel's OIDC provider to the custom auth-service. This includes updating the OIDC issuer configuration, adapting role extraction logic to read from the new flat `authorities` claim, and implementing a context-aware org selection UI that leverages the multi-org infrastructure built in Phase 3.1.

The result: end-to-end PKCE code flow with auth-service, correct role display, and smart org dropdowns that appear only where needed.

</domain>

<decisions>
## Implementation Decisions

### OIDC & Token Management
- **D-01: Issuer via env.js.** The auth-service issuer URL is configured at runtime via `frontend/src/assets/env.js`, following the current pattern. Environment-specific issuer URLs (dev, staging, prod) are injected before app boot.
- **D-02: localStorage for Token Storage.** Access and refresh tokens are stored in `localStorage` (current pattern). Allows persistence across tab refreshes and session restoration.
- **D-03: On-Demand Token Refresh.** Token refresh happens per-request (checked before API calls), not via background silent refresh. The `authInterceptor` will validate token expiry and call the refresh endpoint if needed before each request.
- **D-04: Revoke on Logout.** When user logs out, the frontend first calls the auth-service token revocation endpoint to invalidate the refresh token, then clears local state.

### Role Extraction & Display
- **D-05: Per-Org Role Structure.** `AuthService.getUserRoles()` returns an object mapping roles by organization: `{ org1_id: ['ROLE_ADMIN'], org2_id: ['ROLE_MEMBER'] }`. This enables context-aware role checks and org-specific visibility.
- **D-06: Roles from 'authorities' Claim.** The `authorities` claim in the JWT contains the flat list of roles. These are mapped to org contexts using the `orgs` claim array structure.
- **D-07: Roles in Member Lists Only.** User roles are displayed to the UI only in member management views (organization member lists, user profiles). No role badges in headers or global navigation. Roles are used internally for authorization checks via `@PreAuthorize` (backend) and custom visibility directives (frontend).

### Multi-Org Context & Selection
- **D-08: Conditional Org Dropdowns.** Org selection dropdowns appear only where contextually relevant:
  - **Add Task Dialog:** Dropdown present if user is in multiple orgs. Allows selecting which org to add the task to.
  - **Add Worktime Dialog:** Dropdown present if user is in multiple orgs. Allows selecting which org the worktime is for.
  - **Company Settings Page:** Dropdown present if user is admin in multiple orgs. Allows switching context for that page session.
  - **All Other Pages:** No org dropdown if user is in single org. No visible org selector in header or sidebar.
- **D-09: Dialog-Scoped Org Selection.** When a user selects an org in a dialog (Add Task, Add Worktime), that selection applies only to that dialog. Closing the dialog does not persist the selection globally. Each dialog opens with no pre-selected org.
- **D-10: Page-Scoped Org Selection in Settings.** In company settings, when user selects an org from the dropdown, that org context persists for all actions on that page (e.g., member list filtering, admin actions). Navigating away from settings clears the context.
- **D-11: All Orgs in List Views.** List endpoints (GET /tasks, GET /worktimes) always return data from all organizations the user is member of. The `X-Org-ID` header is NOT sent for read-only list operations. This provides a unified view across orgs.
- **D-12: First Login Default.** After successful authentication, the frontend loads data from all orgs and defaults the "active org" context to the first org in the JWT's `orgs` claim array. No modal or forced selection — seamless entry into the app.

### Frontend Interceptor & Header Management
- **D-13: Conditional X-Org-ID Header.** The `authInterceptor` will conditionally inject the `X-Org-ID` header:
  - **For destructive/creative operations (POST, PUT, DELETE):** Always include `X-Org-ID` from the context selected in the dialog/page. If no org is selected, the interceptor will either use the default (first org) or require the caller to explicitly pass an org context.
  - **For list operations (GET):** Do NOT include `X-Org-ID`. Return all-org data.
  - **For member management endpoints (GET /organization/members):** Always include `X-Org-ID` to filter members by org.

### Token Refresh & Session Management
- **D-14: Per-Request Refresh Check.** The `authInterceptor` will check token expiry before every request. If expired or near expiry, it will call the auth-service refresh endpoint synchronously (or queue requests until refresh completes). No background polling or silent refresh worker.
- **D-15: Refresh Token Scope.** Refresh tokens are obtained with the `offline_access` scope (as configured in auth-service Phase 1). Tokens are stored alongside access tokens in `localStorage`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core Requirements & Roadmap
- `.planning/ROADMAP.md` — Phase 4 goal and success criteria.
- `.planning/REQUIREMENTS.md` — Requirements FE-01 through FE-06 and TEST-05.

### Prior Phase Decisions
- `.planning/phases/02-backend-jwt-validation/02-CONTEXT.md` — Token contract: flat `authorities` claim, `user_id`, `orgs` claim structure.
- `.planning/phases/03.1-refine-organization-context-and-header-requirements/3.1-CONTEXT.md` — X-Org-ID header pattern, `hasOrgRole` expression, org context resolution.

### Frontend Auth Implementation
- `frontend/src/app/core/auth/auth.service.ts` — Core OIDC service; needs updating for new issuer config and role extraction.
- `frontend/src/app/core/auth/auth.interceptor.ts` — HTTP interceptor that injects Bearer tokens and X-Org-ID header; needs conditional header injection logic.
- `frontend/src/assets/env.js` — Runtime configuration file where issuer URL is injected.
- `frontend/src/app/app.config.ts` — Angular app initialization; calls AuthService.initialize().

### API Specification
- `api-spec/openapi.yaml` — Single source of truth for API endpoints; should include X-Org-ID header specification (from Phase 3.1).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuthService` in `auth.service.ts` — Existing OIDC wrapper around `angular-oauth2-oidc`. Can be refactored to:
  - Accept auth-service issuer from env.js
  - Extract roles from `authorities` claim instead of Zitadel's nested structure
  - Build per-org role mapping
- `authInterceptor` in `auth.interceptor.ts` — Already injects Bearer tokens. Can be enhanced to:
  - Implement on-demand token refresh
  - Conditionally inject X-Org-ID header based on request method
  - Handle refresh token exchange

### Established Patterns
- **Environment Injection via env.js:** Proven pattern for runtime config. Zitadel issuer, client ID, and scopes are already injected this way.
- **Angular OIDC with PKCE:** The `angular-oauth2-oidc` library is configured for PKCE code flow. Auth-service supports the same flow — no library changes needed.
- **PrimeNG Components:** Any org selector dropdown will use PrimeNG dropdown component (consistent with existing UI).

### Integration Points
- **AppConfig (app.config.ts):** Where AuthService.initialize() is called. Must pass auth-service issuer from env config.
- **Feature Components:** Any component that creates/edits tasks or worktimes will receive the selected org from a parent dialog context (e.g., via service or route params).
- **Company Settings Page:** Will host the org selector dropdown (if user is admin in multiple orgs).

</code_context>

<specifics>
## Specific Ideas

- **Token Expiry Buffer:** Consider a 5-10 minute buffer when checking token expiry in the interceptor. Refresh if less than 5 min remaining, not at exact expiry time.
- **Refresh Token Exchange:** The auth-service must support the `refresh_token` grant type with the `offline_access` scope (Phase 1 requirement AUTH-06).
- **Org Context Service:** Consider a shared service (`OrgContextService`) to manage the selected org for dialogs and settings pages. This centralizes the per-dialog org selection logic.
- **No Token Validation UI:** The frontend does not validate the issuer or JWKS signature. Leave that to the backend. The frontend simply stores and sends the token.

</specifics>

<deferred>
## Deferred Ideas

- **Social Login:** Google, GitHub OAuth2 providers deferred to v2.
- **2FA/WebAuthn:** Advanced account security deferred to v2.
- **Account Linking / Email Aliases:** New single-account model eliminates this need; deferred indefinitely.
- **Org Admin Console:** Full admin UI for auth-service management; MVP uses management API only (Phase 5).

</deferred>

---

*Phase: 04-frontend-auth-switch*
*Context gathered: 2026-05-03*
