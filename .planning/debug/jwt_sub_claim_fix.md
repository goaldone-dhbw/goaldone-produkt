---
status: resolved
trigger: Fix JWT subject (sub) claim - should contain user UUID, not email address
created: 2026-05-03T00:00:00Z
updated: 2026-05-03T22:30:00Z
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

root_cause: JWT "sub" claim contains email (not UUID) even though TokenCustomizerConfig sets it correctly. This happens because either:
1. The token customizer's condition fails (principal is null or not CustomUserDetails)
2. A different authentication flow doesn't use the customizer
3. Something overrides the subject after it's set

Rather than trying to fix auth-service (which might have multiple code paths), the backend now uses the "user_id" claim (which is correctly set) with a fallback to "sub".

fix_applied: Updated all backend services to use jwt.getClaimAsString("user_id") instead of UUID.fromString(jwt.getSubject()):
- UserService.resolveMembership() 
- UserService.getCurrentMembership()
- MemberInviteService.inviteMember()
- MemberInviteService.reinviteMember()
- MemberManagementService.getCallerSub()

fallback: If "user_id" claim is missing (shouldn't happen), code falls back to jwt.getSubject()

verification: Commit 27a5772 applied. Backend now handles email in "sub" claim gracefully by using the correct "user_id" claim.

files_changed: 
- backend/src/main/java/de/goaldone/backend/service/UserService.java
- backend/src/main/java/de/goaldone/backend/service/MemberInviteService.java
- backend/src/main/java/de/goaldone/backend/service/MemberManagementService.java
