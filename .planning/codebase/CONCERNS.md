# Codebase Concerns

**Analysis Date:** 2026-05-02

## Tech Debt

### Tight Coupling to Zitadel APIs

**Area:** Authentication and User Management

- **Issue:** Backend code tightly couples to Zitadel SDK and Management v1 REST APIs. Role claim extraction, user provisioning, and organization management all hardcoded for Zitadel.
- **Files:** 
  - `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` (lines 88-103) - Zitadel-specific role claim parsing with hardcoded `urn:zitadel:iam:org:project:roles` claim key
  - `backend/src/main/java/de/goaldone/backend/config/ZitadelConfig.java` - Direct Zitadel SDK initialization
  - `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java` (entire file) - Heavy coupling to Zitadel Java SDK and Management API v1 REST endpoints
  - `backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java` (lines 54-55) - Extracts Zitadel-specific org claims from JWT
  - `backend/src/main/java/de/goaldone/backend/service/UserIdentityService.java` (lines 92-93) - Direct Zitadel API calls for role retrieval
- **Impact:** Migration to custom auth service (`auth-service/`) requires refactoring all role/claim extraction, user provisioning, and client calls. No abstraction layer exists to swap auth providers.
- **Fix approach:** Create abstraction layer for auth provider operations before full migration:
  1. Extract `IAuthProvider` interface with methods: `provisionUser()`, `getUserRoles()`, `getUserInfo()`, `getUser()`, etc.
  2. Implement `ZitadelAuthProvider` wrapper around current code
  3. Implement `CustomAuthProvider` for new `auth-service/`
  4. Inject provider via Spring configuration, swap at runtime

### JIT Provisioning Filter Does Not Fail-Safe on Auth Service Issues

**Area:** User Provisioning Flow

- **Issue:** `JitProvisioningFilter` swallows all exceptions during provisioning (line 48-50 in `JitProvisioningFilter.java`). If user identity cannot be created due to FK constraint or race condition, the filter logs but allows request to proceed. User might not be fully provisioned.
- **Files:** `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java` (lines 48-51)
- **Impact:** User receives successful authentication but missing database record. Subsequent calls fail with "Account not found after JIT provisioning" (see `CurrentUserResolver` line 48). Silent failure makes debugging harder.
- **Fix approach:** 
  1. Return different HTTP status (401/403) if provisioning fails unrecoverably (not just race conditions)
  2. Distinguish transient failures (race condition, retriable) vs. permanent (FK missing, org deleted)
  3. Add metrics/alerts for provisioning failures
  4. Ensure FK constraint on `user_identity_id` cannot be violated

### Role Claim Extraction Complexity and Brittleness

**Area:** JWT Role Parsing

- **Issue:** Role extraction hardcodes Zitadel claim structure. Claim key `urn:zitadel:iam:org:project:roles` is not configurable. Structure is a map of role names to metadata (lines 94-100 in `SecurityConfig.java`). If Zitadel changes structure or custom auth service uses different format, code breaks.
- **Files:**
  - `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` (lines 88-103)
  - Test in `backend/src/test/java/de/goaldone/backend/controller/AccountLinkingIntegrationTest.java` (lines 427-441) builds JWT with hardcoded claim structure
- **Impact:** Cannot easily test with different role structures. Integration tests brittle to claim format changes. No mapping between Zitadel role names and application roles.
- **Fix approach:**
  1. Extract claim key to configurable property: `auth.roles-claim-key`
  2. Add claim path resolution (support nested structures)
  3. Add role name mapping: `auth.role-mapping: { "zitadel-admin": "ADMIN", "zitadel-user": "USER" }`
  4. Create `RoleClaimExtractor` interface to allow different extraction strategies

### FK Constraint Enforcement Between UserIdentityEntity and UserAccountEntity

**Area:** Account Linking Data Model

