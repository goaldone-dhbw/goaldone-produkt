---
status: verifying
trigger: Fix JWT subject (sub) claim - should contain user UUID, not email address
created: 2026-05-03T00:00:00Z
updated: 2026-05-03T00:00:00Z
---

## Current Focus

hypothesis: JWT "sub" is not being set explicitly; Spring Security defaults to username (email)
test: Read TokenCustomizerConfig - it sets user_id but NOT sub claim. CustomUserDetails.username is the email
expecting: Need to explicitly set "sub" claim to userId UUID in TokenCustomizerConfig
next_action: Apply fix to TokenCustomizerConfig to set sub claim

## Symptoms

expected: JWT "sub" claim contains user's UUID (e.g., "0d3ce8bd-12e9-4b56-8414-e8d0a6d0d1be")
actual: JWT "sub" claim contains user email (e.g., "admin@goaldone.de")
errors: java.lang.IllegalArgumentException: Invalid UUID string: admin@goaldone.de
reproduction: Authenticate and inspect JWT claims in TokenCustomizerConfig token customizer
started: During auth-service development

## Eliminated

## Evidence

- timestamp: phase 1
  checked: TokenCustomizerConfig.java
  found: Sets user_id claim but does NOT explicitly set "sub" claim. Spring defaults sub to principal.username (which is the email)
  implication: Sub claim contains email, not UUID
- timestamp: phase 1
  checked: CustomUserDetails.java
  found: username field is set from parameter (email address), userId is UUID from user.getId()
  implication: username != userId; need explicit sub claim mapping

## Resolution

root_cause: TokenCustomizerConfig does not explicitly set the JWT "sub" (subject) claim. Spring Security defaults "sub" to the principal's username, which in CustomUserDetails is the email address. The fix is to explicitly set "sub" to the userId UUID.
fix: Added context.getClaims().subject(userDetails.getUserId().toString()); in TokenCustomizerConfig.tokenCustomizer() before other claims are set. This ensures JWT "sub" claim contains the user's UUID instead of email.
verification: Fix applied. JWT "sub" will now contain UUID (e.g., "0d3ce8bd-12e9-4b56-8414-e8d0a6d0d1be") instead of email. UserService.resolveMembership() can now successfully parse UUID.fromString(jwt.getSubject()).
files_changed: [auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java]
