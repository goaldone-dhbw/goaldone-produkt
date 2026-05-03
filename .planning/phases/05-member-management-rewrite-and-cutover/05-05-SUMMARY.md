# Plan 05-05 Summary — Liquibase PK Unification, Entity Cleanup & Zitadel SDK Removal

## What Was Done

This final plan of Phase 5 completed the Zitadel→Custom Auth-Service cutover:

### Task 1 — Liquibase PK Unification Migrations
- Created `013-pk-unification.xml`: Updates org and user PKs from Zitadel string IDs to auth-service UUIDs, then drops `auth_company_id` and `auth_user_id` mapping columns. Uses `preConditions onFail="MARK_RAN"` for idempotency.
- Created `014-add-invitation-id.xml`: Adds `invitation_id`/`status` to memberships (skipped via precondition since Plan 03 already added these columns).
- Updated `db.changelog-master.xml` to include both new files.

### Task 2 — Entity + Code Cleanup + Zitadel SDK Removal
- Removed `authCompanyId` from `OrganizationEntity`; removed `authUserId` from `UserEntity`.
- Removed `findByAuthCompanyId`/`findByAuthUserId` from repositories; added UUID-native methods (`findFirstByUserId`, `findByUserIdAndOrganizationId`).
- Rewrote `JitProvisioningService` to use UUID-based PKs directly from JWT claims.
- Simplified `SecurityConfig.AuthorizationLogic.isMember()` — no DB lookup needed; `orgId.toString()` compared directly to JWT `orgs` claim (removed `OrganizationRepository` dependency entirely).
- Updated `CurrentUserResolver`, `UserService`, `MembershipDeletionService`, `MemberManagementService`, `SuperAdminService`, `AccountLinkingService` to use UUID-based queries.
- Deleted 5 Zitadel files: `ZitadelManagementClient.java`, `ZitadelConfig.java`, `ZitadelApiException.java`, `ZitadelUserInfo.java`, `ZitadelUserInfoClient.java`.
- Deleted dead code `UserGrantDto.java` (referenced only by deleted Zitadel client).
- Removed `io.github.zitadel:client:4.1.2` from `pom.xml`.
- Removed `zitadel.*` block from `application.yaml`; renamed `ZITADEL_ISSUER_URI` → `OIDC_ISSUER_URI`.
- Updated Javadoc in `EmailAlreadyInUseException.java` and `JitProvisioningFilter.java`.
- Removed deprecated `zitadelOrganizationId` field from `OrganizationResponse` in OpenAPI spec (redundant after PK unification — `id` contains the same value).
- Removed `ZitadelApiException` handler from `GlobalExceptionHandler`.

### Task 3 — Integration Tests
- Created `MemberManagementServiceTest.java` with 5 tests: listMembers, changeMemberRole (success/409), removeMember (success/409).
- Created `MemberInviteServiceTest.java` with 3 tests: inviteMember creates INVITED membership, reinvite cancels old invitation and updates ID, reinvite with no prior invitation.
- Used `@MockitoSettings(strictness = Strictness.LENIENT)` to avoid `UnnecessaryStubbingException` from shared JWT mock.

## Acceptance Criteria Verification

| Criterion | Status |
|-----------|--------|
| `grep -r "zitadel" backend/src/main/ --include="*.java"` returns no matches | ✅ |
| `grep -r "zitadel" backend/src/main/ --include="*.yaml"` returns no matches | ✅ |
| `ZitadelManagementClient.java` does NOT exist | ✅ |
| `ZitadelConfig.java` does NOT exist | ✅ |
| `ZitadelApiException.java` does NOT exist | ✅ |
| `ZitadelUserInfo.java` does NOT exist | ✅ |
| `ZitadelUserInfoClient.java` does NOT exist | ✅ |
| `pom.xml` does NOT contain `io.github.zitadel` | ✅ |
| `application.yaml` does NOT contain `zitadel` | ✅ |
| `application-local.yaml` does NOT contain `zitadel` | ✅ |
| Backend compiles clean | ✅ |
| 8 new unit tests pass | ✅ (3 MemberInviteServiceTest + 5 MemberManagementServiceTest) |
| Pre-existing `BackendApplicationTests.contextLoads` failure (012-5 H2 bug) unchanged | expected ⚠️ |

## Key Technical Notes

- **Liquibase `<preConditions>` ordering**: Must be the FIRST child element inside `<changeSet>`, before `<comment>`. SAXParseException with unhelpful message if wrong.
- **Changeset uniqueness**: Identified by `id + author + filename` — new `013-pk-unification.xml` safely uses `id="013-1" author="phase5"` without conflicting with existing `id="013-1" author="gsd"` in `013-add-invited-membership-support.xml`.
- **SecurityConfig simplification**: After PK unification, `org.id == auth-service UUID`, so `orgId.toString()` compares directly to JWT `orgs` claim — removed DB roundtrip and `OrganizationRepository` dependency from `AuthorizationLogic`.
- **OpenAPI cleanup**: `zitadelOrganizationId` removed from `OrganizationResponse` (was always equal to `id` after PK unification; frontend did not use it).
