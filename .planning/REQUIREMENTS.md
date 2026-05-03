# Requirements: Zitadel Migration → Custom Auth Service

**Defined:** 2026-05-02
**Core Value:** Users authenticate through custom auth-service with simplified multi-org identity model

## v1 Requirements

All requirements must be complete and verified before production cutover.

### Auth-Service Foundation

- [ ] **AUTH-01**: RSA keypair persisted to file/environment (not regenerated on restart)
- [ ] **AUTH-02**: RegisteredClient moved to JdbcRegisteredClientRepository (not in-memory)
- [ ] **AUTH-03**: Angular frontend client registered in auth-service with correct redirect URIs
- [ ] **AUTH-04**: JWKS endpoint accessible and cacheable by backend
- [ ] **AUTH-05**: JWT tokens include `authorities` (flat string set), `user_id`, `primary_email`, `emails` claims
- [ ] **AUTH-06**: `offline_access` scope supported; `REFRESH_TOKEN` grant type enabled

### Backend JWT Validation

- [ ] **JWT-01**: Backend validates tokens via JWKS discovery (issuer-uri swap only, no new deps)
- [ ] **JWT-02**: JwtAuthenticationConverter reads `authorities` claim instead of Zitadel's nested `urn:zitadel:iam:org:project:roles`
- [ ] **JWT-03**: JwtGrantedAuthoritiesConverter correctly converts flat `authorities` to `GrantedAuthority` objects
- [ ] **JWT-04**: Role-based access control (RBAC) works end-to-end (authorization checks pass for valid users)
- [ ] **JWT-05**: Tests use explicit `.authorities(...)` in MockMvc JWT helpers (custom JwtAuthenticationConverter not auto-applied)

### JIT Provisioning & User Accounts

- [ ] **JIT-01**: JIT provisioner reads `sub` and `user_id` from auth-service JWT (not Zitadel claims)
- [ ] **JIT-02**: New users auto-provisioned on first login (database records created from JWT)
- [ ] **JIT-03**: JIT provisioner handles multi-org memberships (users can have roles in multiple organizations)
- [ ] **JIT-04**: No `zitadelSub`, `zitadelOrgId`, or other Zitadel-specific fields in JWT processing

### Database Schema Migration

- [ ] **DB-01**: Liquibase changelog created: remove `zitadel_sub`, `zitadel_org_id` columns
- [ ] **DB-02**: New columns added: `auth_user_id` (UUID from auth-service), indexed for login lookups
- [ ] **DB-03**: UserAccountEntity refactored: remove `organizationId` FK, use Membership table for org relationships
- [ ] **DB-04**: Existing Zitadel user data migrated to new schema (if applicable; greenfield if not)
- [ ] **DB-05**: All repository queries updated to use new column names
- [ ] **DB-06**: CurrentUserResolver finds users by `auth_user_id` instead of `zitadelSub`

### Frontend OAuth2 Integration

- [ ] **FE-01**: OIDC issuer URI updated to auth-service URL (from `env.js`)
- [ ] **FE-02**: OIDC client ID updated to registered frontend client ID (from `env.js`)
- [ ] **FE-03**: Redirect URIs point to auth-service login endpoint
- [ ] **FE-04**: Frontend OAuth2 PKCE flow works end-to-end (login succeeds, token received)
- [ ] **FE-05**: AuthService.getUserRoles() reads `authorities` claim instead of Zitadel URN keys
- [ ] **FE-06**: AuthService.getOrganizations() correctly extracts org context from JWT (via multi-org provisioning)

### Member Management Features

- [ ] **MEM-01**: Invite members to organization (send invitation link via auth-service)
- [ ] **MEM-02**: Invitations stored in auth-service; backend receives acceptance via webhook or polling
- [ ] **MEM-03**: Accept invitation → user added to organization with specified role
- [ ] **MEM-04**: Change member role (admin ↔ member) via backend → auth-service Membership API
- [ ] **MEM-05**: Remove member from organization via backend → auth-service Membership API
- [ ] **MEM-06**: All member operations restricted to COMPANY_ADMIN role (authorization checks)
- [ ] **MEM-07**: Last admin guard: cannot remove/demote last admin (error returned)
- [ ] **MEM-08**: Member list endpoint returns users with roles and membership status

### Authorization & Multi-Org

- [ ] **AUTHZ-01**: Role-based access control (RBAC) works per organization
- [ ] **AUTHZ-02**: Users can see/access only organizations they're members of
- [ ] **AUTHZ-03**: Users have different roles (and permissions) in different organizations
- [ ] **AUTHZ-04**: COMPANY_ADMIN role has permission to invite/manage/remove members
- [ ] **AUTHZ-05**: Non-admin users cannot invite, change roles, or remove members