- **Issue:** `UserIdentityEntity` is minimal entity with only `id` and `created_at`. No inverse relationship back to `UserAccountEntity`. If all `UserAccountEntity` records for an identity are deleted, orphaned identity remains. If identity is deleted while accounts reference it, FK constraint violation occurs.
- **Files:**
  - `backend/src/main/java/de/goaldone/backend/entity/UserIdentityEntity.java` - No relationships defined
  - `backend/src/main/java/de/goaldone/backend/entity/UserAccountEntity.java` - Has FK to identity but no back-reference
  - `backend/src/main/resources/db/changelog/changes/003-user-identity-linking.xml` (lines 45-51) - FK created with no CASCADE delete
- **Impact:** Memory leak of orphaned identities over time. Account linking code deletes identity (line 110 in `AccountLinkingService.java`) assuming all accounts already moved, but if move fails, orphaned account-less identity persists. Tests pass but production data corrupts.
- **Fix approach:**
  1. Add `@OneToMany(mappedBy="userIdentity", cascade=CascadeType.DELETE)` to `UserIdentityEntity` to formalize relationship
  2. Modify `AccountLinkingService.unlink()` to only delete identity if no accounts remain (add count check before delete)
  3. Create scheduled job to clean orphaned identities with no accounts
  4. Add integrity check on startup to log any orphaned identities

### Weak Foreign Key Constraint on LinkTokenEntity

**Area:** Link Token Lifecycle

- **Issue:** `LinkTokenEntity.initiator_account_id` has FK constraint with `ON DELETE CASCADE` (line 86 in migration XML). If initiator account is deleted, token is silently deleted. Code assumes token existence but doesn't handle deletion. Also, token TTL is only 10 minutes but cleanup is manual job running hourly - stale tokens accumulate.
- **Files:**
  - `backend/src/main/java/de/goaldone/backend/entity/LinkTokenEntity.java` - No FK defined in entity, only in migration
  - `backend/src/main/java/de/goaldone/backend/service/AccountLinkingService.java` (lines 79-84, 164-169) - Cleanup job runs hourly, tokens valid 10 minutes
- **Impact:** If initiator account deleted (user removed, org deleted), confirmer can still use token but will fail on account lookup with generic "Account not found" instead of proper error.
- **Fix approach:**
  1. Add `@ManyToOne` FK explicitly to entity with `fetch=LAZY`
  2. Document cascade behavior in code comments
  3. Change cleanup to run every minute (or implement immediate cleanup on account deletion)
  4. Add test for cascade delete scenario

## Known Bugs

### JIT Provisioning Race Condition on UserIdentityEntity Creation

**Area:** Multi-threaded Provisioning

- **Symptoms:** Two concurrent requests from same Zitadel user sometimes create two `UserIdentityEntity` records, but both accounts link to one identity by chance, resulting in orphaned second identity.
- **Files:** `backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java` (lines 60-78)
- **Trigger:** 
  1. Two requests arrive simultaneously for new user from same org
  2. Both check `findByZitadelSub()` - both find nothing
  3. Both call `findByZitadelOrgId()` - both find/create same org
  4. Both create separate `UserIdentityEntity` (line 63-65)
  5. Both save to DB - one succeeds, one fails, but catch block only handles org race, not identity race
  6. Only first account uses first identity, second account gets first identity (lines 72)
- **Workaround:** None - relies on low concurrency in practice. No distributed lock.
- **Fix:** 
  1. Add unique constraint on `(zitadel_sub, user_identity_id)` to detect duplicates
  2. Use database-level unique constraint on identity creation with retry logic
  3. Acquire row-level lock on Zitadel sub during provisioning

### Integration Test JWT Setup Requires Manual authorities() Call

**Area:** Testing with Spring Security

- **Symptoms:** When using `.jwt()` helper in `MockMvc`, role-based `@PreAuthorize` tests fail with 403 even though roles are in JWT claim. Custom `JwtAuthenticationConverter` is not applied in test context.
- **Files:** 
  - Test pattern in `backend/src/test/java/de/goaldone/backend/controller/AccountLinkingIntegrationTest.java` (lines 87-88) - builds JWT but doesn't add `.authorities()`
  - `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` (lines 88-103) - converter defined in production but skipped in tests
- **Trigger:** Run test with role-based authorization like `@PreAuthorize("hasRole('ADMIN')")`
- **Workaround:** Must manually add `.authorities(List.of(new SimpleGrantedAuthority("ROLE_..."))` to `.jwt()` builder in tests
- **Fix:** Create test utility `JwtTestUtils.buildAuthenticatedJwt()` that applies role conversion, or auto-configure `JwtAuthenticationConverter` for tests

