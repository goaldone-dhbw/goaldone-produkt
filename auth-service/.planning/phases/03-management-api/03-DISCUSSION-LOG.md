# Phase 03: Management API - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 03-Management API
**Areas discussed:** API Design Style, Security & Auth, Management Scopes, Invitation Mechanics

---

## API Design Style

| Option | Description | Selected |
|--------|-------------|----------|
| /api/management/... | e.g., /api/management/users | |
| /api/v1/... (Versioned) | e.g., /api/v1/users | ✓ |
| /management/... (Unprefixed) | e.g., /management/users | |

| Option | Description | Selected |
|--------|-------------|----------|
| Direct (Object) | Returns the object directly. Simple and standard. | ✓ |
| Wrapped (Envelope) | e.g., { "data": ..., "meta": ... }. Better for pagination/extra info. | |

| Option | Description | Selected |
|--------|-------------|----------|
| Problem Details (RFC 7807) | Industry standard for Spring Boot (application/problem+json). | ✓ |
| Custom JSON Error | e.g., { "error": "code", "message": "..." } | |

| Option | Description | Selected |
|--------|-------------|----------|
| Java Records | Modern, concise, and immutable. Recommended for Java 21. | |
| Standard Classes (Lombok) | Standard Java classes (POJOs). More flexible for mapping libraries. | ✓ |

**User's choice:** Versioned API, Direct objects, RFC 7807, Lombok classes.
**Notes:** Decided to stick with traditional Lombok classes for DTOs to ensure maximum compatibility with existing mapping patterns and libraries if needed.

---

## Security & Auth

| Option | Description | Selected |
|--------|-------------|----------|
| Single Global Client | One client with full access to all management endpoints. | ✓ |
| Scoped Clients | Separate clients/scopes (e.g., users:read, orgs:write). | |

| Option | Description | Selected |
|--------|-------------|----------|
| Specific Mgmt Audience | Include a specific "aud" claim (e.g., "auth-service-mgmt") in management tokens. | ✓ |
| Global Audience | Use the same audience as the standard auth server. | |

| Option | Description | Selected |
|--------|-------------|----------|
| Admin-Only Service | Require both Client Credentials AND a valid Super-Admin user context. | |
| Pure Machine-to-Machine | Pure machine-to-machine; the client ID identifies the caller. | ✓ |

**User's choice:** Single global client, Specific audience, Machine-to-machine.
**Notes:** User noted that while it's mainly machine-to-machine, both are possible. Decision is to start with pure M2M for simplicity.

---

## Management Scopes

| Option | Description | Selected |
|--------|-------------|----------|
| Full CRUD | Full CRUD (Create, Read, Update, Delete). | ✓ |
| Read & Status Only | Read and Status updates only (INVITED -> ACTIVE -> DISABLED). | |

| Option | Description | Selected |
|--------|-------------|----------|
| Basic Management | Create and basic info updates (name, slug). | ✓ |
| Advanced Management | Full control including archiving and domain settings. | |

| Option | Description | Selected |
|--------|-------------|----------|
| Strict Lookup | Exact matches for ID or primary email only. | ✓ |
| Rich Search/Filtering | Partial name/email search and list filtering. | |

**User's choice:** Full CRUD for users, Basic management for orgs, Strict lookup.
**Notes:** Strict lookup is sufficient for the initial internal management needs.

---

## Invitation Mechanics

| Option | Description | Selected |
|--------|-------------|----------|
| Database Tokens (UUID) | Opaque UUIDs stored in the database. Simple and revocable. | ✓ |
| Signed JWTs | Self-contained signed tokens. Stateless but harder to revoke. | |

| Option | Description | Selected |
|--------|-------------|----------|
| 3 Days | 3 days | |
| 7 Days | 7 days (Recommended) | ✓ |
| 30 Days | 30 days | |

| Option | Description | Selected |
|--------|-------------|----------|
| Block Duplicate | Prevent invitation (return error). | ✓ |
| Allow Multiple Orgs | Allow it; the user just gains a new membership when they accept. | |

**User's choice:** UUID tokens, 7 days, Block duplicates.
**Notes:** Blocking duplicates ensures data integrity and prevents confusing multiple pending invitations for the same user in the same org.

---

## Claude's Discretion
- **Security Scopes:** Since a single global client is used, fine-grained scopes (e.g., `users:write`) are at Claude's discretion for implementation if helpful for future-proofing.
- **Problem Details Details:** Specific implementation of Problem Details (e.g., custom attributes) is left to the developer.

## Deferred Ideas
- **Rich Search:** Deferred to a later phase once a larger volume of users/orgs is present.
- **Admin UI Integration:** While the API supports an Admin UI, the actual UI and user-context security checks are deferred.
