# Pitfalls Research

**Domain:** IdP migration — Zitadel to custom Spring Authorization Server
**Researched:** 2026-05-02
**Confidence:** HIGH (based on direct codebase inspection + IdP migration patterns)

---

## Critical Pitfalls

### Pitfall 1: JWT Claim Shape Mismatch Silently Breaks Role Extraction

**What goes wrong:**
The backend's `SecurityConfig.jwtAuthenticationConverter()` reads roles from the Zitadel-specific nested map claim `urn:zitadel:iam:org:project:roles` with shape `{"roleName": {"orgId": "domain"}}`. The auth-service `TokenCustomizerConfig` emits a flat `Set<String>` in the `authorities` claim with shape `["ROLE_COMPANY_ADMIN", "ORG_<uuid>_COMPANY_ADMIN"]`. After the switch, the converter returns an empty list for every request. All `@PreAuthorize("hasRole('COMPANY_ADMIN')")` annotations silently grant no access — every endpoint that was COMPANY_ADMIN-gated returns 403.

**Why it happens:**
The converter checks for a claim named `urn:zitadel:iam:org:project:roles` — a key that will simply not exist in auth-service tokens. `getClaimAsMap` returns null, and the null-guard returns `List.of()`. No exception, no log, just empty authorities on every request.

**How to avoid:**
Update `SecurityConfig.jwtAuthenticationConverter()` before switching any traffic to read from the auth-service claim shape. The auth-service puts roles in `authorities` as a flat string collection. The converter must call `jwt.getClaimAsStringList("authorities")` and map directly to `SimpleGrantedAuthority`. Do this as the first backend change in any migration phase — it is a prerequisite for all other backend work.

**Warning signs:**
- Every authenticated request returns 403 even when the token is structurally valid.
- Integration tests pass when using `.authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))` (bypasses the converter) but fail end-to-end.
- Backend logs show zero authority extraction errors — the failure is silent.

**Testing strategy:**
Write a focused unit test for the converter that feeds it an auth-service-shaped JWT (with `authorities` claim) and asserts the resulting authority list is non-empty. Add a parallel test feeding a Zitadel-shaped JWT to verify the old path fails as expected. Run this before touching any other code.

**Phase to address:**
Backend token validation phase — first task, before any service wiring.

---

### Pitfall 2: `zitadel_sub` Column Becomes an Orphaned Foreign Key Reference

**What goes wrong:**
`UserAccountEntity` has `zitadel_sub VARCHAR(64) NOT NULL UNIQUE` in the `user_accounts` table. `CurrentUserResolver.resolveCurrentAccount()` calls `userAccountRepository.findByZitadelSub(jwt.getSubject())` on every business request. After migration, auth-service tokens carry a different `sub` value (auth-service internal UUID). No existing row matches. `resolveCurrentAccount()` throws `IllegalStateException("Account not found for sub ...")`, which bubbles up as a 500 on every authenticated endpoint.

**Why it happens:**
The `sub` claim in Zitadel tokens is a Zitadel user ID. The `sub` in auth-service tokens is the `User.id` UUID from the auth-service database — a completely different value for the same human. Unless users are re-provisioned or the lookup column is migrated, the local identity bridge is broken for all existing users.

**How to avoid:**
The column must be renamed from `zitadel_sub` to a provider-neutral name (e.g., `auth_user_id`) via a Liquibase changeset before any live traffic switches. Simultaneously, update `UserAccountEntity`, `UserAccountRepository.findByZitadelSub`, and `CurrentUserResolver` to use the new column name. All existing rows must have their `zitadel_sub` value replaced with the corresponding auth-service `User.id`, or the JIT provisioning flow must create fresh rows keyed by auth-service sub.

**Warning signs:**
- `IllegalStateException: Account not found for sub` in backend logs after a user successfully authenticates.
- Users get 500 errors immediately after login despite a valid token.
- `JitProvisioningService.provisionUser()` creates duplicate `UserAccountEntity` rows for every request (once per sub format).