### Testing & Validation

- [ ] **TEST-01**: Spring Security integration tests updated to use auth-service token format
- [ ] **TEST-02**: JIT provisioning tests pass with auth-service claim structure
- [ ] **TEST-03**: Member management tests pass (invitation, role change, removal workflows)
- [ ] **TEST-04**: Organization management tests pass with new auth model
- [ ] **TEST-05**: End-to-end login flow works (frontend → auth-service → backend → frontend)
- [ ] **TEST-06**: Role-based access control tests verify authorization decisions

### Infrastructure & Cleanup

- [ ] **INFRA-01**: Zitadel SDK dependency removed from backend pom.xml
- [ ] **INFRA-02**: ZitadelConfig, ZitadelManagementClient, ZitadelProperties deleted
- [ ] **INFRA-03**: StartupValidator updated or removed (no Zitadel endpoints to validate)
- [ ] **INFRA-04**: Auth-service deployed with persistent RSA key + JDBC client repo (production-ready)
- [ ] **INFRA-05**: Zitadel credentials removed from all configuration files

## v2 Requirements

Deferred to future release. Not in current roadmap.

### Advanced Features

- **2FA/WebAuthn** — Advanced account security features (explicitly deferred; not planned)
- **Social Login** — Google, GitHub, other OAuth2 providers (explicitly deferred; not planned)
- **Account Linking** — Email aliases / secondary emails (explicitly deferred; not planned)
- **Audit Logging** — Detailed auth event tracking beyond auth-service baseline (explicitly deferred; not planned)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Multi-factor authentication | Not planned for this product; deferred indefinitely |
| Social provider login | Single email/password auth sufficient for v1; social login deferred |
| Account linking / email aliases | New single-account model eliminates this need; not planned |
| Granular audit logging | Auth-service provides baseline; detailed audit tracking deferred |
| Admin console for auth-service | Management API only; no admin UI for v1 |

## Traceability

Updated during roadmap creation. Maps each requirement to phase + success criteria.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Pending |
| AUTH-02 | Phase 1 | Pending |
| AUTH-03 | Phase 1 | Pending |
| AUTH-04 | Phase 1 | Pending |
| AUTH-05 | Phase 1 | Pending |
| AUTH-06 | Phase 1 | Pending |
| JWT-01 | Phase 2 | Pending |
| JWT-02 | Phase 2 | Pending |
| JWT-03 | Phase 2 | Pending |
| JWT-04 | Phase 2 | Pending |
| JWT-05 | Phase 2 | Pending |
| JIT-01 | Phase 2 | Pending |
| JIT-02 | Phase 2 | Pending |
| JIT-03 | Phase 2 | Pending |
| JIT-04 | Phase 2 | Pending |
| DB-01 | Phase 3 | Pending |
| DB-02 | Phase 3 | Pending |
| DB-03 | Phase 3 | Pending |
| DB-04 | Phase 3 | Pending |
| DB-05 | Phase 3 | Pending |
| DB-06 | Phase 3 | Pending |
| FE-01 | Phase 4 | Pending |
| FE-02 | Phase 4 | Pending |
| FE-03 | Phase 4 | Pending |
| FE-04 | Phase 4 | Pending |
| FE-05 | Phase 4 | Pending |
| FE-06 | Phase 4 | Pending |
| MEM-01 | Phase 5 | Pending |
| MEM-02 | Phase 5 | Pending |
| MEM-03 | Phase 5 | Pending |
| MEM-04 | Phase 5 | Pending |
| MEM-05 | Phase 5 | Pending |
| MEM-06 | Phase 5 | Pending |
| MEM-07 | Phase 5 | Pending |
| MEM-08 | Phase 5 | Pending |
| AUTHZ-01 | Phase 5 | Pending |
| AUTHZ-02 | Phase 5 | Pending |
| AUTHZ-03 | Phase 5 | Pending |
| AUTHZ-04 | Phase 5 | Pending |
| AUTHZ-05 | Phase 5 | Pending |
| TEST-01 | Phase 2 | Pending |
| TEST-02 | Phase 2 | Pending |
| TEST-03 | Phase 5 | Pending |
| TEST-04 | Phase 5 | Pending |
| TEST-05 | Phase 4 | Pending |
| TEST-06 | Phase 5 | Pending |
| INFRA-01 | Phase 5 | Pending |
| INFRA-02 | Phase 5 | Pending |
| INFRA-03 | Phase 5 | Pending |
| INFRA-04 | Phase 1 | Pending |
| INFRA-05 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 46 total
- Mapped to phases: 46
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-02*
*Last updated: 2026-05-02 after research synthesis*
