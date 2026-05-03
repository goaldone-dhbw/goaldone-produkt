# Feature Research

**Domain:** Multi-tenant SaaS authentication migration — Zitadel to custom auth-service
**Researched:** 2026-05-02
**Confidence:** HIGH (based on direct codebase analysis of both auth-service and backend)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete or broken.

| Feature | Why Expected | Complexity | Status in auth-service | Notes |
|---------|--------------|------------|------------------------|-------|
| Login with email/password | Baseline access to the application | LOW | DONE — Spring Authorization Server with form login, `CustomUserDetailsService` loads user by email, `CustomUserDetails` populates JWT | Only verified emails allowed to login (enforced in `CustomUserDetailsService`) |
| Logout / session invalidation | Users expect session termination to work | LOW | DONE (form login handles logout) | Password reset also invalidates sessions via `SessionRegistry` |
| JWT issuance with org-aware claims | Backend must read roles and user identity from token | MEDIUM | DONE — `TokenCustomizerConfig` emits `authorities`, `emails`, `primary_email`, `user_id` | Roles are flat `authorities` strings; backend `SecurityConfig` must be updated to read from `authorities` claim instead of Zitadel's nested `urn:zitadel:iam:org:project:roles` map |
| JWKS endpoint for backend token validation | Backend resource server must validate signatures | LOW | DONE — RSAKey generated in `AuthorizationServerConfig`, standard Spring Authorization Server JWKS endpoint at `/.well-known/oauth-authorization-server` | Current RSA key is in-memory (ephemeral); must be persisted for production |
| Password reset via email | Self-service credential recovery | MEDIUM | DONE — `PasswordResetController` handles forgot/reset flow with `VerificationToken`, enumeration protection present | Session invalidation after reset is implemented |
| Email verification for new accounts | Prevent abandoned / fake registrations | MEDIUM | PARTIAL — `UserEmail.verified` field exists, `VerificationToken` with `TokenType` exists, but no standalone EMAIL_VERIFICATION token type or flow controller found | `TokenType` enum only has `INVITATION` and `PASSWORD_RESET`; email verify flow for self-registration not wired |
| Member invitation flow (invite by email) | Org admins must onboard teammates | HIGH | DONE — Full flow: `InvitationManagementService.createInvitation` sends email, `InvitationController` handles landing page, `activateUser` creates account on first accept | Role on invitation is hardcoded to `Role.USER`; inviter role is not enforced |
| Invitation acceptance for new users (set password) | Invited users must complete account creation | MEDIUM | DONE — `InvitationController.setPassword` / `InvitationManagementService.activateUser` | Password confirmation validation is in controller |
| Invitation acceptance for existing users (account linking) | Invited email may belong to an existing account | HIGH | DONE — `InvitationManagementService.handleAccountLinking` adds secondary email, creates membership, sends confirmation email | `AccountLinkingAuthenticationProvider` is a helper only; actual PKCE linking flow through OAuth2 not fully wired |
| Role-based access control (COMPANY_ADMIN / USER) | Org admins need elevated permissions | MEDIUM | DONE — `Role` enum, `Membership` entity, `authorities` claim in JWT | Backend `SecurityConfig` must be updated to read flat `ROLE_COMPANY_ADMIN` from `authorities` claim |
| Membership removal (member leaves or is removed) | Admin must be able to remove users from org | MEDIUM | PARTIAL — `MembershipManagementController.deleteMembership` has last-admin guard, but service call is a TODO comment | `userManagementService.isLastCompanyAdmin` is implemented; actual delete not wired |
| Membership role change | Admin promotes/demotes members | MEDIUM | PARTIAL — `MembershipManagementController.updateMembershipRole` has last-admin guard, but service call is a TODO comment | Same gap as removal |
| List org members | Admins and members need to see who is in their org | LOW | MISSING — No controller or service method to list members of a company | Required for frontend member management page |
| Backend JIT provisioning from auth-service JWT | Backend must create local `UserAccountEntity` on first login | MEDIUM | MISSING — Current `JitProvisioningService` reads Zitadel-specific claims (`urn:zitadel:iam:user:resourceowner:id`, `zitadelSub`); must be rewritten to read `user_id` from auth-service JWT | This is the critical bridge; without it the backend cannot associate requests to local users |
| Backend claim extractor update | Backend `SecurityConfig` extracts roles from new claim format | LOW | MISSING — Current converter reads `urn:zitadel:iam:org:project:roles` map; auth-service emits flat `authorities` list | One-line change in `jwtAuthenticationConverter`, but a blocking dependency for all protected endpoints |
| Frontend OIDC config update | Frontend must point to auth-service discovery URL | LOW | MISSING — `window.__env` OIDC config still points to Zitadel | Once auth-service issuer URL is set, `angular-oauth2-oidc` should work without code changes |
| Registered client config (persistence) | Frontend OAuth2 client must be registered with auth-service | MEDIUM | PARTIAL — `AuthorizationServerConfig` uses `InMemoryRegisteredClientRepository` with hardcoded test client; frontend client not registered, and config is ephemeral | Must move to DB-backed `JdbcRegisteredClientRepository` or externalized config before go-live |
| RSA key persistence | Token signatures must survive restarts | MEDIUM | MISSING — `AuthorizationServerConfig.generateRsaKey()` creates a new in-memory key pair on every startup; all issued tokens become invalid on restart | Critical for production; use externalized key (env var, vault, or DB-backed `JdbcOAuth2AuthorizationService`) |