### UserIdentityService Makes N+1 API Calls to Zitadel

**Area:** Performance - Account List Endpoint

- **Symptoms:** `/users/accounts` endpoint slow when user has 10+ linked accounts. Makes separate API call per account to get user roles.
- **Files:** `backend/src/main/java/de/goaldone/backend/service/UserIdentityService.java` (lines 88-120)
- **Trigger:** User has 3 linked accounts. Endpoint makes 1 call to get identity, then 3 API calls (lines 92-93) to `zitadelManagementClient.getUserGrantRoles()` for each account.
- **Impact:** Response time ~300ms + 3×100ms = 600ms+ for 3 accounts. Scales O(n).
- **Fix:**
  1. Batch role fetching: `zitadelManagementClient.getUserGrantRolesBatch(List<userId>)`
  2. Cache role results for 5 minutes
  3. Return roles lazily in optional field, or fetch in separate endpoint

## Security Considerations

### Service Account Token Hardcoded in Config

**Area:** Secret Management

- **Risk:** `zitadel.service-account-token` property must be set via environment variable but is used directly in `ZitadelConfig.java` and `ZitadelManagementClient` without validation. If leaked in logs or error messages, full Zitadel admin access compromised.
- **Files:**
  - `backend/src/main/java/de/goaldone/backend/config/ZitadelConfig.java` (line 25)
  - `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java` (line 78)
- **Current mitigation:** Token passed via environment variable, likely not logged
- **Recommendations:**
  1. Add validation on token presence at startup (see `StartupValidator`)
  2. Never log full token, only hash or first 8 chars
  3. Audit all error messages that might include token
  4. Add token rotation capability (bearer token refresh)
  5. Use Spring Vault or similar for secret management

### Zitadel API Calls Made with Bearer Token in URL/Headers

**Area:** Token Exposure

- **Risk:** Service account token included in HTTP headers (`Authorization: Bearer <token>`) for every Zitadel API call. If HTTP request logged, token visible. If interceptor installed, token sniffed.
- **Files:** `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java` (multiple lines with `Bearer ` + token)
- **Current mitigation:** HTTPS only (assumed), token rotation not supported
- **Recommendations:**
  1. Add request logging filter that masks bearer token
  2. Implement request signing instead of bearer tokens if possible
  3. Add audit trail of all API calls made
  4. Limit token scope to minimum required permissions (read-only where possible)

### JWT Subject (sub) Claim Uniqueness Not Enforced at DB Level

**Area:** Authentication Identity

- **Risk:** `UserAccountEntity.zitadelSub` has `unique=true` constraint, but if Zitadel sub is compromised or reused, account hijacking possible. No additional validation.
- **Files:** `backend/src/main/java/de/goaldone/backend/entity/UserAccountEntity.java` (line 28)
- **Current mitigation:** Unique constraint prevents duplicates
- **Recommendations:**
  1. Add secondary identity verification (email, user name) in addition to sub
  2. Implement account linking approval workflow before merging identities
  3. Add audit log of all identity changes
  4. Validate JWT issuer and issuer URI on every request

### Account Linking Allows Merging Identities Without Email Verification

**Area:** Authorization - Account Linking

- **Risk:** User can request link token, pass it to another user, and confirm link without email verification. No out-of-band confirmation required.
- **Files:** `backend/src/main/java/de/goaldone/backend/service/AccountLinkingService.java` (lines 78-120)
- **Impact:** Attacker with access to second account can link it to first account, gaining access to victim's data across organizations.
- **Recommendations:**
  1. Add email confirmation step: send link token to email of account being linked
  2. Add rate limiting on `requestLink()` and `confirmLink()`
  3. Add user notification when account linking initiated/completed
  4. Add 24-hour confirmation window instead of 10 minutes
  5. Log all linking operations for audit trail

### Role-Based Authorization Not Consistently Applied

**Area:** Method-level Security