**Testing strategy:**
Write an integration test that inserts a `UserAccountEntity` with an auth-service-format sub (UUID string), then makes a request with a JWT whose `sub` matches. Assert that `resolveCurrentAccount()` returns the correct entity. Also assert the lookup fails gracefully when sub is Zitadel-format.

**Phase to address:**
Database migration phase — Liquibase changeset required before any backend switch.

---

### Pitfall 3: InMemory RSA Key Pair Means Every Auth-Service Restart Invalidates All Sessions

**What goes wrong:**
`AuthorizationServerConfig.jwkSource()` calls `generateRsaKey()` which generates a fresh RSA key pair on every JVM startup. The `kid` (key ID) is a new `UUID.randomUUID()` each time. The backend caches JWKS from the auth-service issuer. After an auth-service restart, all previously issued tokens become unverifiable — the signing key is gone. Users are logged out globally on every auth-service deployment. In dev this is annoying; in prod it is a user-facing outage.

**Why it happens:**
The in-memory key is appropriate for early development but the code has no pathway to a persistent key. The backend's Spring Security JWKS cache (default TTL: 5 minutes) will not help because the old `kid` is permanently gone from the JWKS endpoint, not temporarily unavailable.

**How to avoid:**
Before switching any traffic to the auth-service, implement a persistent RSA key strategy. Store the key pair in a keystore file (PKCS12) loaded from a mounted secret in the container, or use a database-backed key store. The `kid` must be stable across restarts. The `RegisteredClientRepository` has the same problem — it is `InMemoryRegisteredClientRepository`, so all OAuth2 authorization codes and refresh tokens are lost on restart. Both must be made persistent together.

**Warning signs:**
- All access tokens become invalid after auth-service redeploy.
- Backend logs show `JWT signature does not match` or `No matching JWK found` after restarts.
- Silent refresh fails for all active browser sessions within minutes of deployment.

**Testing strategy:**
Restart the auth-service twice. Verify that a token issued before the first restart can still be validated after the second restart. Verify that the JWKS endpoint returns the same `kid` value on each startup.

**Phase to address:**
Auth-service hardening phase — before production, mandatory before any shared-environment deployment.

---

### Pitfall 4: Frontend Role Extraction Breaks on Auth-Service Claim Shape

**What goes wrong:**
`AuthService.getUserRoles()` in the frontend searches for a key matching `urn:zitadel:iam:org:project:roles` or similar Zitadel URN patterns. Auth-service tokens carry roles in `authorities` as a flat array. The key search loop in `getUserRoles()` finds nothing, returns `[]`. UI role guards (if any) stop working. The user can authenticate but role-gated UI elements vanish or always render as unauthorized.

**Why it happens:**
The frontend's role lookup is Zitadel-vocabulary-aware. It checks for `roles`, `urn:zitadel:iam:org:project:roles`, and `urn:zitadel:iam:org:project:{projectId}:roles`. None of these match `authorities`. The fallback `rolesObj` is `{}`, so `Object.keys({})` returns `[]`.

**How to avoid:**
Add `authorities` as the first candidate in the key search, or make the claim name configurable via `window.__env`. Since auth-service puts roles in `authorities`, add it explicitly before the Zitadel-specific keys. Remove Zitadel-specific key patterns after migration is confirmed working. Also update `getUserOrganizationId()` which looks for `urn:zitadel:iam:user:resourceowner:id` — the auth-service does not emit this claim.

**Warning signs:**
- Users can log in but have no roles in the frontend.
- Role-guarded UI components are hidden or inaccessible for all users.
- `AuthService.getUserRoles()` returns `[]` in browser console.

**Testing strategy:**
Unit test `AuthService.getUserRoles()` with a mock auth-service JWT payload (containing `authorities: ["ROLE_COMPANY_ADMIN"]`). Assert the returned array contains `"ROLE_COMPANY_ADMIN"`. Add a second test with a Zitadel-shaped payload to ensure backward-compatible reading (useful during parallel-running period).

