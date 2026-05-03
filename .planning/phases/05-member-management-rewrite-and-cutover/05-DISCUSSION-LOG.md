# Phase 5: Member Management Rewrite & Cutover - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 05-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 05-member-management-rewrite-and-cutover
**Areas discussed:** Backend-to-auth-service authentication, Invitation acceptance feedback, Member list data source, Member identity in operations

---

## Backend-to-Auth-Service Authentication

### How should the backend authenticate when calling auth-service management APIs?

| Option | Description | Selected |
|--------|-------------|----------|
| M2M client credentials | Register a backend service client in auth-service JDBC registry; use client_credentials grant | ✓ |
| Pass-through user JWT | Forward the end-user's JWT from the incoming request to auth-service | |
| Static service token | Hard-code or inject a long-lived bearer token | |

**User's choice:** M2M client credentials

---

### How should the M2M token be managed?

| Option | Description | Selected |
|--------|-------------|----------|
| Cached per-instance | Fetch on first use, cache, refresh when expired (<5 min remaining) | ✓ |
| Per-request | Get a fresh token before every management API call | |
| Shared Spring OAuth2AuthorizedClientManager | Reuse existing client manager infrastructure | |

**User's choice:** Cached per-instance

---

### How should the backend M2M client be registered in auth-service?

| Option | Description | Selected |
|--------|-------------|----------|
| Seeded via BootstrapRunner | Add backend client to auth-service startup seeding from config | ✓ |
| Manual DB insert | Insert directly into JDBC client registry | |
| Separate ENV/config client | Inject client-id/secret via application properties, no auto-seeding | |

**User's choice:** Seeded via BootstrapRunner (ClientSeedingRunner)

---

### Should the auth-service management API require a specific scope for M2M callers?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, require mgmt scope | Auth-service management API only callable by M2M clients with mgmt scope | ✓ |
| No, open to any authenticated JWT | Simpler; management ops already protected by @PreAuthorize on backend | |
| You decide | — | |

**User's choice:** Yes — management API restricted to M2M clients with `mgmt` scope

---

### For backend → auth-service internal calls: path params or X-Org-ID header?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep path params for auth-service management API | Internal M2M API; path params are REST-standard for internal calls | ✓ |
| Add X-Org-ID support to auth-service management API | Uniform convention everywhere | |
| You decide | — | |

**User's choice:** Keep path params — X-Org-ID is for user-facing frontend→backend API only

---

## Invitation Acceptance Feedback

### When should the backend create a local MembershipEntity for an invited user?

| Option | Description | Selected |
|--------|-------------|----------|
| Lazily via JIT on first login | Backend only calls auth-service to invite; JIT provisioning creates MembershipEntity on login | |
| Eagerly on invite | Create pending MembershipEntity (status=INVITED) when invite is sent; confirm on first login | ✓ |
| Webhook from auth-service | Auth-service calls backend endpoint after acceptance | |

**User's choice:** Eagerly on invite — enables showing pending members before they log in

---

### Should a local UserEntity also be created for the invited user immediately?

| Option | Description | Selected |
|--------|-------------|----------|
| UserEntity with auth_user_id=null + status=INVITED | Create placeholder UserEntity; fill auth_user_id on first login | |
| No UserEntity yet | Only create MembershipEntity with email reference | |
| Use invitation ID as placeholder | Store invitationId (UUID) in nullable field; resolve to auth_user_id after first login | ✓ |

**User's choice:** Store invitationId as placeholder — no auth-service API extension needed

---

### Should auth-service POST /api/v1/invitations be extended to pre-create a pending user?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, extend auth-service | Create pending user and return userId in invitation response | |
| Use invitation ID as placeholder | Store invitationId; resolve to auth_user_id after first login | ✓ |
| You decide | — | |

**User's choice:** Use invitation ID as placeholder — no auth-service API extension needed

---

### Should listMembers show pending/invited members?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, show pending | Return both ACTIVE and INVITED members; frontend shows pending badge | ✓ |
| No, active only | Return only confirmed members | |
| You decide | — | |

**User's choice:** Yes — show pending members, allow resending invitation email, and allow cancelling invitations (deletes invitation from auth-service)

---

### When cancelling a pending invitation, how should auth-service be notified?

| Option | Description | Selected |
|--------|-------------|----------|
| Via invitation token | Call DELETE /api/v1/invitations/{token} on auth-service | |
| Backend manages locally | Mark MembershipEntity cancelled; auth-service invitation expires naturally | |
| Both | Cancel locally AND call auth-service DELETE immediately | ✓ |

**User's choice:** Both — local + auth-service in sync

---

## Member List Data Source

### Where does member list data come from in the new system?