---

### Differentiators (What Simplifies in the New Model)

These are features or simplifications the new single-account model unlocks compared to Zitadel.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Single user account across orgs | User has one login, joins multiple orgs via memberships — no linked-account confusion | LOW (already modeled) | `User` + `Membership` model is already the right shape; Zitadel's linked-account complexity disappears |
| Invitation role assignment (not just USER) | When inviting, org admin can specify whether the invitee joins as COMPANY_ADMIN or USER | LOW | Currently hardcoded to `Role.USER` in `activateUser`; the `InvitationRequest` just needs a `role` field |
| Account linking via invitation email match | Existing user invited to second org gets secondary email added and membership created without creating a duplicate account | HIGH (already implemented) | `handleAccountLinking` is fully implemented; the missing piece is the PKCE OAuth2 redirect during the invite acceptance to authenticate the existing user |
| Self-managed org onboarding | Auth-service owns invitation, verification, and membership creation — no Zitadel Management API calls required | MEDIUM | All Zitadel Management API calls (`ZitadelManagementClient`) in backend become internal auth-service calls |
| Last-admin guard | Business rule: cannot remove or demote the last COMPANY_ADMIN or SUPER_ADMIN | LOW (already implemented) | `isLastCompanyAdmin` and `isLastSuperAdmin` are fully implemented in `UserManagementService` |
| Invitation expiration cleanup | Expired invitations are cleaned up automatically | LOW (already implemented) | `InvitationExpirationCleanupJob` exists |
| Audit trail for auth operations | `AuditService` exists as a stub; can be wired to critical flows | MEDIUM | Not currently called from controllers; adding calls is straightforward |

---