**Phase to address:**
Frontend auth switch phase — must happen alongside or just after backend token validation phase.

---

### Pitfall 5: JIT Provisioning Still Reads Zitadel-Specific Claims After Token Switch

**What goes wrong:**
`JitProvisioningService.provisionUser()` extracts org context from `urn:zitadel:iam:user:resourceowner:id` and `urn:zitadel:iam:user:resourceowner:name` — claims that only exist in Zitadel tokens. Auth-service tokens do not carry these claims. Both `jwt.getClaimAsString("urn:zitadel:iam:user:resourceowner:id")` return null. `createOrganization(null, null)` is called, creating an org with `zitadel_org_id = null` — which violates the NOT NULL constraint and throws `DataIntegrityViolationException`. The filter swallows the exception (by design, to not fail requests), so every new user silently fails to provision. `resolveCurrentAccount()` then returns empty, cascading 500s.

**Why it happens:**
The JIT provisioner was designed around Zitadel's multi-org model where each user belongs to exactly one Zitadel org, identified by those claims. The auth-service model is different: a user can have multiple memberships, and org context is not in the token — membership is in the auth-service database.

**How to avoid:**
The JIT provisioner must be rewritten for the new model. It should no longer try to provision an org from token claims. Instead: (1) look up whether a `UserAccountEntity` exists for the `sub`, (2) if not, create one and link it to a default membership or leave org linkage to a separate membership-sync step. The org provisioning responsibility shifts to the auth-service invitation flow — which already creates `Company` and `Membership` records.

**Warning signs:**
- `DataIntegrityViolationException` in filter logs on first login of new users.
- New users authenticate successfully but get 500 on all subsequent API calls.
- `user_accounts` table has no new rows despite active logins.

**Testing strategy:**
Unit test `JitProvisioningService.provisionUser()` with an auth-service-shaped JWT (no Zitadel claims). Assert a `UserAccountEntity` is created without errors. Assert no org creation is attempted from token claims. Integration test: full auth flow with auth-service token through the filter chain, verify user is provisioned and the next request succeeds.

**Phase to address:**
JIT provisioner rewrite phase — coupled with the database migration phase.

---

### Pitfall 6: `MemberManagementService` and `MemberInviteService` Still Reference Zitadel SDK After Token Switch

**What goes wrong:**
Both services are fully coupled to `ZitadelManagementClient` for all member operations: listing members (via `listAllGrants`), changing roles (via `updateUserGrant`), removing members (via `deleteUser`), and inviting (via `addHumanUser` + `addUserGrant` + `createInviteCode`). After removing Zitadel, these calls have no replacement and the services are non-functional. This is not a subtle bug — it is a compile error once the Zitadel SDK is removed from the classpath.

**Why it happens:**
The migration plan is "complete replacement" but the services were not written with an interface layer. There is no `MemberManagementPort` or similar abstraction — the Zitadel client is wired in directly via `@RequiredArgsConstructor`.

**How to avoid:**
Before removing the Zitadel dependency, introduce thin service interfaces that the Zitadel client currently satisfies, then implement the auth-service-backed versions. Alternatively (given timeline pressure), rewrite both services directly against auth-service API calls in one phase. The auth-service already has `MembershipManagementController` and `InvitationManagementController` — these become the new backend for the operations. Map each Zitadel operation to its auth-service equivalent explicitly before writing code.

**Warning signs:**
- Compilation failure on Zitadel SDK removal if attempted before replacement.
- Member management endpoints all return 500 after switch if Zitadel client is still wired but points to a dead host.

**Testing strategy:**
Integration tests exist (`MemberManagementControllerIT`) and stub Zitadel responses via WireMock. These tests must be migrated to stub auth-service responses instead. Run the existing test suite against the new implementation — all 14 test cases must still pass with the same observable behavior.

**Phase to address:**
Member management rewrite phase — one of the largest and last backend phases before cutover.

---

### Pitfall 7: `StartupValidator` Blocks Application on Zitadel Unavailability

