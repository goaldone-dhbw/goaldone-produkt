# Phase 2: Backend JWT Validation - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

The core goal of this phase is to rewire the backend Spring Security configuration to validate JWTs issued by the new `auth-service` instead of Zitadel. This includes updating the JWT claim extraction (roles and identifiers) and the Just-In-Time (JIT) provisioning logic to handle the new claim shapes and multi-org structure.

</domain>

<decisions>
## Implementation Decisions

### JWT Claim Mapping
- **D-01: Flat Authorities Mapping.** The backend will read the `authorities` claim as a flat list of strings (as decided in Phase 1). The complex nested Zitadel mapping in `SecurityConfig.java` will be replaced.
- **D-02: Force ROLE_ Prefix.** Every extracted authority from the `authorities` claim will be prefixed with `ROLE_` (e.g., `ADMIN` becomes `ROLE_ADMIN`) to maintain compatibility with existing `@PreAuthorize` annotations.

### JIT Provisioning Transition
- **D-03: Rename Identifiers Now.** Instead of waiting for Phase 3, we will rename the identity fields in the database and entity classes now. `zitadelSub` becomes `authUserId` (UUID) and `zitadelOrgId` becomes `authCompanyId`. This aligns the code with the new auth model immediately.
- **D-04: Bulk Membership JIT.** On first login (or when the token is processed), the JIT provisioner will iterate through the `orgs` array in the JWT and ensure all memberships listed in the token are provisioned in the local database.

### Multi-Org Authorization
- **D-05: Centralized Authz Logic.** We will introduce a centralized mechanism (e.g., a custom Security Expression or Aspect) to verify that the `orgId` provided in a request matches one of the organizations listed in the user's `orgs` claim. This replaces explicit service-level membership checks.

### Integration Testing Strategy
- **D-06: Update Test Helpers.** Existing integration tests and MockMvc security helpers will be updated to generate and use the new auth-service claim shapes (`user_id`, `authorities`, `orgs`) instead of mimicking Zitadel tokens.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core Requirements & Roadmap
- `.planning/ROADMAP.md` — Defines phase goals and dependencies.
- `.planning/REQUIREMENTS.md` — Lists specific JWT-XX and JIT-XX requirements.
- `1-CONTEXT.md` — Defines the token contract (`authorities`, `user_id`, `orgs` claims).

### Existing Security Implementation
- `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` — Current JWT validation and role mapping logic.
- `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java` — Existing JIT entry point.
- `backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java` — Existing provisioning business logic.
- `backend/src/main/java/de/goaldone/backend/entity/UserAccountEntity.java` — Current user/org entity model.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JwtAuthenticationConverter`: Existing class that can be refactored to use the new flat `authorities` claim.
- `JitProvisioningService`: Core logic for user/org creation that needs to be updated for bulk provisioning.

### Established Patterns
- **Stateless Resource Server:** The backend is already a stateless resource server; we are only changing the issuer and claim extraction logic.
- **Liquibase Migrations:** Used for renaming columns (`zitadel_sub` -> `auth_user_id`).

### Integration Points
- **SecurityFilterChain:** Where the new `JwtAuthenticationConverter` and potentially a new multi-org check filter will be registered.
- **CurrentUserResolver:** Needs to be updated to resolve users via the new `authUserId`.

</code_context>

<specifics>
## Specific Ideas
- The bulk JIT provisioning should be idempotent; if a membership already exists, it should not be recreated.
- The centralized authz check should handle cases where a user might be a global `super_admin`, bypassing org-specific checks.

</specifics>

<deferred>
## Deferred Ideas
- None — all discussed items were within the scope of Phase 2.

</deferred>

---

*Phase: 02-backend-jwt-validation*
*Context gathered: 2026-05-02*
