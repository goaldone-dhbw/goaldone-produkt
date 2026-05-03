# Phase 5: Member Management Rewrite & Cutover - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase replaces every Zitadel SDK call in the backend with calls to the auth-service management API, and removes the Zitadel SDK (`io.github.zitadel:client`) from `pom.xml`. The scope covers:

- Rewrite `MemberInviteService` and `MemberManagementService` to call auth-service via a new `AuthServiceManagementClient` (replaces `ZitadelManagementClient`)
- Delete `ZitadelManagementClient`, `ZitadelConfig`, `ZitadelApiException`, `ZitadelUserInfo`, `ZitadelUserInfoClient`
- Replace `StartupValidator` with an auth-service health check
- Migrate `OrganizationEntity.id` and `UserEntity.id` PKs to use auth-service UUIDs directly (remove separate `authCompanyId` / `authUserId` mapping columns)
- Add `GET /api/v1/organizations/{id}/members` endpoint to auth-service
- Add M2M client credentials support to the backend-to-auth-service connection (including seeding of backend client in auth-service `ClientSeedingRunner`)
- Update OpenAPI spec to reflect UUID-typed member operation parameters
- All existing member management features remain: invite, reinvite, cancel invite, list members (active + pending), change role, remove member

The result: backend compiles and all tests pass with Zitadel SDK fully removed.

</domain>

<decisions>
## Implementation Decisions

### Auth-Service Client Authentication (Backend → Auth-Service)

- **D-01: M2M Client Credentials.** The backend authenticates to the auth-service management API using the OAuth2 `client_credentials` grant type. A dedicated backend service client is registered in auth-service (not a user-facing client).
- **D-02: Cached Token.** The M2M access token is fetched on first use and cached in memory. It is refreshed proactively when less than 5 minutes of validity remain. The standard Spring `OAuth2AuthorizedClientManager` / `RestClient` integration is used.
- **D-03: Seeded by ClientSeedingRunner.** The backend M2M client is registered in auth-service at startup via the existing `ClientSeedingRunner` (same seeding mechanism as the frontend OIDC client). Configuration is injected via application properties.
- **D-04: Management API Scope Guard.** The auth-service management API (`/api/v1/**`) requires a specific scope (e.g., `scope=mgmt`) to distinguish M2M backend calls from end-user JWTs. End-user JWTs cannot call management endpoints.
- **D-05: Path Parameters for Internal Calls.** Backend → auth-service M2M calls use path parameters (`/api/v1/users/{userId}/memberships/{companyId}`). The X-Org-ID header convention applies only to the user-facing frontend → backend API, not internal service calls.

### Invitation Flow

- **D-06: Eager Pending Membership.** When `inviteMember` is called, the backend immediately creates a `MembershipEntity` with `status=INVITED` alongside calling `POST /api/v1/invitations` on auth-service. This enables showing pending members in the member list before they log in.
- **D-07: InvitationId Placeholder.** The pending `MembershipEntity` stores the auth-service `invitationId` (UUID returned from `POST /api/v1/invitations`) in a dedicated nullable column (`invitation_id`). The `auth_user_id` field remains null until the user accepts the invitation and logs in (JIT provisioning fills it).
- **D-08: Resend and Cancel Support.** The backend exposes reinvite (resend invitation email) and cancel invitation operations:
  - **Resend:** Backend calls auth-service `POST /api/v1/invitations` again (or a dedicated resend endpoint) and updates the stored `invitationId`.
  - **Cancel:** Backend marks the `MembershipEntity` as cancelled AND calls auth-service `DELETE /api/v1/invitations/{token}` to cancel immediately (both systems stay in sync).
- **D-09: Pending Members in listMembers.** `listMembers` returns both `ACTIVE` (confirmed, have `auth_user_id`) and `INVITED` (pending, have only `invitationId`) members. The `MemberStatus` field distinguishes them. Frontend can display a "pending" badge and show resend/cancel actions.

### Member List Data Source

- **D-10: New Auth-Service Bulk Endpoint.** Member list data comes from a new endpoint added to auth-service: `GET /api/v1/organizations/{authCompanyId}/members`. Returns `[{userId, email, firstName, lastName, role, status}]` for all active and pending members of the org.
- **D-11: Auth-Service CompanyId.** The backend calls the new endpoint using `OrganizationEntity.authCompanyId` (UUID from auth-service), not the backend's local org UUID. After the PK unification (D-14), this will be the same value.
- **D-12: Roles from Auth-Service Membership Record.** The new endpoint returns roles directly from the auth-service `Membership` entity's role field. No JWT claim lookup needed for listing other users' roles.