**What goes wrong:**
`StartupValidator.validateZitadelConfiguration()` runs on `ApplicationReadyEvent` and calls `zitadelClient.organizationExists()` and `zitadelClient.listUserIdsByRole()`. After Zitadel is decommissioned, these calls either fail with connection refused or are never cleaned up, leaving startup validation calling a dead service. In the best case, it logs errors on startup. In the worst case, if someone later re-enables validation (removes the local/test profile skip), the application fails to start in production.

**Why it happens:**
The validator is tightly coupled to Zitadel as an external dependency. It exists to assert Zitadel is properly configured. After migration, the invariant it checks (super-admin present in Zitadel) is no longer meaningful.

**How to avoid:**
Delete `StartupValidator` and `ZitadelConfig` entirely as part of the Zitadel removal phase. If startup validation of the auth-service is desired, write a new validator that checks auth-service health and super-admin presence via the auth-service management API. Do not leave dead validation code that references removed infrastructure.

**Warning signs:**
- Startup logs contain `ZitadelApiException` or connection errors even after Zitadel removal.
- Application startup time increases due to connection timeout to defunct Zitadel.

**Testing strategy:**
Verify application starts cleanly with `spring.profiles.active=local` AND with `spring.profiles.active=dev` (which currently triggers the validator). After Zitadel removal, startup must succeed in both profiles without Zitadel-related log lines.

**Phase to address:**
Infrastructure cleanup phase — Zitadel SDK and config removal.

---

### Pitfall 8: Scope Mismatch Prevents Token Issuance

**What goes wrong:**
The frontend requests `scope: 'openid profile email offline_access urn:zitadel:iam:user:resourceowner'` (note the Zitadel-specific scope). The auth-service `RegisteredClient` only has `OidcScopes.OPENID` and `OidcScopes.PROFILE` registered. Any scope not registered on the client is stripped during token issuance per OAuth2 spec. The `offline_access` scope (for refresh tokens) may also be absent. Result: no refresh tokens issued, and the Zitadel scope causes no error but silently disappears from the token.

**Why it happens:**
The frontend scope list was built for Zitadel. The auth-service client registration was built independently without cross-referencing the frontend's requested scopes. `angular-oauth2-oidc` will request whatever scopes are configured — if the server does not recognize them, they are silently dropped.

**How to avoid:**
Align the `RegisteredClient` scope list with exactly what the frontend requests: `openid`, `profile`, `email`, `offline_access`. Remove the Zitadel-specific scope from the frontend config. Ensure `AuthorizationGrantType.REFRESH_TOKEN` is added to the registered client (currently not present in `AuthorizationServerConfig`). Verify the token response includes `refresh_token` during end-to-end testing.

**Warning signs:**
- Users are logged out when the access token expires (no silent refresh).
- `token_refresh_error` events fire in the frontend's `AuthService`.
- Token introspection shows missing scopes.

**Testing strategy:**
`AuthorizationServerEndpointsTests` already exists in the auth-service test suite. Add a test asserting that the authorization code flow with `scope=openid profile email offline_access` returns a response containing `refresh_token`. Run end-to-end: log in, wait for access token expiry, verify silent refresh succeeds without re-prompting login.

**Phase to address:**
Frontend auth switch phase — alongside client registration finalization.

---

### Pitfall 9: Multi-Org Token Context Loss

**What goes wrong:**
Under Zitadel, the token carried org context via `urn:zitadel:iam:user:resourceowner:id`, and `MemberManagementService.validateCallerBelongsToOrg()` used the local `UserAccountEntity.organizationId` to enforce org isolation. The new auth-service model allows a user to be a member of multiple companies (see `Membership` entity). The token does not carry a "current org" claim — it carries all memberships via the `authorities` claim (e.g., `ORG_<uuid>_COMPANY_ADMIN`). If the backend's org authorization check still relies on `userAccount.organizationId` (a single org FK), users with multiple memberships cannot access all their orgs. Worse, a user account linked to org A could be used to access org B's resources if the check is wrong.