- **Risk:** `@EnableMethodSecurity` enabled in `SecurityConfig`, but not all endpoints have `@PreAuthorize` guards. Only role-based checks present, no resource-level ownership checks.
- **Files:** 
  - `backend/src/main/java/de/goaldone/backend/config/SecurityConfig.java` (line 32)
  - Endpoints in `backend/src/main/java/de/goaldone/backend/controller/` - many missing authorization checks
- **Impact:** Unauthenticated users get 401, but authenticated users might access data from other organizations without explicit authorization.
- **Recommendations:**
  1. Audit all endpoints for missing authorization checks
  2. Add resource-level checks: verify account belongs to current user's identity
  3. Add organization-level checks: verify account belongs to current user's organization
  4. Create custom `@RequireAccountAccess` annotation to reduce boilerplate
  5. Add integration tests verifying 403 for unauthorized access

## Performance Bottlenecks

### JIT Provisioning Synchronous in Filter Chain

**Problem:** User provisioning (org lookup/create, identity create, account create) happens synchronously in HTTP request filter. If Zitadel/database slow, request blocked.
- **Files:** `backend/src/main/java/de/goaldone/backend/filter/JitProvisioningFilter.java` (lines 46-47)
- **Cause:** Filter is imperative, cannot return before provisioning completes
- **Improvement path:**
  1. Make provisioning async: store JWT info in cache, background job provisions
  2. Cache identity/org lookup for 10 seconds to avoid repeated DB hits
  3. Add circuit breaker for Zitadel calls: skip provisioning if timeout/5xx

### Zitadel Management Client No Connection Pooling

**Problem:** Each `RestClient` call creates new HTTP connection. No pooling configured.
- **Files:** `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java` (lines 74-82)
- **Cause:** RestClient instantiated per method, no configuration of pool size
- **Improvement path:**
  1. Configure `RestClient.Builder` with connection pool: `clientHttpRequestFactory(HttpComponentsClientHttpRequestFactory)` with pool size 10-20
  2. Add timeout configuration: connection timeout 5s, read timeout 10s
  3. Add retry policy with exponential backoff for transient failures

## Fragile Areas

### Account Linking Unlink Operation Vulnerable to Race Conditions

**Area:** Account Unlinking

- **Files:** `backend/src/main/java/de/goaldone/backend/service/AccountLinkingService.java` (lines 131-158)
- **Why fragile:** 
  1. Checks if account is last in identity (line 142-145): `countByUserIdentityId()`
  2. But between check and unlink, another account might be unlinked by concurrent request
  3. No transaction-level locking on identity count
  4. Multiple PATCH requests to same endpoint might both succeed
- **Safe modification:**
  1. Add pessimistic lock: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on identity row
  2. Use `SELECT ... FOR UPDATE` in repository query
  3. Add test for concurrent unlink attempts
- **Test coverage:** No test for concurrent unlink

### Organization Deletion Cascade Not Complete

**Area:** Data Integrity

- **Files:** Multiple - no orphan check after org deletion
- **Why fragile:**
  1. Deleting organization does not cascade to accounts, identities, working times, tasks
  2. `SuperAdminService` deletes org from DB, but accounts remain with dangling FK
  3. Cascading deletes commented as TODO in code (line 153 in `SuperAdminService.java`)
- **Safe modification:**
  1. Make `organization_id` FK `ON DELETE CASCADE` (instead of fail)
  2. Or add service method to delete org + all related records in transaction
  3. Add soft delete (deleted_at timestamp) instead of hard delete

### CurrentUserResolver Fails Silently If Account Not Found

**Area:** Current User Resolution

- **Files:** `backend/src/main/java/de/goaldone/backend/service/CurrentUserResolver.java` (lines 46-48, 71-75)
- **Why fragile:**
  1. If JIT provisioning fails and account not in DB, `resolveCurrentAccount()` throws `IllegalStateException` with generic message
  2. Caller doesn't distinguish "not yet provisioned" vs. "account deleted" vs. "database corrupted"
  3. No fallback or retry logic
- **Safe modification:**
  1. Add specific exception types: `NotProvisionedException`, `AccountDeletedException`
  2. Catch in filter and return 401 (retry login) vs. 500 (internal error)
  3. Add retry with exponential backoff before throwing

