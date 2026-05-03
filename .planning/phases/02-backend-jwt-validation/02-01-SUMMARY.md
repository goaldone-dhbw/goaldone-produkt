# Phase 02-01 Summary: Generic Auth Identifier Refactoring

The refactoring of the backend to use generic authentication identifiers instead of Zitadel-specific ones has been completed.

## Changes Performed

### 1. Database & Entities
- Liquibase changelogs were updated to rename `zitadel_sub` to `auth_user_id` and `zitadel_org_id` to `auth_company_id`.
- `UserAccountEntity` and `OrganizationEntity` were refactored to use `authUserId` and `authCompanyId` fields.

### 2. Repositories
- `UserAccountRepository` and `OrganizationRepository` method names were updated:
    - `findByZitadelSub` -> `findByAuthUserId`
    - `findByZitadelOrgId` -> `findByAuthCompanyId`

### 3. Services
- All service layer logic (e.g., `JitProvisioningService`, `MemberManagementService`, `CurrentUserResolver`) was updated to use the new field and method names.

### 4. Tests
- A batch refactor was performed on all test files in `backend/src/test` to rename:
    - `findByZitadelSub` -> `findByAuthUserId`
    - `findByZitadelOrgId` -> `findByAuthCompanyId`
    - `getZitadelSub` -> `getAuthUserId`
    - `getZitadelOrgId` -> `getAuthCompanyId`
    - `setZitadelSub` -> `setAuthUserId`
    - `setZitadelOrgId` -> `setAuthCompanyId`
    - `zitadelSub` -> `authUserId`
    - `zitadelOrgId` -> `authCompanyId`

## Verification
- Successfully ran `mvn clean compile` in the backend directory.
- Verified that no Zitadel-specific identifier strings remain in the test codebase.