**Why it happens:**
The `UserAccountEntity` currently has a single `organization_id` column — one account per org. The new model has one auth-service user per human (not per org). The authorization check in `validateCallerBelongsToOrg()` must change from "does this user account belong to this org?" to "does this user have a membership in this org?"

**How to avoid:**
After migration, the org membership check must query whether the auth-service user ID (JWT `user_id` claim or `sub`) has a `Membership` record for the target org in the backend's local membership mirror — or call the auth-service management API to verify. Define this contract explicitly before writing any service code.

**Warning signs:**
- Users with multiple org memberships can only access one org.
- `validateCallerBelongsToOrg()` throws 403 for valid org members after migration.
- Users from org A can call org B's member management endpoints (authorization bypass).

**Testing strategy:**
Integration test: create a user with memberships in two orgs. Make requests to both org endpoints in the same session. Assert both succeed. Assert that a user with no membership in org C receives 403.

**Phase to address:**
Member management rewrite phase — authorization logic must be redesigned, not just ported.

---

### Pitfall 10: `env.js` Hardcodes Zitadel Client ID and Issuer

**What goes wrong:**
`frontend/src/assets/env.js` contains hardcoded `clientId: '368981534071324679'` (a Zitadel-format numeric ID) and `issuerUri: 'https://sso.dev.goaldone.de'`. The auth-service client ID is `test-client` (string). If `env.js` is not updated in sync with the auth-service client registration, the OIDC discovery document request goes to the wrong issuer, or the authorization request is made with the wrong `client_id`. The error is an OIDC protocol error, not an application error — the browser gets a 400 or 401 from the auth-service with no meaningful app-level log.

**Why it happens:**
`env.js` is a runtime-injected config, not compiled into the app. It is easy to forget to update when switching environments. There is no validation that `clientId` matches any registered client.

**How to avoid:**
Update `env.js` (and its environment-specific counterparts for dev/prod) as the first step in the frontend migration phase. The auth-service client ID and issuer URI are required inputs before any frontend testing can begin. Add a startup assertion in `AuthService.initialize()` that logs the resolved issuer and client ID clearly so misconfiguration is immediately visible in the browser console.

**Warning signs:**
- OIDC discovery fails with CORS error or 404 (wrong issuer URI).
- Authorization code flow fails at token exchange with `invalid_client` error.
- Browser console shows requests going to `sso.dev.goaldone.de` after migration.

**Testing strategy:**
E2E smoke test: navigate to app, observe network request to `<issuer>/.well-known/openid-configuration`, verify it resolves correctly. Confirm `client_id` in the authorization request matches a registered client in the auth-service.

