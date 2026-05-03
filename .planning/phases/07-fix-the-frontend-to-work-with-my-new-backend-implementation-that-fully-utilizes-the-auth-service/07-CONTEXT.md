# Phase 7: Fix the Frontend to Work with New Backend - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 7 is a comprehensive integration and verification phase that ensures the frontend works correctly end-to-end with the new auth-service-based backend (after Phases 1-6 complete their implementations).

The scope covers:
1. **End-to-end verification** of the OIDC login flow, token lifecycle, and multi-org provisioning
2. **API alignment** between frontend expectations and backend API signatures (from Phase 5)
3. **Member management UI** fixes to match new Phase 5 API contracts
4. **Multi-org UI** implementation with proper org context management and deprecated method refactoring
5. **Frontend test restoration** for auth services and components to work with new auth-service token format
6. **Error handling** implementation with user-friendly messages for common auth/org failures

Result: A fully functional frontend that integrates seamlessly with the auth-service backend, with all core workflows (login, task creation, member management, multi-org access) working end-to-end and covered by tests.

</domain>

<decisions>
## Implementation Decisions

### End-to-End Verification Strategy
- **D-01: Manual Walkthrough + Automated Tests.** Phase 7 includes both:
  - Manual walkthrough of core user workflows to identify integration breaks early
  - Automated frontend unit/integration tests to prevent regressions and document expected behavior
  - Not just backend trust; proactive frontend verification

### Login Flow & Token Lifecycle
- **D-02: Full OIDC Flow Re-verification.** Re-verify the complete chain:
  - env.js configuration loading
  - OIDC discovery document resolution from auth-service
  - Authorization code flow with PKCE
  - Token exchange with auth-service
  - JWT decoding and claim extraction (authorities, orgs, user_id)
  - Multi-org provisioning (orgs claim array → OrgContextService)
  - First successful request to backend with valid token
  - Ensures Phase 4 integration works correctly with live backend

### Member Management UI
- **D-03: Full Scope Member Management Fixes.** Phase 7 will fix/verify all member management workflows:
  - Invitation dialog → send invitation via auth-service API
  - Invitation acceptance flow (invited user joins org)
  - Role change dialog → update member role via auth-service
  - Member removal with last-admin guard validation
  - All component signatures and API calls aligned with Phase 5 backend contract

### Multi-Org UI Implementation
- **D-04: Full Multi-Org UI Coverage.** Implement and verify:
  - Org dropdown in Add Task dialog (appears if user in multiple orgs)
  - Org dropdown in Add Worktime dialog (appears if user in multiple orgs)
  - Org dropdown in Company Settings page (appears if admin in multiple orgs)
  - Org context persists for dialog operations, clears on dialog close
  - Org context persists for settings page, clears on navigation away
  - First login defaults to first org in JWT orgs claim
  - No org dropdown in single-org mode (hidden from UI)

- **D-05: Deprecated Method Refactoring.** Remove usage of deprecated auth service methods:
  - Replace `getUserOrganizationId()` with `getActiveOrganization()` or `getOrganizations()`
  - Replace `getUserMemberships()` with `getOrganizations()`
  - Audit all components for these deprecated calls and update

### API Signature Alignment
- **D-06: Proactive API Scan & Regeneration.** Before fixing issues:
  - Run `npm run generate-api` to regenerate TypeScript API client from openapi.yaml
  - Compare generated API shapes against current component usage
  - Identify all mismatches (parameter types, new required fields, response shape changes)
  - Fix all identified mismatches systematically
  - Use openapi.yaml as source of truth for API contracts (Phase 5 may have changed them)

### Frontend Test Coverage
- **D-07: Comprehensive Test Updates.** Update all frontend spec files:
  - **Auth layer:** auth.service.spec.ts, auth.interceptor.spec.ts
    - Test with auth-service token format (authorities claim, orgs array)
    - Test token refresh logic (on-demand, 5-minute buffer)
    - Test X-Org-ID header conditional injection
    - Test org context service integration
  - **Component layer:** All specs for components that call API services
    - Update mocks to match new API shapes
    - Test org context resolution (dialog vs settings vs default)
    - Test error scenarios (403, 410, 409 responses)
  - **Target:** All frontend tests pass; no failures from API shape changes

### Error Handling & User Messaging
- **D-08: Implement Error Handling with User Messages.** Add graceful error handling for common failures:
  - **403 FORBIDDEN:** User lacks access to organization. Message: "You don't have permission to access this organization. Contact your admin."
  - **410 GONE:** Link token expired (account linking, invitation). Message: "This link has expired. Please request a new one."
  - **409 CONFLICT:** Operation conflict (already linked, already member, duplicate). Message: "This action cannot be completed. Please refresh and try again."
  - **4xx/5xx Network errors:** Generic backend error. Message: "Something went wrong. Please try again or contact support."
  - **Network/timeout errors:** Connection issue. Message: "Unable to connect to server. Check your connection and try again."
  - Implement in `authInterceptor` and service error handlers
  - Display via toast notifications or inline error messages in dialogs

### Scope Boundaries
- **D-09: Phase 7 Does Not Expand Scope Beyond Integration.** Phase 7 focuses on fixing the frontend to work with the new backend. It does not:
  - Add new features beyond what was specified in Phases 1-6
  - Enhance auth-service beyond fixing bugs to align with backend
  - Add new UI components or refactor existing design
  - Scope creep is redirected to post-Phase 7 backlog

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core Requirements & Roadmap
- `.planning/ROADMAP.md` — Phase 7 goal and context (integration phase after Phase 6)
- `.planning/REQUIREMENTS.md` — v1 requirements FE-01 through FE-06, TEST-05