### Anti-Features (Things to Deliberately NOT Build or Simplify)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| OAuth2 social login (Google, GitHub) | Users want convenience | Out of scope for this migration timeline; adds complexity to registration and account-linking flows; requires separate OIDC client registration per provider | Explicitly deferred to v2 per PROJECT.md |
| Two-factor authentication | Security best practice | Significant auth-service UI and backend complexity; not needed for migration baseline | Deferred to v2 per PROJECT.md |
| Admin RBAC for auth-service management API | Fine-grained access control on `/api/v1/**` | The management API (`/api/v1/users`, `/api/v1/organizations`) is currently callable by any authenticated client with a valid JWT; RBAC design needs thought | Defer: require `mgmt:admin` scope via `client_credentials` for machine-to-machine calls for now |
| Self-service organization creation | Any user creates their own org | Not aligned with current GoalDone model (orgs are created by admins or during onboarding); opens flood risk | Keep org creation as an admin/admin-API-only operation |
| Gradual migration / dual-IdP | Run Zitadel and auth-service in parallel | Doubles maintenance surface; token format differences cause integration complexity | Complete cutover is the correct approach per PROJECT.md |
| Email change without re-verification | Users want to update primary email instantly | Risk of account takeover if new email is not verified first | Require verification token for any primary email change |
| Remember me / persistent sessions | Users want to stay logged in | Auth-service uses Spring Session; adding persistent sessions requires DB-backed session store and complicates logout | Defer; standard session timeout is sufficient for v1 |

---

## Feature Dependencies

```
[Backend JWT claim extraction from auth-service]
    └──requires──> [auth-service RSA key configured and JWKS endpoint live]
    
[Backend JIT provisioning from auth-service JWT]
    └──requires──> [auth-service emits user_id claim]  (DONE in TokenCustomizerConfig)
    └──requires──> [Backend SecurityConfig reads authorities instead of Zitadel claim]
    
[Frontend OIDC login via auth-service]
    └──requires──> [Registered client in auth-service for Angular frontend]
    └──requires──> [auth-service issuer URL configured in window.__env]
    
[Member invitation (new user path)]
    └──requires──> [auth-service invitation flow live (DONE)]
    └──requires──> [Backend MemberInviteService replaced: no more ZitadelManagementClient calls]
    
[Member invitation (existing user / account linking path)]
    └──requires──> [Invitation flow live]
    └──requires──> [PKCE redirect wired to authenticate existing user before linking]
    └──requires──> [AccountLinkingAuthenticationProvider.extractLinkingContext implemented (currently stub)]
    
[Membership removal (actually deletes)]
    └──requires──> [MembershipManagementController.deleteMembership TODO completed in service]
    
[Membership role change (actually updates)]
    └──requires──> [MembershipManagementController.updateMembershipRole TODO completed in service]

[List org members]
    └──requires──> [New endpoint in OrganizationManagementController or MembershipManagementController]
    └──requires──> [Backend updated to call auth-service members API instead of Zitadel]

[RSA key persistence]
    └──required by──> [Production deployment]
    └──can be deferred for──> [local / dev testing]
    
[Registered client persistence]
    └──required by──> [Production deployment]
    └──can be deferred for──> [local / dev testing with InMemory]
```

### Dependency Notes

- **Backend JIT provisioning requires auth-service claims:** The backend's `JitProvisioningService` currently reads `urn:zitadel:iam:user:resourceowner:id` and `zitadelSub`. After migration it must read `user_id` (already in JWT from `TokenCustomizerConfig`) and drop the Zitadel org claim lookup. This is a blocking dependency for every authenticated backend request.
- **Claim extractor must be updated before any protected endpoint works:** The backend's `jwtAuthenticationConverter` reads a nested Zitadel map. Auth-service emits a flat `authorities` list. Without this fix all role checks return empty, breaking all authorization.
- **Account linking PKCE flow is conceptually designed but not fully wired:** `AccountLinkingAuthenticationProvider.extractLinkingContext` returns `null` (placeholder comment). The session-storage helpers are implemented but nothing calls `storeLinkingContextInSession` during the OAuth2 authorization code flow. This is the most complex missing piece.
- **Membership CRUD TODOs block member management:** Both `deleteMembership` and `updateMembershipRole` have `// TODO: Implement actual ...` comments. Guards are implemented but the mutations are not.

---

## MVP Definition

### Launch With (v1 — Migration Complete)

Minimum to cut over from Zitadel with all existing features working.

