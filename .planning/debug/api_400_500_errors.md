---
status: awaiting_human_verify
trigger: "API calls to /users/accounts return 400 then 500 errors despite valid JWT token"
created: 2026-05-03T21:45:00Z
updated: 2026-05-03T21:50:00Z
symptoms_prefilled: true
goal: find_and_fix
---

## Current Focus

status: Fixed and tested
hypothesis: CONFIRMED - User has no organization memberships in auth-service. Frontend sends empty string as X-Org-ID header. Spring's @RequestHeader UUID conversion fails with 400.
test: Fix applied - made X-Org-ID optional in controller and handle null in service. All tests pass.
expecting: Frontend can now call getMyAccounts with no/empty X-Org-ID header and get 200 with empty accounts list
next_action: Need user to verify in browser - should no longer see 400/500 errors when logging in

## Symptoms

expected: Valid JWT token allows frontend to call /users/accounts and receive user account data
actual: GET /users/accounts returns 400 Bad Request, then 500 Internal Server Error on retry
errors: "400 Bad Request" and "500 Internal Server Error"
reproduction: Login to frontend successfully, wait for app to load, observe API errors in console
started: Current session (newly migrated from Zitadel to auth-service)

## Eliminated

## Evidence

- timestamp: 2026-05-03T21:45:00Z
  checked: User report
  found: Frontend login works, JWT token is valid (tokenLength: 1206), but API calls fail
  implication: JWT is being generated correctly by auth-service, but backend rejects /users/accounts

- timestamp: 2026-05-03T21:50:00Z
  checked: Code path for X-Org-ID header
  found: Frontend service correctly sets X-Org-ID header from tenantService.getActiveOrgId(). Auth-service TokenCustomizer adds "orgs" claim to JWT with company IDs (UUIDs). TenantService.getActiveOrgId() returns first org or stored org from JWT.
  implication: Header should be valid if user has memberships. Issue likely: user has NO memberships (empty orgs array) → getActiveOrgId() returns null → header becomes empty string → validation fails with 400

- timestamp: 2026-05-03T21:55:00Z
  checked: Frontend sidebar component (app-sidebar.component.ts line 25)
  found: getMyAccounts() is called with orgContextService.getDefaultOrg()?.id || '' — if user has no orgs, empty string is sent. Backend controller expects @RequestHeader("X-Org-ID") UUID xOrgID which Spring tries to parse. Empty string cannot be parsed as UUID → 400 Bad Request (invalid request format).
  implication: Root cause identified. User's JWT has empty orgs array because auth-service hasn't created any memberships for this user.

## Resolution

root_cause: User has no organization memberships in auth-service. JWT contains empty orgs claim. Frontend passes empty X-Org-ID header. Spring's @RequestHeader("X-Org-ID") UUID binding fails with 400 Bad Request when header is empty/missing. Then 500 occurs on retry due to incomplete error handling.

fix: 
1. Made X-Org-ID header optional in UserAccountsController.getMyAccounts() using @RequestHeader(value = "X-Org-ID", required = false)
2. Updated UserService.buildAccountListResponse() to handle null xOrgID gracefully by returning empty account list

verification: 
files_changed:
  - backend/src/main/java/de/goaldone/backend/controller/UserAccountsController.java
  - backend/src/main/java/de/goaldone/backend/service/UserService.java