### ID Unification (Backend Entity PKs)

- **D-13: Auth-Service UUIDs as PKs.** `OrganizationEntity.id` and `UserEntity.id` (and `MembershipEntity.id`) are migrated to use auth-service UUIDs directly as their primary keys. The separate `authCompanyId` / `authUserId` mapping columns are dropped.
- **D-14: Liquibase Migration Required.** A new Liquibase changeset migrates existing rows: set `id = authCompanyId` (for organizations) and `id = authUserId` (for users). Drop the old mapping columns. Any FK references must be updated accordingly.
- **D-15: No Separate Local PKs.** After Phase 5, the backend does not generate its own UUIDs for users or organizations. Auth-service is the authoritative source of identity.

### Member Identity in Operations (API Surface)

- **D-16: UUID Member IDs.** All member operation method signatures and API endpoint parameters use `UUID` for user identification, replacing the old `String zitadelUserId`. The OpenAPI spec is the source of truth and must be updated first.
- **D-17: MemberResponse.userId is UUID.** The `MemberResponse` DTO field changes from `zitadelUserId` (String) to `userId` (UUID). The `zitadelUserId` field is removed.
- **D-18: Dual Sync for Role/Remove Operations.** When changing a member's role or removing them, the backend:
  1. Calls auth-service management API (`PATCH` or `DELETE /api/v1/users/{userId}/memberships/{companyId}`)
  2. Updates the local `MembershipEntity`
  Both must succeed; if auth-service returns an error, the local DB is not updated.
- **D-19: Auth-Service Enforces Last-Admin Guard.** The last-admin constraint (cannot remove or demote the last `COMPANY_ADMIN`) is enforced by auth-service, which returns 409. The backend maps auth-service 409 to its own `ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED/REMOVED")`.

### Zitadel Cleanup

- **D-20: StartupValidator → Auth-Service Health Check.** The `StartupValidator` is rewritten to replace Zitadel-specific checks (org exists, SUPER_ADMIN present) with an auth-service reachability check at startup. If the auth-service management API is unreachable, a startup warning is logged.
- **D-21: Full Zitadel SDK Removal.** The following are deleted entirely: `ZitadelManagementClient`, `ZitadelConfig`, `ZitadelApiException`, `ZitadelUserInfo`, `ZitadelUserInfoClient`, and all `zitadel.*` config properties. The `io.github.zitadel:client` dependency is removed from `pom.xml`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core Requirements & Roadmap
- `.planning/ROADMAP.md` — Phase 5 goal and success criteria (MEM-01 through MEM-08, AUTHZ-01 through AUTHZ-05, TEST-03, TEST-04, TEST-06, INFRA-01 through INFRA-03, INFRA-05)
- `.planning/REQUIREMENTS.md` — Full requirement definitions for Phase 5

### Prior Phase Decisions
- `.planning/phases/03.1-refine-organization-context-and-header-requirements/3.1-CONTEXT.md` — X-Org-ID header pattern, `/organization/members` path, `hasOrgRole` expression
- `.planning/phases/04-frontend-auth-switch/04-CONTEXT.md` — Role extraction from `orgs` claim, X-Org-ID conditional injection behavior

### Backend Services Being Replaced
- `backend/src/main/java/de/goaldone/backend/service/MemberManagementService.java` — Current Zitadel-based implementation; full rewrite target
- `backend/src/main/java/de/goaldone/backend/service/MemberInviteService.java` — Current Zitadel-based invite; full rewrite target
- `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java` — To be deleted and replaced by AuthServiceManagementClient
- `backend/src/main/java/de/goaldone/backend/config/ZitadelConfig.java` — To be deleted
- `backend/src/main/java/de/goaldone/backend/config/StartupValidator.java` — To be rewritten as auth-service health check

