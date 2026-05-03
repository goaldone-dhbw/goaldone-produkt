# Phase 2: Backend JWT Validation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 2-Backend JWT Validation
**Areas discussed:** JWT Claim Mapping, JIT Provisioning Transition, Multi-Org Authorization, Integration Testing Strategy

---

## JWT Claim Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Flat Authorities Mapping | Directly map 'authorities' claim to Spring GrantedAuthorities. | ✓ |
| Hybrid Mapping Strategy | Maintain compatibility with both Zitadel and auth-service. | |

**User's choice:** Flat Authorities Mapping
**Notes:** Simple and aligned with Phase 1 decisions.

| Option | Description | Selected |
|--------|-------------|----------|
| Force ROLE_ Prefix | Ensure all extracted authorities have 'ROLE_' prefix. | ✓ |
| As-Is Mapping | Use values exactly as they appear in the claim. | |

**User's choice:** Force ROLE_ Prefix
**Notes:** Best for standard Spring Security @PreAuthorize usage.

---

## JIT Provisioning Transition

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-aligned mapping | Map 'user_id' claim to existing 'zitadelSub' field. | |
| Rename now | Perform the rename to 'authUserId' now in Phase 2. | ✓ |

**User's choice:** Rename now
**Notes:** Pulls column renaming from Phase 3 into Phase 2 for cleaner code immediately.

| Option | Description | Selected |
|--------|-------------|----------|
| Lazy Single-Org JIT | Only provision the organization specified in the request. | |
| Bulk Membership JIT | Iterate through 'orgs' array and provision all memberships. | ✓ |

**User's choice:** Bulk Membership JIT
**Notes:** Ensures all user access is ready on first login.

---

## Multi-Org Authorization

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit Service Checks | Continue checking membership explicitly in service methods. | |
| Centralized Authz Logic | Create a custom Security Expression or Aspect to check automatically. | ✓ |

**User's choice:** Centralized Authz Logic
**Notes:** Prefers a DRYer approach over explicit manual checks.

---

## Integration Testing Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Update Test Helpers | Update security helpers to generate JWTs with new claim shapes. | ✓ |
| WireMock Auth Mocking | Maintain WireMock stubs for auth-service endpoints. | |

**User's choice:** Update Test Helpers
**Notes:** Aligns test infrastructure with the new token contract.

---

## Claude's Discretion
- Idempotency in bulk JIT provisioning.
- Handling `super_admin` in centralized authz.

## Deferred Ideas
- None.