- [ ] **RSA key externalized** — tokens survive restarts; without this every server restart logs everyone out
- [ ] **Registered client for Angular frontend** — auth-service knows the frontend redirect URI; currently only a test client exists
- [ ] **Backend SecurityConfig: claim extractor updated** — reads `authorities` list from auth-service JWT instead of Zitadel nested map
- [ ] **Backend JIT provisioning rewritten** — reads `user_id` and `primary_email` from auth-service JWT, drops `zitadelSub` / `zitadelOrgId` references
- [ ] **Frontend `window.__env` updated** — points to auth-service OIDC discovery endpoint
- [ ] **Membership deletion wired** — `MembershipManagementController` TODO completed in `UserManagementService`
- [ ] **Membership role change wired** — same
- [ ] **List org members endpoint** — new endpoint needed for frontend member list page
- [ ] **Backend `MemberInviteService` rewritten** — stops calling `ZitadelManagementClient`, calls auth-service invitation API instead (or auth-service handles it entirely)
- [ ] **Backend `MemberManagementService` rewritten** — stops calling Zitadel for member list, role change, removal; reads from auth-service

### Add After Validation (v1.x)

Features to add once the migration is verified working.

- [ ] **Invitation with role assignment** — extend `InvitationRequest` with `role` field; update `activateUser` and `handleAccountLinking` to use it instead of hardcoded `Role.USER`
- [ ] **Account linking PKCE flow fully wired** — implement `extractLinkingContext` and connect `storeLinkingContextInSession` to the OAuth2 authorization flow; current implementation accepts invites but linking via redirect loop is incomplete
- [ ] **Registered client persistence** — move from `InMemoryRegisteredClientRepository` to `JdbcRegisteredClientRepository` to support config without restart
- [ ] **Audit service wiring** — call `AuditService` from critical controller methods (login, invite, membership change)

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] **OAuth2 social login** — as stated in PROJECT.md Out of Scope
- [ ] **Two-factor authentication** — as stated in PROJECT.md Out of Scope
- [ ] **Admin RBAC for management API** — scope-based access control on `/api/v1/**` for machine clients
- [ ] **Self-service email change with re-verification** — requires EMAIL_VERIFICATION token type to be added
- [ ] **Persistent sessions / remember me** — DB-backed Spring Session

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Backend claim extractor update | HIGH | LOW | P1 |
| Backend JIT provisioning rewrite | HIGH | LOW | P1 |
| Frontend OIDC config update | HIGH | LOW | P1 |
| RSA key persistence | HIGH | MEDIUM | P1 |
| Registered client for Angular frontend | HIGH | LOW | P1 |
| Membership deletion / role change wired | HIGH | LOW | P1 |
| List org members endpoint | HIGH | LOW | P1 |
| Backend MemberInviteService rewrite | HIGH | MEDIUM | P1 |
| Backend MemberManagementService rewrite | HIGH | MEDIUM | P1 |
| Invitation with role assignment | MEDIUM | LOW | P2 |
| Account linking PKCE flow fully wired | MEDIUM | HIGH | P2 |
| Registered client persistence | LOW | MEDIUM | P2 |
| Audit service wiring | LOW | LOW | P2 |
| OAuth2 social login | MEDIUM | HIGH | P3 |
| Two-factor authentication | MEDIUM | HIGH | P3 |

**Priority key:**
- P1: Must have for migration cutover
- P2: Should have, add when possible (post-cutover stabilization)
- P3: Nice to have, future consideration

---

## Sources

- Direct analysis of `auth-service/src/main/java/de/goaldone/authservice/` — domain, controller, service, security, config packages (2026-05-02)
- Direct analysis of `backend/src/main/java/de/goaldone/backend/` — filter, config, service, client packages (2026-05-02)
- `.planning/PROJECT.md` — project requirements and out-of-scope decisions (2026-05-02)

---
*Feature research for: Zitadel migration to custom auth-service (multi-tenant SaaS)*
*Researched: 2026-05-02*
