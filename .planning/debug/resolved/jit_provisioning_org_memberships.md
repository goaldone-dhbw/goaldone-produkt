---
status: resolved
trigger: "Fix JIT provisioning so user gets correct organization memberships after auth-service login"
created: 2026-05-03T00:00:00Z
updated: 2026-05-03T21:55:00Z
---

## Current Focus

hypothesis: UserRepository.findByEmail() doesn't eagerly load memberships, so CustomUserDetails gets empty memberships list, JWT orgs claim is empty, TenantContextFilter rejects request with 403
test: Modify UserRepository.findByEmail() to fetch memberships eagerly with LEFT JOIN FETCH
expecting: After fix, JWT will have orgs claim populated, TenantContextFilter will allow requests with valid X-Org-ID headers
next_action: Add fetch to findByEmail() query in UserRepository

## Symptoms

expected: After user logs in via auth-service, calling `/users/accounts` with X-Org-ID header should succeed with 200 OK
actual: Request fails with 403 Forbidden and error "User is not a member of the requested organization"
errors: "X-Org-ID f70fa124-ed24-4757-89d9-a7dfc8f39035 not in user's memberships"
reproduction: 1. Login via auth-service 2. Call /users/accounts with X-Org-ID header matching organization
started: After auth-service JWT integration

## Eliminated

(none yet)

## Evidence

- timestamp: 2026-05-03
  checked: SecurityConfig.java (backend filter ordering)
  found: Filter order is BearerTokenAuthenticationFilter → JitProvisioningFilter → TenantContextFilter (lines 127-128)
  implication: TenantContextFilter checks JWT orgs claim AFTER JIT provisioning, so if JWT has orgs, it should work

- timestamp: 2026-05-03
  checked: TenantContextFilter.java (backend)
  found: Validates X-Org-ID header against jwt.getClaim("orgs") list, checking org.get("id") field (line 98)
  implication: 403 error means jwt.getClaim("orgs") is null or empty - the orgs claim is missing from JWT

- timestamp: 2026-05-03
  checked: TokenCustomizerConfig.java (auth-service)
  found: Creates JWT orgs claim from userDetails.getMemberships() (line 38) which is a List<MembershipInfo>
  implication: If getMemberships() returns empty list, JWT orgs claim will be empty array

- timestamp: 2026-05-03
  checked: UserRepository.java (auth-service)
  found: findByEmail() query does JOIN on emails but NO fetch/join on memberships (line 15)
  implication: User object returned is lazy-loaded for memberships; accessing memberships outside transaction may fail

- timestamp: 2026-05-03
  checked: CustomUserDetailsService.java (auth-service)
  found: Calls userRepository.findByEmail() which loads User, then passes to CustomUserDetails constructor
  implication: CustomUserDetails constructor accesses user.getMemberships() but memberships may not be loaded yet - potential lazy loading exception

- timestamp: 2026-05-03
  checked: CustomUserDetails.java constructor (auth-service)
  found: Line 51-53 accesses user.getMemberships() to build memberships list
  implication: If memberships not loaded from DB, this could get null or empty collection, resulting in empty JWT orgs claim

## Resolution

root_cause: UserRepository.findByEmail() and findByPrimaryEmail() do not eagerly fetch the memberships collection. When CustomUserDetailsService loads a User and creates CustomUserDetails, the memberships are not loaded. CustomUserDetails constructor accesses user.getMemberships(), which either returns an empty/uninitialized collection, causing TokenCustomizer to add an empty orgs claim to the JWT. Backend's TenantContextFilter then rejects requests because jwt.getClaim("orgs") is empty or null.

fix: Modified UserRepository.findByEmail() and findByPrimaryEmail() to use LEFT JOIN FETCH u.memberships m LEFT JOIN FETCH m.company to eagerly load all memberships and their associated companies in a single query. This ensures CustomUserDetails gets populated memberships, resulting in JWT orgs claim being populated correctly. Verified with:
- CustomUserDetailsServiceTests: PASSED (4 tests)
- TokenClaimsIntegrationTest: PASSED (1 test)
- UserRepositoryTests: PASSED (1 test) - SQL logs confirm LEFT JOIN FETCH is working
- SecurityIntegrationTest: PASSED (5 tests)

verification: PASSED - All tests pass, query logs show LEFT JOIN FETCH working correctly, memberships are loaded eagerly

files_changed:
- auth-service/src/main/java/de/goaldone/authservice/repository/UserRepository.java (2 methods updated with LEFT JOIN FETCH)
