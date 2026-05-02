# Phase 03: Database Schema Migration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 03-database-schema-migration
**Areas discussed:** Entity Consolidation & Naming, Identity Identifier Location, Role Persistence Strategy, Multi-Org Resolution Strategy

---

## Entity Consolidation & Naming

| Option | Description | Selected |
|--------|-------------|----------|
| User/Membership | Rename to UserEntity and MembershipEntity. (Clearer domain) | ✓ |
| Identity/Account | Keep current names. (Less refactoring) | |

**User's choice:** Rename to `UserEntity` and `MembershipEntity`.
**Notes:** This aligns the naming with the "single identity, multiple memberships" conceptual model.

---

## Identity Identifier Location

| Option | Description | Selected |
|--------|-------------|----------|
| Global User Entity | Move auth_user_id to the global User entity. (Normalized) | ✓ |
| Per-Org Record | Keep auth_user_id on the per-org record. (Current state) | |

**User's choice:** Move `auth_user_id` to the global `UserEntity`.
**Notes:** Normalizes the database by having the external auth-service identifier at the identity level rather than repeated on every membership.

---

## Role Persistence Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Persist in Database | Store roles in the Membership table. (Enables DB queries) | |
| JWT-Only (Dynamic) | Extract roles from JWT only. (Strictly dynamic) | ✓ |

**User's choice:** JWT-Only (Dynamic).
**Notes:** The backend will not mirror roles in its database. This ensures the `auth-service` remains the single source of truth and avoids sync issues. Management views will fetch roles via API.

---

## Multi-Org Resolution Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Request Header + Validation | Use a request header, validated against the JWT 'orgs' claim. | ✓ |
| JWT 'active_org_id' Claim | Use a claim in the JWT to define the active context. | |
| Path Parameter | Include the organization ID in the API URL paths. | |

**User's choice:** Request Header + Validation (`X-Org-ID`).
**Notes:** The user explicitly rejected `active_org_id` in the token to avoid unnecessary "selection" flows in the frontend. Context is provided per-request via the header and validated against the token's memberships.

---

## Claude's Discretion
- Implementation of the `X-Org-ID` validation filter/interceptor.
- Exact structure of the data migration for `auth_user_id`.

## Deferred Ideas
- None.
