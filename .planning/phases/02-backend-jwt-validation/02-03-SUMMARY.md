# Phase 02-03 Summary: Bulk JIT Provisioning

The JIT provisioning logic has been updated to support the new multi-organization `orgs` claim and bulk membership provisioning.

## Changes Performed

### 1. Database Schema
- Added Liquibase migration `011-allow-multiple-orgs-per-user.xml` to:
    - Drop the unique constraint on `auth_user_id` in the `user_accounts` table.
    - Add a composite unique constraint on `(auth_user_id, organization_id)`.
- This allows a single authentication identity to hold accounts in multiple organizations.

### 2. Repositories
- Updated `UserAccountRepository` with:
    - `findAllByAuthUserId(String authUserId)`: To find all memberships for a user identity.
    - `findByAuthUserIdAndOrganizationId(String authUserId, UUID organizationId)`: To find a specific membership.

### 3. Services
- Refactored `JitProvisioningService.provisionUser(Jwt jwt)` to:
    - Extract `user_id` (primary) or `sub` (fallback) as `authUserId`.
    - Extract the `orgs` list from the JWT.
    - Iterate through all organizations in the `orgs` claim.
    - For each organization:
        - Resolve or create the `OrganizationEntity`.
        - Ensure a `UserAccountEntity` exists for the `authUserId` and `organizationId`.
        - Link all accounts to a single shared `UserIdentityEntity` per `authUserId`.
    - Logic is idempotent and updates `lastSeenAt` for existing accounts.

### 4. Filters
- `JitProvisioningFilter` continues to pass the `Jwt` object to the service, which now handles the new claims internally.

## Verification
- Successfully ran `JitProvisioningServiceTest`, including a new test case for multiple organizations (`provisionUser_newUserMultipleOrgs_provisionsAll`).
- Backend build verified with `mvn clean compile`.