**Phase to address:**
Frontend auth switch phase — first task, before any OIDC flow testing.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Keep `zitadel_sub` column name, just change its values | Avoids a Liquibase migration | Misleading column name survives forever; future developers are confused about what "Zitadel sub" means | Never — rename it now, the migration is happening anyway |
| Leave `StartupValidator` and only disable via profile flag | No code deletion required | Dead code referencing decommissioned system; will break if profile flag is ever removed | Never — delete it |
| In-memory `RegisteredClientRepository` in prod | Zero setup, works immediately | All refresh tokens and auth codes lost on restart; users lose sessions every deployment | Local development only |
| In-memory RSA key pair in prod | Zero key management burden | All sessions invalidated on restart; impossible to run multiple auth-service instances | Local development only |
| Wire `CurrentUserResolver` to accept both `zitadel_sub` and `user_id` sub formats | Easier parallel-running period | Dual-lookup logic creates ambiguity; hard to remove later; masks if old-format subs are still present | Only during explicit migration cutover window, not permanently |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Backend JWKS validation | Pointing `issuer-uri` at Zitadel after switching auth-service as the token issuer | Update `spring.security.oauth2.resourceserver.jwt.issuer-uri` to auth-service URL before processing any auth-service tokens |
| Auth-service `authorities` claim | Assuming Spring Security auto-maps the `authorities` claim to granted authorities | It does not — the `JwtAuthenticationConverter` must explicitly read `authorities` and map to `SimpleGrantedAuthority` |
| `angular-oauth2-oidc` silent refresh | Assuming `useSilentRefresh: false` + `offline_access` scope is sufficient | Must also ensure auth-service client has `REFRESH_TOKEN` grant type AND refresh token TTL is configured |
| Auth-service client registration | Using `InMemoryRegisteredClientRepository` in any shared environment | Must persist to database (`JdbcRegisteredClientRepository`) before any shared testing begins |
| WireMock test stubs | Tests stub `urn:zitadel:iam:...` claim shapes that no longer match | All integration tests must be updated to produce auth-service JWT claim shapes; stale stubs cause false test passes |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| JWKS endpoint fetched on every token validation | Auth latency spikes; auth-service overloaded | Spring Security caches JWKS by default (5min); verify cache is not disabled and auth-service has short response times | At moderate load if caching is accidentally disabled |
| `findByZitadelSub` (soon `findByAuthUserId`) called twice per request in `JitProvisioningService` (lines 46-48) | Unnecessary DB round-trip per authenticated request | Combine into a single `findByAuthUserId` + update-or-create pattern | At high request rates |
| `MemberManagementService.listMembers()` fetches all `userAccountRepository.findAll()` | O(n) with total users table | Replace with a targeted query by org ID after membership model migration | When total user count exceeds a few hundred |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Not rotating the RSA key pair periodically | Compromise of the long-lived signing key allows unlimited token forgery | Implement key rotation with overlapping JWKS entries (old kid stays for TTL of existing tokens) |
| Sharing the `{noop}secret` client secret in production | Client credential flows can be replayed by anyone | Use `{bcrypt}` secrets or switch to `CLIENT_SECRET_JWT` authentication method for all production clients |
| Removing Zitadel before all existing refresh tokens expire | Users with valid Zitadel refresh tokens silently lose sessions, some may be confused about data loss | Plan a token-TTL-length overlap period; or force re-login for all users at cutover |
| `MemberInviteService` not checking email uniqueness after migration | Duplicate users created in auth-service with same email | Auth-service must enforce unique email constraint; check response for conflict before assuming success |
| JIT provisioner exception is silently swallowed | Failed provisioning goes undetected; user gets 500 with no alert | Add structured log alert or metric increment on provisioning failure; do not silently swallow in production |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Global session invalidation at cutover | All users logged out simultaneously; "what happened?" confusion | Announce planned logout; or issue both Zitadel and auth-service tokens briefly during overlap |
| Invitation emails sent from auth-service reference new URL scheme | Invited users land on a broken flow if frontend redirect URIs are not updated | Ensure `redirectUri` in `RegisteredClient` and frontend `env.js` match before sending any invitations |
| Password reset flow is new (auth-service `PasswordResetController` vs Zitadel-managed) | Users who reset password during or after cutover are routed to a new UI they have not seen | Smoke-test the full password reset flow end-to-end before opening migration to real users |
| User state `INITIAL` vs `ACTIVE` semantics differ between Zitadel and auth-service | `MemberInviteService.reinviteMember()` checks for `USER_STATE_INITIAL` (Zitadel enum) — this will not match auth-service's `UserStatus.PENDING_VERIFICATION` | Replace Zitadel state string comparison with auth-service `UserStatus` enum check in reinvite logic |

---

## "Looks Done But Isn't" Checklist