### Auth-Service Management API (Target)
- `auth-service/src/main/java/de/goaldone/authservice/controller/InvitationManagementController.java` — POST/GET/DELETE /api/v1/invitations
- `auth-service/src/main/java/de/goaldone/authservice/controller/MembershipManagementController.java` — DELETE/PATCH /api/v1/users/{userId}/memberships/{companyId}
- `auth-service/src/main/java/de/goaldone/authservice/controller/UserManagementController.java` — GET /api/v1/users/{id}, GET /api/v1/users/search
- `auth-service/src/main/java/de/goaldone/authservice/config/DefaultSecurityConfig.java` — Management API security (requires JWT + mgmt scope for /api/v1/**)
- `auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java` — Where backend M2M client must be seeded

### API Specification
- `api-spec/openapi.yaml` — Single source of truth; MUST be updated before implementation: userId parameters → UUID, member operations, new GET members endpoint

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MemberManagementController` — Controller structure is correct (header-based org context, `@PreAuthorize` guards). Only the service layer under it changes.
- `MembershipEntity` — Needs a new nullable `invitation_id` (UUID) column added via Liquibase. The `status` field (ACTIVE/INVITED) pattern is already established.
- `OrganizationEntity` + `UserEntity` — PKs will change to auth-service UUIDs via Liquibase migration. All FK references in other entities must be updated.
- `InvitationManagementService` (auth-service) — Already handles invitation creation, cancellation, and acceptance flow. The new `GET /api/v1/organizations/{id}/members` endpoint needs to be added to `OrganizationManagementController`.
- Spring `OAuth2AuthorizedClientManager` — Standard Spring pattern for M2M client credentials; already supported by Spring Security OAuth2 client on the backend.

### Established Patterns
- **Liquibase for schema changes:** All DB migrations go through Liquibase changesets. ID unification and the new `invitation_id` column both need changesets.
- **OpenAPI first:** `api-spec/openapi.yaml` is updated before writing service/controller code. The generated `MemberManagementApi` interface is the contract.
- **`@PreAuthorize` with custom `@authz` expression:** Member endpoints already use `@authz.isMember(#xOrgID)` — this pattern continues unchanged.
- **X-Org-ID from header (frontend → backend):** Member operations receive org context via header, not path. No change needed in `MemberManagementController`.
- **Internal M2M path params (backend → auth-service):** Internal calls use path parameters for org/user IDs, not headers.

### Integration Points
- **JIT Provisioning (Phase 2/3):** When an invited user logs in for the first time, JIT provisioning must match the new `UserEntity` (by email or JWT `sub`) to the pending `MembershipEntity` (by `invitationId` → `auth_user_id` fill-in). The existing JIT provisioner must be updated to handle this case.
- **`MembershipDeletionService`:** Currently used by `removeMember`. Must be updated to also call auth-service DELETE membership endpoint.
- **`ZitadelApiException`:** All catch blocks in services that catch `ZitadelApiException` must be updated to handle the new `AuthServiceManagementException`.

</code_context>

<specifics>
## Specific Ideas

- **OpenAPI first:** The OpenAPI spec update (userId → UUID, new endpoints) must happen before writing service code, per project convention.
- **Resend invitation:** The resend flow calls auth-service to send a new invitation email. The existing `invitationId` in the pending `MembershipEntity` is replaced with the new invitation token returned by auth-service.
- **Auth-service client scope name:** Use `mgmt` as the scope name for the backend M2M client credentials scope. This scope is required by `DefaultSecurityConfig.managementApiSecurityFilterChain`.
- **Token cache implementation:** Use `OAuth2AuthorizedClientManager` with `ClientCredentialsOAuth2AuthorizedClientProvider`. Cache is backed by `InMemoryOAuth2AuthorizedClientService`. Refresh threshold: 5 minutes before expiry.
- **ID migration strategy:** During Liquibase migration, `id = authUserId` / `id = authCompanyId` for existing rows. For invited-but-not-yet-logged-in users, there may be rows with null `authUserId` — these need to be handled (either set a placeholder or ensure they don't exist at migration time since Phase 5 introduces this feature fresh).

</specifics>

<deferred>
## Deferred Ideas

- **Phase 6 scope:** Restoring 100+ removed tests is explicitly deferred to Phase 6. Phase 5 should not break existing tests but does not need to restore Phase 6's test backlog.
- **Admin Console UI for auth-service:** Full admin UI for auth-service management is out of scope for this milestone (v2).
- **Social Login / 2FA / WebAuthn:** Deferred to v2 as established in prior phases.

</deferred>

---

*Phase: 05-member-management-rewrite-and-cutover*
*Context gathered: 2026-05-03*
