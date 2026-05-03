---
status: awaiting_human_verify
trigger: "JWT issuer configuration mismatch - backend expects http://localhost:8080 but auth-service runs at http://localhost:9000"
created: 2026-05-03T21:40:00Z
updated: 2026-05-03T21:43:00Z
symptoms_prefilled: true
---

## Current Focus

hypothesis: CONFIRMED AND FIXED - Backend JWT resource server was configured to validate tokens from issuer `http://localhost:8080` but auth-service runs on port 9000 and includes issuer claim `http://localhost:9000`.

test: Applied fix to backend/src/main/resources/application-local.yaml. Now awaiting user testing to verify the fix works end-to-end.

expecting: After fix, API requests to backend will succeed with 200 responses. No "Unable to resolve Configuration with provided Issuer" errors in logs. JWT validation will pass because issuer claim matches configured issuer-uri.

next_action: USER TESTING - Restart backend and test login flow. Report if token validation errors are resolved.

## Symptoms

expected: Backend accepts JWT tokens from auth-service without issuer validation errors
actual: Backend rejects tokens with "Unable to resolve the Configuration with the provided Issuer of http://localhost:8080"
errors: "Unable to resolve the Configuration with the provided Issuer of \"http://localhost:8080\""
reproduction: (1) Login at auth-service and complete token exchange. (2) Frontend makes API request to backend with Authorization Bearer token. (3) Backend returns 401 Unauthorized with issuer configuration error.
started: After auth-service implementation (replacing Zitadel)

## Eliminated

## Evidence

- timestamp: 2026-05-03T21:40
  checked: Backend application-local.yaml configuration
  found: Line 19 has `issuer-uri: ${OIDC_ISSUER_URI:http://localhost:8080}`. Default issuer URI is hardcoded to port 8080.
  implication: Backend expects tokens to be issued by something on port 8080, not 9000

- timestamp: 2026-05-03T21:40
  checked: Auth-service server port configuration
  found: auth-service/src/main/resources/application.yaml line 43 sets `server.port: 9000`
  implication: Auth-service runs on port 9000 and will include this in token issuer claims

- timestamp: 2026-05-03T21:40
  checked: Frontend OIDC configuration
  found: AuthService.initialize() at line 17 reads issuer from `window.__env['issuerUri']` or defaults to `http://localhost:9000`. This is the correct issuer. Frontend knows auth-service is at port 9000.
  implication: Frontend is configured correctly for port 9000. Backend is not.

- timestamp: 2026-05-03T21:41
  checked: Backend main application.yaml
  found: Line 13 has `issuer-uri: ${OIDC_ISSUER_URI:https://sso.dev.goaldone.de}`. This is the Zitadel default issuer URI.
  implication: Production config expects Zitadel. Local profile (application-local.yaml) overrides with incorrect localhost:8080

- timestamp: 2026-05-03T21:41
  checked: Spring OAuth2 resource server JWT validation
  found: Spring Security's BearerTokenAuthenticationFilter uses issuer-uri to discover OIDC configuration and validate token issuer claim matches. If issuer in JWT != configured issuer-uri, validation fails.
  implication: Mismatch is a hard validation error, not a warning. Tokens will be rejected.

## Resolution

root_cause: application-local.yaml line 19 has incorrect default issuer-uri value. It defaults to `http://localhost:8080` which was likely copied from a prior configuration or Zitadel reference. Auth-service actually runs on port 9000 and will include `http://localhost:9000` as the issuer claim in JWTs. When backend validates tokens, the issuer claim doesn't match the configured issuer-uri, causing validation to fail with "Unable to resolve the Configuration with the provided Issuer of http://localhost:8080".

fix: APPLIED - Updated backend/src/main/resources/application-local.yaml line 19 to use correct issuer URI. Changed from `issuer-uri: ${OIDC_ISSUER_URI:http://localhost:8080}` to `issuer-uri: ${OIDC_ISSUER_URI:http://localhost:9000}` to match the auth-service port.

verification: 
1. Config change applied
2. Ready to test: Restart backend with local profile
3. Login at auth-service
4. Make API request to backend with token
5. Verify no issuer validation errors in logs/console
6. Verify 200 response, not 401

files_changed:
- backend/src/main/resources/application-local.yaml (line 19)