- [ ] **Token validation:** Backend is validating tokens BUT the `jwtAuthenticationConverter` still reads Zitadel claim shape — verify with a raw auth-service token, not a mocked one.
- [ ] **JIT provisioning:** Users are being provisioned BUT only new users — verify that existing `UserAccountEntity` rows (with old `zitadel_sub` values) are migrated or the lookup handles both.
- [ ] **Member listing:** Endpoint returns 200 BUT data comes from Zitadel SDK stub in tests — verify the actual auth-service management API call is wired.
- [ ] **Refresh tokens:** Login works BUT sessions expire after 1 hour — verify `offline_access` scope is registered AND `REFRESH_TOKEN` grant type is on the client AND silent refresh fires.
- [ ] **RSA key persistence:** Auth-service starts correctly BUT all sessions die on restart — verify key pair is loaded from a persistent source, not generated at boot.
- [ ] **Org isolation:** Members API enforces org isolation BUT only tested with single-membership users — verify a user with two org memberships cannot access the wrong org.
- [ ] **Frontend roles:** UI renders for authenticated users BUT role-gated elements are hidden — verify `getUserRoles()` returns non-empty array with auth-service token.
- [ ] **Registered clients:** OIDC flow works in dev BUT in-memory client repo means no persistence — verify `JdbcRegisteredClientRepository` before any shared environment.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| JwtAuthenticationConverter wrong claim | LOW | Update converter, redeploy backend; no data changes needed |
| `zitadel_sub` lookup broken for existing users | HIGH | Write a Liquibase migration to update column values using a mapping table; requires mapping of Zitadel user IDs to auth-service user UUIDs before cutover |
| In-memory RSA key rotated by restart | MEDIUM | All active sessions lost; users must re-login; generate persistent key, reconfigure, redeploy |
| Zitadel SDK still in classpath after removal | LOW | Restore compile-time dependency, replace usages, rebuild |
| Auth-service client missing refresh token grant | LOW | Update `RegisteredClient` configuration, restart auth-service (sessions unaffected — new tokens will include refresh token) |
| Frontend `env.js` points to wrong issuer | LOW | Update `env.js`, redeploy frontend; no backend changes |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| JWT claim shape mismatch (roles) | Backend token validation phase | Unit test converter with auth-service JWT; run role-gated endpoint test |
| `zitadel_sub` lookup broken | Database migration phase | Integration test: lookup by auth-service sub succeeds |
| In-memory RSA key pair | Auth-service hardening phase | Restart auth-service twice; verify old tokens still validate |
| Frontend role extraction | Frontend auth switch phase | Unit test `getUserRoles()` with auth-service JWT payload |
| JIT provisioner reads Zitadel claims | JIT provisioner rewrite phase | Integration test: first-request provisioning with auth-service token |
| Member services coupled to Zitadel SDK | Member management rewrite phase | All 14 `MemberManagementControllerIT` tests pass against auth-service stubs |
| `StartupValidator` calls dead Zitadel | Infrastructure cleanup phase | App starts without Zitadel running; no connection errors in startup logs |
| Scope mismatch (refresh tokens) | Frontend auth switch phase | E2E: token expires, silent refresh fires, no re-login prompt |
| Multi-org token context loss | Member management rewrite phase | Integration test: user with two org memberships accesses both |
| `env.js` hardcodes Zitadel client ID | Frontend auth switch phase — first task | Browser network tab: discovery document request goes to auth-service URL |

---

## Sources

- Direct inspection of `SecurityConfig.java` (lines 89-103): Zitadel claim name hardcoded
- Direct inspection of `JitProvisioningService.java` (lines 54-55): Zitadel URN claims hardcoded
- Direct inspection of `AuthorizationServerConfig.java` (lines 94-104): in-memory RSA key generation
- Direct inspection of `AuthorizationServerConfig.java` (line 70): `InMemoryRegisteredClientRepository`
- Direct inspection of `TokenCustomizerConfig.java`: auth-service emits `authorities`, `emails`, `user_id`, `primary_email`
- Direct inspection of `AuthService.ts` (lines 89-111): frontend role key search logic
- Direct inspection of `MemberManagementService.java` + `MemberInviteService.java`: full Zitadel SDK coupling
- Direct inspection of `CurrentUserResolver.java`: `findByZitadelSub` call pattern
- Direct inspection of `env.js`: hardcoded Zitadel client ID and issuer

---

*Pitfalls research for: Zitadel to custom auth-service migration*
*Researched: 2026-05-02*
