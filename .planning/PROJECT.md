# Zitadel Migration → Custom Auth Service

## What This Is

Replace Zitadel as the identity provider with a custom auth-service across backend, frontend, and infrastructure. This is a **complete replacement** that removes all Zitadel dependencies while simplifying multi-tenant organization management by moving from linked multi-account to single-account + org memberships model.

## Core Value

Users can authenticate and manage multi-org access through a fully custom, in-house identity service that aligns with our simplified multi-tenancy model.

## Requirements

### Validated

(None yet — migration to validate)

### Active

#### Authentication & Authorization
- [ ] Backend validates JWT tokens from auth-service using JWKS endpoint
- [ ] Spring Security extracts roles from auth-service JWT claims
- [ ] Frontend OAuth2 client uses auth-service as provider (OIDC flow)
- [ ] Frontend redirect URIs updated to point to auth-service

#### User & Account Management
- [ ] Users have single auth-service account (not linked multi-account)
- [ ] User creation flow integrated with auth-service (not Zitadel Management API)
- [ ] Email verification handled by auth-service
- [ ] Password reset handled by auth-service

#### Multi-Organization Features
- [ ] Users can be members of multiple organizations
- [ ] Role assignment per org (not per user globally)
- [ ] Member invitation flow uses auth-service endpoints
- [ ] Member role changes integrated with auth-service
- [ ] Member removal integrated with auth-service

#### Database & Models
- [ ] Remove all Zitadel ID references (zitadelUserId, zitadelOrgId, zitadelSub)
- [ ] Migrate existing user/org data to new model (if applicable)
- [ ] UserAccountEntity updated to use auth-service user references
- [ ] OrganizationEntity updated to remove Zitadel org ID

#### Testing
- [ ] Spring Security integration tests use auth-service token format
- [ ] Member management tests pass with new auth flow
- [ ] Organization management tests pass with new auth flow

### Out of Scope

- OAuth2 login methods beyond email/password (e.g., Google, GitHub) — defer to v2
- Advanced account security (2FA, WebAuthn) — defer to v2
- Audit logging beyond what auth-service provides — defer to v2
- Admin RBAC for auth-service management — defer to v2

## Context

**Current State:**
- Zitadel acts as OIDC provider and user/org management backend
- Backend integrates with Zitadel Management API for member operations
- Frontend uses `angular-oauth2-oidc` with Zitadel OIDC config
- Multi-account linking is internal to app (Zitadel concept)
- Roles stored in JWT claim `urn:zitadel:iam:org:project:roles`

**Auth-Service State:**
- Spring Authorization Server already configured (OAuth2 Authorization Server)
- Token customizer already includes authorities, emails, user_id in JWT
- User, Company, Membership, Role domains defined
- Invitation flow partially implemented
- User management service exists
- JWKS endpoint ready for backend validation

**Why This Migration:**
- Simplify multi-tenancy (single user account + org memberships vs linked accounts)
- Remove Zitadel dependency/cost
- Full control over auth flows and data
- Align auth model with business needs

## Constraints

- **Timeline**: ASAP (weeks) — tight schedule for complete replacement
- **No breaking changes**: All legacy features must work (invitations, roles, member management)
- **OAuth2 provider model**: Auth-service stays as OAuth2 provider (frontend flow unchanged)
- **Token validation**: Backend must validate tokens via JWKS (not trust Zitadel)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Complete replacement (not gradual) | Faster, cleaner than phased migration; allows model simplification | — Pending |
| Auth-service as OAuth2 provider | Minimal frontend changes; proven pattern | — Pending |
| JWT token with custom claims | Simplest integration with Spring Security; includes org context | — Pending |
| Remove Zitadel IDs completely | Single source of truth (auth-service IDs); no dual-identity confusion | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

---
*Last updated: 2026-05-02 after initialization*
