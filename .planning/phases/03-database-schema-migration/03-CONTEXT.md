# Phase 03: Database Schema Migration - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

The core goal of this phase is to finalize the database schema and entity model transition from the legacy Zitadel structure to the new custom `auth-service` model. This involves renaming and consolidating entities to follow a "single user identity, multiple memberships" architecture and ensuring all Zitadel-specific references are removed.

</domain>

<decisions>
## Implementation Decisions

### Entity Consolidation & Naming
- **D-01: Rename to User/Membership.** `UserIdentityEntity` will be renamed to `UserEntity`. `UserAccountEntity` will be renamed to `MembershipEntity`.
- **D-02: Normalize auth_user_id.** The global identifier `auth_user_id` will be moved from the `user_accounts` table to the `users` (formerly `user_identities`) table. This makes it the unique handle for the global user identity.

### Role Persistence
- **D-03: JWT-Only (Dynamic).** User roles will not be stored in the backend database. The backend will rely on the `authorities` (or `orgs`) claim in the JWT for all authorization decisions.
- **D-04: External Role Management.** For administrative views like "Member Lists" that require roles for users other than the current one, the backend will fetch roles on-demand from the `auth-service` Management API.

### Multi-Org Resolution
- **D-05: Header-Based Context (X-Org-ID).** The system will use the `X-Org-ID` header to determine which organization a request is targeting.
- **D-06: Per-Org Authorization.** The backend must validate that the user is a member of the org specified in `X-Org-ID` and that they possess the required roles *for that specific organization* (as defined in the JWT).
- **D-07: No Active Org in Token.** The JWT will contain all of a user's memberships and roles, but no "active" organization claim. The frontend provides the context via the header.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core Requirements & Roadmap
- `.planning/ROADMAP.md` — Defines Phase 3 goals and success criteria.
- `.planning/REQUIREMENTS.md` — Lists DB-XX requirements for schema migration.

### Existing Entity Implementation
- `backend/src/main/java/de/goaldone/backend/entity/UserIdentityEntity.java` — To be renamed to `UserEntity`.
- `backend/src/main/java/de/goaldone/backend/entity/UserAccountEntity.java` — To be renamed to `MembershipEntity`.
- `backend/src/main/java/de/goaldone/backend/entity/OrganizationEntity.java` — Defines the organization model.

### Database Migrations
- `backend/src/main/resources/db/changelog/changes/001-create-user-and-org-shadow-tables.xml` — Original schema.
- `backend/src/main/resources/db/changelog/changes/010-rename-zitadel-identifiers.xml` — Initial renaming work from Phase 2.
- `backend/src/main/resources/db/changelog/changes/011-allow-multiple-orgs-per-user.xml` — Composite unique constraint setup.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CurrentUserResolver`: Needs significant refactoring to use `X-Org-ID` and support multiple memberships.
- `UserAccountRepository`: Needs to be renamed to `MembershipRepository` and queries updated for the moved `auth_user_id`.

### Established Patterns
- **Liquibase:** All schema changes must be captured in a new changelog file (e.g., `012-finalize-user-membership-model.xml`).
- **JIT Provisioning:** The provisioning logic (from Phase 2) must be updated to create/update `UserEntity` and `MembershipEntity` separately.

### Integration Points
- **Controller Layer:** Where the `X-Org-ID` header will be received and potentially validated via a filter or interceptor.

</code_context>

<specifics>
## Specific Ideas
- Ensure that the move of `auth_user_id` to the `users` table includes a data migration script to preserve existing user mappings.
- The `MembershipEntity` should have a composite primary key or a unique constraint on `(user_id, organization_id)`.

</specifics>

<deferred>
## Deferred Ideas
- None — all discussed items were within the scope of Phase 3.

</deferred>

---

*Phase: 03-database-schema-migration*
*Context gathered: 2026-05-02*
