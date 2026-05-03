# Implementation Plan: Phase 2 - Token Customization & Resource Server Integration

This phase focuses on tailoring the JWT for external Resource Server consumption and establishing a robust Super-Admin bootstrapping mechanism.

## Tasks

### [ ] Task 2.1: Schema, Domain & Repository Updates
- **Description:** Update the database schema, JPA entities, and establish necessary repositories.
- **Actions:**
    - Add `verified` flag to `user_emails` table via Liquibase.
    - Add `slug` to `companies` table via Liquibase (for configurable system org lookup).
    - Update `UserEmail` and `Company` JPA entities.
    - Create `CompanyRepository` and `MembershipRepository`.
- **Verification Strategy:**
    - Verify Liquibase migration success.
    - Unit tests for JPA entity changes.
    - Compile check for new repositories.

### [ ] Task 2.2: Super-Admin Bootstrapping
- **Description:** Ensure the system is always accessible by a Super-Admin on startup.
- **Actions:**
    - Implement `SuperAdminProperties` for type-safe ENV variable mapping.
    - Implement `BootstrapRunner` (ApplicationRunner) to create the system org and first super-admin if missing.
- **Verification Strategy:**
    - Integration test: Start application context with specific ENV variables and verify user creation.

### [ ] Task 2.3: JWT Token Customization
- **Description:** Inject custom claims into the JWT Access Token.
- **Actions:**
    - Update `CustomUserDetailsService` to return formatted authorities (`ORG_{id}_{ROLE}`).
    - Implement `OAuth2TokenCustomizer` to add `authorities`, `emails` (verified only), and `primary_email` claims.
- **Verification Strategy:**
    - Integration test: Perform an Authorization Code flow and inspect the resulting JWT claims.

## Execution Waves
- **Wave 1:** Schema, Domain & Repository Updates (Plan 02-01)
- **Wave 2:** Super-Admin & Token Customization (Plans 02-02, 02-03)

---
*Note: Detailed implementation steps for each wave are located in `.planning/phases/02-token-customization-resource-server/`.*
