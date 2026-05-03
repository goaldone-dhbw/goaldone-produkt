# Phase 03: Management API - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Administrative REST APIs for managing Users, Organizations, and Invitations. This phase establishes the internal management layer used by other services or administrative tools to perform CRUD operations on core entities.

</domain>

<decisions>
## Implementation Decisions

### API Design Style
- **D-01:** **Base Path:** Use versioned base path `/api/v1/...` (e.g., `/api/v1/users`).
- **D-02:** **Response Format:** Direct object return for successful responses (no envelope).
- **D-03:** **Error Handling:** Implement **RFC 7807 (Problem Details)** for consistent error reporting.
- **D-04:** **DTO Pattern:** Use standard Java classes with **Lombok** for DTOs (Requests/Responses).

### Security & Auth
- **D-05:** **Authentication:** Secured via **OAuth 2.1 Client Credentials** grant.
- **D-06:** **Client Structure:** Use a **Single Global Client** for all management operations initially.
- **D-07:** **Audience:** Management tokens must include a specific audience claim (e.g., `auth-service-mgmt`).
- **D-08:** **M2M Flow:** Purely Machine-to-Machine interaction; authorization is tied to the Client ID.

### Management Scopes
- **D-09:** **User Management:** Full CRUD capabilities (Create, Read, Update, Delete).
- **D-10:** **Org Management:** Basic management (Create, Update name/slug).
- **D-11:** **Lookup Strategy:** **Strict Lookup** only. Search/Get by ID or Primary Email. No partial search required in this phase.

### Invitation Mechanics
- **D-12:** **Token Format:** Use **Opaque UUIDs** stored in the database.
- **D-13:** **Expiration:** Default invitation lifetime is **7 days**.
- **D-14:** **Conflict Policy:** **Block Duplicates**. If a user is already a member of an organization, prevent creating a new invitation for that same user/org pair.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — Section 1.6 (Client Credentials), 3 (Org Mgmt), 4 (Invitations).

### Domain Entities
- `src/main/java/de/goaldone/authservice/domain/User.java`
- `src/main/java/de/goaldone/authservice/domain/Company.java`
- `src/main/java/de/goaldone/authservice/domain/Membership.java`
- `src/main/java/de/goaldone/authservice/domain/UserEmail.java`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Repositories:** `UserRepository`, `CompanyRepository`, `MembershipRepository` already exist and should be used by the new management services.
- **Domain Model:** The Multi-Org schema (User -> Membership -> Company) is already implemented and must be respected.

### Established Patterns
- **Lombok:** Extensively used in domain entities; continue using it for DTOs.
- **Validation:** Use `jakarta.validation` (Bean Validation) for DTO constraints.

### Integration Points
- **Authorization Server:** New Client Credentials configuration needed in `AuthorizationServerConfig`.
- **Security Filter Chain:** Update `DefaultSecurityConfig` to protect `/api/v1/**` with the Resource Server filter chain.

</code_context>

<specifics>
## Specific Ideas
- No specific requirements — open to standard Spring Boot / Spring Security approaches.
</specifics>

<deferred>
## Deferred Ideas
- **Rich Search:** Algorithmic or partial search for users/orgs is deferred to a future performance/UX phase.
- **Invitation UI:** The landing page and reset flows are handled in Phase 4.
</deferred>

---

*Phase: 03-management-api*
*Context gathered: 2026-05-01*