### Prior Phase Decisions (Critical for Phase 7)
- `.planning/phases/02-backend-jwt-validation/02-CONTEXT.md` — JWT token contract: flat `authorities` claim, `user_id`, multi-org `orgs` array
- `.planning/phases/04-frontend-auth-switch/04-CONTEXT.md` — Frontend OIDC implementation, role extraction, org context management decisions (D-01 through D-15)
- `.planning/phases/05-member-management-rewrite-and-cutover/05-CONTEXT.md` — Member management API changes, UUID member IDs, auth-service management API contracts
- `.planning/phases/06-backend-error-fix-and-test-restoration/06-CONTEXT.md` — Backend error handling, test restoration patterns

### Frontend Implementation Artifacts
- `frontend/src/app/core/auth/auth.service.ts` — Core OIDC and token management; verify works with live auth-service
- `frontend/src/app/core/auth/auth.interceptor.ts` — Bearer token injection and X-Org-ID header logic; verify conditional header injection
- `frontend/src/app/core/services/org-context.service.ts` — Org context management (dialog, settings, default); verify persistence/clearing
- `frontend/src/assets/env.js` — Runtime configuration for issuerUri, clientId, apiBasePath; verify env.js injection works
- `frontend/src/app/app.config.ts` — App initialization; calls AuthService.initialize()

### API Specification (Source of Truth)
- `api-spec/openapi.yaml` — All API endpoint definitions, parameter types, response shapes. Phase 5 may have changed member management endpoints. Use this to regenerate API client.
- Generated API client: `frontend/src/app/api/` (git-ignored; regenerated via `npm run generate-api`)

### Member Management Context
- Member invite/accept flows and API contracts
- Role change and member removal operations
- Last-admin guard constraints
- Error responses (409 CONFLICT, 403 FORBIDDEN)

### Test Files
- `frontend/src/app/core/auth/auth.service.spec.ts` — Existing auth service tests; update for auth-service token format
- `frontend/src/app/core/auth/auth.interceptor.spec.ts` — Existing interceptor tests; verify X-Org-ID header logic, token refresh
- All component `.spec.ts` files that call API services — update mocks for new API shapes

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **AuthService** (`auth.service.ts`) — Already implements OIDC with auth-service configuration, token decoding, role extraction from authorities/orgs claims. Just needs end-to-end verification.
- **authInterceptor** (`auth.interceptor.ts`) — Already implements token injection and conditional X-Org-ID header. Needs verification and error handling enhancement.
- **OrgContextService** (`org-context.service.ts`) — Manages dialog/settings/default org context. Needs verification of persistence/clearing logic.
- **Existing PrimeNG components** — Dropdown, Dialog, Toast components for UI. Can be reused for org selector and error messages.
- **SharedWiremockSetup pattern** (from backend) — Can be referenced for frontend test mocking patterns if needed.

### Established Patterns
- **Environment injection via env.js:** Issuer, client ID, API base path injected at runtime. Works for dev/staging/prod configuration.
- **PKCE code flow with angular-oauth2-oidc:** Implemented and working. Just needs live backend verification.
- **PrimeNG for UI components:** All new UI (org dropdowns, error toasts) should use PrimeNG components.
- **RxJS for async operations:** Token refresh, API calls all use Observables/Promises. Established pattern.
- **Lazy-loaded feature modules:** Core auth in standalone, features lazy-loaded. Org context service injectable sitewide.

### Integration Points
- **App initialization (app.config.ts):** Where AuthService.initialize() is called on app startup
- **All API service calls:** Protected by authInterceptor (token injection, org context, error handling)
- **Member management dialogs:** Should have org dropdown (Add Member) and handle 403/409 errors gracefully
- **Task/Worktime dialogs:** Should have org dropdown if multi-org user
- **Settings page:** Org dropdown if multi-org admin; persists context for admin operations

</code_context>

<specifics>
## Specific Ideas

- **Manual Test Checklist:** Create a checklist of core flows to manually test: single-org login → multi-org login → add task with org selection → add worktime → invite member → change role → remove member → logout. Document pass/fail for each.
- **API Regeneration First:** Before implementing, run `npm run generate-api` and inspect `frontend/src/app/api/` to see what changed from Phase 5. This informs all downstream work.
- **Token Expiry Simulation:** Test token refresh logic by setting a short expiry in dev auth-service and forcing refresh during a long operation.
- **Error Testing:** Manually trigger 403 (try accessing org user isn't member of), 410 (expire a link token), 409 (duplicate member invite) scenarios to verify error messages display correctly.
- **Deprecated Method Audit:** `grep -r "getUserOrganizationId\|getUserMemberships" frontend/src/` to find all deprecated usage and prioritize replacement.
- **Multi-Org Single-Org Toggle:** Test both single-org (one org in JWT) and multi-org (multiple orgs in JWT) scenarios to verify UI dropdowns appear/disappear correctly.

</specifics>

<deferred>
## Deferred Ideas

- **Social login:** Google, GitHub OAuth providers deferred to v2.
- **2FA/WebAuthn:** Advanced account security deferred to v2.
- **New frontend features:** Any feature not required by Phases 1-6 requirements deferred to Phase 7+ backlog.
- **Performance optimization:** Frontend bundle size, lazy loading optimization deferred to optimization phase.
- **UI/UX redesign:** Component redesign, layout improvements deferred to design phase.

</deferred>

---

*Phase: 07-fix-the-frontend-to-work-with-my-new-backend-implementation-that-fully-utilizes-the-auth-service*
*Context gathered: 2026-05-03*