| Option | Description | Selected |
|--------|-------------|----------|
| Local DB only | Read from local MembershipRepository + UserEntity. Fast, self-contained. | |
| Auth-service per-user calls | Get member IDs from local DB, call GET /api/v1/users/{id} per member | |
| New bulk endpoint on auth-service | Add GET /api/v1/organizations/{id}/members to auth-service | ✓ |

**User's choice:** New bulk endpoint on auth-service — clean API design

---

### What should the new auth-service bulk endpoint path look like?

| Option | Description | Selected |
|--------|-------------|----------|
| GET /api/v1/organizations/{id}/members | Returns [{userId, email, firstName, lastName, role, status}] | ✓ |
| GET /api/v1/memberships?companyId={id} | Query-param style | |
| GET /api/v1/users?companyId={id} | Filter existing users endpoint | |

**User's choice:** GET /api/v1/organizations/{id}/members

---

### Where do member roles come from in the listMembers response?

| Option | Description | Selected |
|--------|-------------|----------|
| Roles from auth-service membership record | New endpoint returns role from auth-service Membership entity directly | ✓ |
| Roles from JWT orgs claim | Backend reads role from caller's JWT | |
| Backend resolves roles from local MembershipEntity | Roles in local DB | |

**User's choice:** Roles from auth-service membership record

---

### Which ID should be used to call GET /api/v1/organizations/{id}/members?

| Option | Description | Selected |
|--------|-------------|----------|
| Auth-service companyId (UUID) | Use OrganizationEntity.authCompanyId to call auth-service | ✓ |
| Local org UUID | Pass X-Org-ID (local org UUID) directly to auth-service | |

**User's choice:** Auth-service companyId

---

### Should Phase 5 collapse backend PKs to use auth-service UUIDs directly?

| Option | Description | Selected |
|--------|-------------|----------|
| Include in Phase 5 | Migrate backend PKs to use auth-service UUIDs; remove dual-ID mapping layer | ✓ |
| Defer to follow-on phase | Keep dual IDs for now; Phase 5 focuses on Zitadel client replacement only | |
| You decide | — | |

**User's choice:** Include in Phase 5 — eliminates unnecessary local IDs

---

### Should auth-service UUID become the actual PK (@Id)?

| Option | Description | Selected |
|--------|-------------|----------|
| Drop local UUID PK, use auth-service UUID as PK | OrganizationEntity.id = authCompanyId, UserEntity.id = authUserId | ✓ |
| Keep local UUID PK with UNIQUE constraint | Less disruptive but two IDs per entity still exist | |

**User's choice:** Drop local UUID PK — single identity everywhere

---

## Member Identity in Operations

### Should member operation API parameters change from String to UUID?

| Option | Description | Selected |
|--------|-------------|----------|
| UUID (change signatures and API) | Consistent with unified ID model | ✓ |
| Keep String, parse to UUID internally | Less disruptive but inconsistent | |

**User's choice:** UUID — consistent everywhere

---

### Should MemberResponse.zitadelUserId become userId (UUID)?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — userId is UUID, remove zitadelUserId field | ✓ |
| Keep as String for backward compatibility | |

**User's choice:** Yes — remove zitadelUserId field, use UUID userId

---

### Should OpenAPI spec be updated for the UUID change?

**Notes:** User stated: "Always update the OPENAPI — this is the single source of truth in our backend." OpenAPI spec must be updated before implementation.

---

### When changing role or removing a member, should backend call auth-service?

| Option | Description | Selected |
|--------|-------------|----------|
| Auth-service memberships endpoint | Use DELETE/PATCH /api/v1/users/{userId}/memberships/{companyId} | |
| Backend-local only | Update local DB only | |
| Both | Update local DB AND call auth-service membership API | ✓ |

**User's choice:** Both — keep both in sync

---

### Who enforces the last-admin guard?

| Option | Description | Selected |
|--------|-------------|----------|
| Auth-service is authoritative | Auth-service returns 409; backend maps to ResponseStatusException | ✓ |
| Backend enforces first | Backend checks locally before calling auth-service | |

**User's choice:** Auth-service is authoritative

---

### What should happen to StartupValidator?

| Option | Description | Selected |
|--------|-------------|----------|
| Delete entirely | Zitadel-specific logic with no auth-service equivalent | |
| Replace with auth-service health check | Check if auth-service management API is reachable at startup | ✓ |

**User's choice:** Replace with auth-service health check

---

## Agent's Discretion

No "you decide" responses — all gray areas were explicitly decided by the user.

## Deferred Ideas

- **Phase 6:** Restoring 100+ removed tests — explicitly deferred to Phase 6
- **Admin Console UI:** Full admin UI for auth-service management — deferred to v2
- **Social Login / 2FA / WebAuthn:** Established as v2 deferred items in prior phases
