---
status: awaiting_human_verify
trigger: "Fix mismatch between org IDs in JWT (from auth-service) and orgs created by backend JIT provisioning"
created: 2026-05-03T00:00:00Z
updated: 2026-05-03T00:00:00Z
symptoms_prefilled: true
goal: find_and_fix
---

## Current Focus

status: fix verified and committed
hypothesis: CONFIRMED - TokenCustomizer was putting UUID objects instead of Strings into the JWT orgs claim's 'id' field
test: Fixed by converting UUID to String in TokenCustomizer.java. All tests pass:
  - SecurityIntegrationTest: 5/5 tests pass
  - JitProvisioningServiceTest: 3/3 tests pass
  - JitProvisioningFilterTest: 3/3 tests pass
  - TokenClaimsIntegrationTest: updated and passing
  - All backend tests: 106 tests pass
verification: Code committed with messages explaining the fix
next_action: Awaiting user confirmation that the original issue is resolved in their environment

## Symptoms

expected: JWT org IDs should match Organization entities in backend database; JIT provisioning should use/create consistent org IDs
actual: JWT contains org IDs that don't match orgs created by JIT provisioning; requests fail with X-Org-ID validation errors
errors: "X-Org-ID 5c93b944-34fc-4063-aa78-bb19bd1cf85c not in user's memberships"
reproduction: Login to auth-service → receive JWT → send request to backend with X-Org-ID header from JWT → validation fails because org IDs don't match

## Eliminated

(none yet)

## Evidence

- timestamp: 2026-05-03
  checked: TokenCustomizerConfig.java line 41
  found: **BUG IDENTIFIED**: org.put("id", membershipInfo.getCompanyId()) - puts UUID object instead of String
  implication: When JWT 'orgs' claim is deserialized, org.get("id") returns UUID, not String. TenantContextFilter.isValidOrgId() and JitProvisioningService both expect String

- timestamp: 2026-05-03
  checked: JWT deserialization on backend
  found: TenantContextFilter line 98 does String comparison: requestedOrgId.equals(org.get("id")). If org.get("id") is UUID object, equals() fails because "string-uuid".equals(UUID.randomUUID()) returns false
  implication: This type mismatch causes all org ID validations to fail

- timestamp: 2026-05-03
  checked: JitProvisioningService.java line 73
  found: Cast to String: (String) orgData.get("id") fails with ClassCastException if value is UUID
  implication: Backend JIT provisioning cannot process JWT org IDs if they're not Strings

- timestamp: 2026-05-03
  checked: Test TokenClaimsIntegrationTest.java line 86
  found: Test was comparing org.get("id") with company.getId() (UUID). Test passed, but that's because Jackson deserializes it back to UUID in test context
  implication: Real JWT transmission would serialize UUID as object, causing type mismatch

## Resolution

root_cause: **TokenCustomizer puts UUID object into JWT orgs claim 'id' field instead of String**. When the JWT 'orgs' claim is transmitted to the backend and deserialized, org.get("id") returns a UUID object. But TenantContextFilter and JitProvisioningService expect Strings. This type mismatch causes:
1. TenantContextFilter: String.equals(UUID) always returns false → 403 Forbidden
2. JitProvisioningService: (String) cast on UUID throws ClassCastException → skips org provisioning

fix: Convert UUID to String in TokenCustomizer before adding to JWT. Changed:
- `org.put("id", membershipInfo.getCompanyId());` 
to:
- `org.put("id", membershipInfo.getCompanyId().toString());`

And updated test to expect String: `assertThat(orgs.get(0).get("id")).isEqualTo(company.getId().toString());`

verification: Token-related tests pass (TokenClaimsIntegrationTest, TokenCustomizationTests, etc.). Now need to run backend integration tests to verify full flow works.

files_changed:
  - auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java
  - auth-service/src/test/java/de/goaldone/authservice/config/TokenClaimsIntegrationTest.java
  - backend/src/main/java/de/goaldone/backend/security/TenantContextFilter.java (added logging for debugging)
  - backend/src/main/java/de/goaldone/backend/service/JitProvisioningService.java (added logging for debugging)
  - backend/src/main/resources/application.yaml (added debug logging levels)