## Test Coverage Gaps

### Integration Tests JWT Role Claim Not Auto-Applied

**Area:** Role Authorization Testing

- **What's not tested:** Tests that verify `@PreAuthorize` on endpoints actually enforce roles
- **Files:** `backend/src/test/java/de/goaldone/backend/controller/AccountLinkingIntegrationTest.java` - tests only happy path with valid roles
- **Risk:** Endpoint might not check roles, tests pass but production has security hole
- **Priority:** High - affects authorization
- **Fix:**
  1. Add test for 403 when calling endpoint without required role
  2. Create `JwtTestBuilder` utility to handle role-to-authority conversion
  3. Add parameterized test: same endpoint, different roles, verify 403 for insufficient role

### Account Linking Concurrent Unlink Not Tested

**Area:** Race Condition Testing

- **What's not tested:** Two concurrent requests to unlink same account might both succeed
- **Files:** No test in `AccountLinkingIntegrationTest`
- **Risk:** Data corruption (identity deleted while accounts reference it)
- **Priority:** High - affects data integrity
- **Fix:**
  1. Add test using `ExecutorService.submit()` with 10 concurrent unlink requests
  2. Verify only 1 succeeds (others get 409 Conflict or 400 Bad Request)

### Zitadel API Client Error Handling Not Tested

**Area:** External API Resilience

- **What's not tested:** How ZitadelManagementClient behaves on API errors (4xx, 5xx, timeout, connection refused)
- **Files:** No test for `RestClientResponseException` or timeout scenarios
- **Risk:** Production errors not handled, request hangs or crashes
- **Priority:** Medium - affects resilience
- **Fix:**
  1. Add test mocking Zitadel returning 500
  2. Verify `ZitadelApiException` thrown with proper message
  3. Add test for timeout scenario
  4. Add test for network error

### JIT Provisioning Race Condition Not Tested

**Area:** Concurrency Testing

- **What's not tested:** Two simultaneous requests from same user both creating identities
- **Files:** No test in `JitProvisioningServiceTest`
- **Risk:** Orphaned identities accumulate over time
- **Priority:** High - affects data integrity
- **Fix:**
  1. Use `CountDownLatch` to synchronize two threads
  2. Mock repo to fail once, succeed second time (simulate race)
  3. Verify only one identity created

## Missing Critical Features

### Auth Provider Abstraction

**Area:** Custom Auth Service Integration

- **Problem:** Migrating from Zitadel to custom `auth-service/` requires changing code in 10+ files. No abstraction to swap auth providers.
- **Blocks:** Full migration to custom auth service, supporting multiple auth providers
- **Fix:** Create `IAuthProvider` interface (see Tech Debt section above)

### Token Refresh Mechanism

**Area:** JWT Token Lifecycle

- **Problem:** JWT tokens fixed to issuer-uri from Zitadel. No mechanism to refresh or extend token lifetime if user stays active. Token expires after 1 hour (standard), user must re-login.
- **Blocks:** Long-lived sessions, better UX
- **Fix:**
  1. Implement refresh token flow (OAuth2 standard)
  2. Add refresh endpoint `/auth/token/refresh`
  3. Store refresh tokens in DB with rotation

### Audit Trail for Auth Events

**Area:** Security & Compliance

- **Problem:** No logging of auth events: login, logout, role changes, account linking, password resets.
- **Blocks:** Compliance, forensics, fraud detection
- **Fix:**
  1. Create `AuthAuditLog` entity with user, action, timestamp, IP, result
  2. Log in filters and services
  3. Add admin endpoint to query audit logs

### Organization/Project Isolation

**Area:** Multi-tenancy

- **Problem:** Zitadel organization mapping is one-to-one. If user has accounts in multiple Zitadel organizations, they all map to one GoalDone organization. No true multi-org support in GoalDone.
- **Blocks:** SaaS deployment, org-level data isolation, org-level member management
- **Fix:**
  1. Map `UserAccount.organizationId` to actual organizations, not just JIT-created ones
  2. Enforce org-level isolation in queries: `where organization_id = current_user_org_id`
  3. Add org membership entity separate from account linking

---

*Concerns audit: 2026-05-02*
