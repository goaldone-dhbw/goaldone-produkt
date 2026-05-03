# Phase 4: Frontend Auth Switch - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 04-frontend-auth-switch
**Areas discussed:** OIDC & Token Management, Role Extraction & Display, Multi-Org Context

---

## OIDC & Token Management

### Issuer Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Via env.js (current approach) | Runtime environment injection, same pattern as Zitadel; issuer URL differs per environment | ✓ |
| Via HTTP discovery | Frontend queries config endpoint to fetch issuer URL dynamically from backend | |
| Build-time constant | Issuer URL baked into frontend build; redeploy for environment changes | |

**User's choice:** Via env.js (current approach)
**Notes:** Consistent with existing Zitadel pattern; proven reliable for multi-environment deployments.

---

### Token Storage

| Option | Description | Selected |
|--------|-------------|----------|
| localStorage (current pattern) | Persistent across tabs/refreshes; standard for angular-oauth2-oidc; XSS risk if not mitigated | ✓ |
| sessionStorage | Cleared on tab close; more secure; loses auth on page refresh (requires re-login) | |
| Memory only | No persistence; safest against XSS; requires fresh login on page refresh; not ideal for SPAs | |

**User's choice:** localStorage (current pattern)
**Notes:** Maintains current storage strategy; supports seamless session recovery.

---

### Token Refresh Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Silent refresh (automatic) | angular-oauth2-oidc watches token expiry and refreshes silently in background | |
| On-demand refresh (per request) | Frontend checks token expiry before each API call; refreshes if needed | ✓ |
| Manual re-login | When token expires, user is redirected to login | |

**User's choice:** On-demand refresh (per request)
**Notes:** Simpler implementation; avoids background token refresh workers; checks happen naturally with API calls.

---

### Logout & Token Revocation

| Option | Description | Selected |
|--------|-------------|----------|
| Silent logout (clear local state) | Clear tokens from localStorage and redirect to login. Don't notify auth-service. | |
| Revoke refresh token first | Call auth-service revocation endpoint before clearing local state. Ensures refresh token can't be reused. | ✓ |

**User's choice:** Revoke refresh token first
**Notes:** Explicit revocation prevents token reuse after logout; adds security layer.

---

## Role Extraction & Display

### Role Data Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Return flat array of strings | E.g., ['ROLE_ADMIN', 'ROLE_MEMBER']. UI checks string membership. | |
| Return object mapping roles per org | E.g., { org1: ['ROLE_ADMIN'], org2: ['ROLE_MEMBER'] }. Supports showing roles in context. | ✓ |
| You decide | Claude determines based on codebase usage. | |

**User's choice:** Return object mapping roles per org
**Notes:** Enables context-aware role checks aligned with multi-org architecture; supports org-specific member role display.

---

### Role Display in UI

| Option | Description | Selected |
|--------|-------------|----------|
| Hidden from users | Roles used only for authorization (visibility checks, button enable/disable). No labels shown. | |
| Display in member lists only | Show roles when viewing org members or user profile. Nowhere else. | ✓ |
| Display in header/sidebar | Show current user's role(s) for active org in header/nav. Always visible. | |

**User's choice:** Display in member lists only
**Notes:** Keeps UI clean; roles are primarily for internal authorization logic and member management context.

---

## Multi-Org Context

### Org Selection UI

**User's custom answer:**
> "Only while adding Tasks etc. that can be added to any org there should BE a dropdown in the Add Tasks Dialog. Same with worktimes etc.
> 
> Only in the company settings there should BE a dropdown if the User is Admin in more than one org and then automatically keep the org context for the Next actions a User is fulfilling in this Page.
> 
> If the User is only in one org then dont Show any dropdowns."

**Captured as D-08:** Conditional org dropdowns in Add Task, Add Worktime dialogs, and company settings page (if admin in multiple orgs).

---

### Org Selection Persistence

| Option | Description | Selected |
|--------|-------------|----------|
| For that dialog only | Selection resets when dialog closes; no persistent context change. | ✓ |
| For the current page session | User selects in settings → all actions on that page use that org. Navigate away → context clears. | |
| Globally (until manually changed) | User selects anywhere → becomes global 'active org' for all subsequent actions. | |

**User's choice:** For that dialog only
**User's additional note:** "btw loading Tasks etc. should Always load all orgs"

**Captured as D-09 & D-11:**
- Dialog org selection is local to that dialog
- List endpoints always load all orgs (no X-Org-ID header for reads)

---

### First Login with Multiple Orgs

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-select first org | Default to their first org from JWT. No prompt. | |
| Show org selection modal | Prompt user to choose which org to start in. Required before accessing app. | |
| Load all orgs, show data from all | No 'active org' initially. All pages show data from all orgs. | ✓ |

**User's choice:** Load all orgs, show data from all and then select the first one
**Captured as D-12:** After successful authentication, load data from all orgs and default "active org" context to first org in JWT's `orgs` claim.

---

## Claude's Discretion

- **Token Expiry Buffer:** Claude will determine an appropriate buffer time (e.g., 5-10 min) for refresh checks.
- **Org Context Service:** Claude may introduce a shared `OrgContextService` to centralize org selection logic across dialogs.
- **Refresh Token Exchange Flow:** Claude will implement the exact refresh flow using auth-service endpoints.

---

## Deferred Ideas

- **Social Login (v2):** Google, GitHub OAuth2 providers — out of scope for v1; noted for future release.
- **2FA/WebAuthn (v2):** Advanced security features — deferred to v2.
- **Admin Console for Auth-Service:** Full management UI — phase 5 uses management API only; UI deferred to v2.

---

*Discussion logged: 2026-05-03*
