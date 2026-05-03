---
status: fixing
trigger: "Auth redirect loop between /callback and startpage after login with new auth-service"
created: 2026-05-03T00:00:00Z
updated: 2026-05-03T00:00:00Z
---

hypothesis: One of two issues: (1) The token exchange POST request is not being made because of PKCE validation failure or callback URL mismatch. (2) The token exchange succeeds but the 'token_received' event is not fired, so the callback component times out after 3 seconds and redirects to startpage.
test: Add logging to frontend to see if loadDiscoveryDocumentAndTryLogin() is being called, and if 'token_received' event is fired. Check browser Network tab to confirm POST /oauth2/token is being called.
expecting: (1) Authorization code should be detected by loadDiscoveryDocumentAndTryLogin(). (2) POST /oauth2/token should be called with correct code_verifier. (3) Token response should be parsed and stored. (4) 'token_received' event should fire. (5) hasValidAccessToken() should return true.
next_action: Add debugging console.log statements to auth.service and callback-page.component to trace the exact flow and identify where it's failing.

## Symptoms

expected: User logs in → redirects to /callback → stays on startpage with "get to app" button visible
actual: Browser stuck in redirect loop between /callback and startpage
errors: No visible errors in browser console or terminal (silent failure)
reproduction: Standard login flow - login page → enter credentials → click login
started: After auth-service custom implementation (replacing Zitadel)

## Root Cause Analysis

The callback flow has a **timing/race condition issue**:

1. User logs in → redirected to /callback?code=...&state=...
2. Browser navigates to new URL, which triggers a full page load (or at least route change)
3. APP_INITIALIZER runs and calls `loadDiscoveryDocumentAndTryLogin()`
4. This method detects the auth code and exchanges it for a token (async)
5. While this is happening, Angular finishes bootstrap and routes to /callback
6. CallbackPageComponent.ngOnInit() runs
7. Checks `hasValidAccessToken()` - token exchange might still be in progress!

If the token exchange hasn't completed by step 6, the callback component won't have a token yet. It then subscribes to OAuthService events waiting for 'token_received'. But there might be a timing issue where:
- The token exchange completes, but the event isn't fired yet
- Or the event is fired before the subscription is set up
- Or there's a mismatch between when the token is stored and when it's validated

The code at line 28 `window.history.replaceState()` removes the URL parameters, which is correct - but it happens synchronously, so the token exchange might still be async.

## Next Steps for User

**The issue is diagnosed but root cause needs verification. Follow these steps:**

1. Clear browser cache and localStorage: Right-click on page → Inspect → Application → Clear storage
2. Open DevTools Console tab
3. Go to http://localhost:4200
4. Click "get to app" button to start login
5. Complete the login form
6. Watch the console logs appear in real-time
7. Report what you see in the console logs, especially:
   - Does [AuthService.initialize] logging appear?
   - Does [AuthService.OAuthEvents] show any token_received or code_error events?
   - Does [CallbackPage] logging show the token check results?
   - Are there any JavaScript errors in the console?

**Expected log sequence:**
```
[AuthService.initialize] Starting auth initialization...
[AuthService.initialize] loadDiscoveryDocumentAndTryLogin...
[AuthService.OAuthEvents] discovery_document_loaded
[AuthService.OAuthEvents] token_received
[CallbackPage] Component initialized
[CallbackPage] hasValidAccessToken(): true
[CallbackPage] Token valid, navigating to /app
```

**If you see a timeout instead:**
```
[CallbackPage] 3-second timeout check. Token valid? false
```

This means the 'token_received' event was never fired, and the code exchange failed silently.

## Eliminated

## Actions Taken

- Added extensive logging to auth.service.ts initialize() method to trace discovery document loading and token exchange
- Added comprehensive logging to callback-page.component.ts to trace token validation and event subscriptions
- Added a global OAuthService event listener to log ALL events from angular-oauth2-oidc

## Evidence

- timestamp: 2026-05-03T00:01
  checked: Callback component flow
  found: Callback component (line 31) checks hasValidAccessToken() immediately in ngOnInit. If true, redirects to /app after 500ms. If false, waits for oauth events or times out after 3s.
  implication: Token should be in storage after oauth-service's loadDiscoveryDocumentAndTryLogin() completes in app.config.ts

- timestamp: 2026-05-03T00:02
  checked: AuthService initialize() in app.config APP_INITIALIZER
  found: APP_INITIALIZER calls auth.initialize() which calls oauthService.loadDiscoveryDocumentAndTryLogin(). This attempts code exchange if callback URL has auth code. Token should be stored BEFORE callback component loads.
  implication: If token isn't being stored, the issue is in the code exchange response or token validation

- timestamp: 2026-05-03T00:03
  checked: Token customizer configuration
  found: TokenCustomizerConfig.tokenCustomizer() adds claims to JWT: authorities, emails, user_id, super_admin, orgs. Uses CustomUserDetails for org/membership info.
  implication: Token structure should include 'orgs' claim with org objects containing id, slug, role fields

- timestamp: 2026-05-03T00:04
  checked: Discovery document and token endpoint configuration
  found: AuthorizationServerConfig uses Spring OAuth2AuthorizationServerConfigurer with OIDC enabled (line 66). Standard OAuth2 token endpoint should be at /oauth2/token. JWKS should be at /.well-known/oauth-authorization-server or /oauth2/jwks
  implication: angular-oauth2-oidc library should discover these endpoints; need to verify actual response format

- timestamp: 2026-05-03T00:05
  checked: Discovery document endpoint
  found: Discovery document is working correctly at /.well-known/oauth-authorization-server. Token endpoint: http://localhost:9000/oauth2/token. JWKS URI: http://localhost:9000/oauth2/jwks
  implication: Discovery is correct, client can find the token endpoint

- timestamp: 2026-05-03T00:06
  checked: Token endpoint response to invalid auth code
  found: POST to /oauth2/token with invalid code returns proper OAuth2 error {"error":"invalid_grant"}. Not a 3xx redirect, proper JSON response.
  implication: Token endpoint IS working! The issue is likely authentication flow-related, not endpoint availability

- timestamp: 2026-05-03T00:07
  checked: Auth-service database configuration
  found: Local profile uses H2 in-memory (jdbc:h2:mem:authdb). Liquibase enabled with changelogs. Session store is JDBC with initialize-schema: always. RegisteredClientRepository is JDBC-based (looks for clients in oauth2_registered_client table).
  implication: Clients must be seeded into H2 by ClientSeedingRunner or they won't be found

- timestamp: 2026-05-03T00:08
  checked: Full OAuth2 flow test (end-to-end)
  found: CRITICAL DISCOVERY: Performed full auth flow manually. (1) Authorization endpoint returns auth code successfully. (2) Token endpoint exchanges code for token and returns 200 OK with valid JWT access token AND id_token with all expected claims (sub, orgs, authorities, user_id, super_admin, emails, primary_email).
  implication: **Backend auth-service is working perfectly.** The issue is NOT in the auth-service. The issue must be in the FRONTEND or in how angular-oauth2-oidc is handling the token response.

- timestamp: 2026-05-03T00:09
  checked: Frontend token storage and validation
  found: CallbackPageComponent checks hasValidAccessToken() immediately on init. If true, navigates to /app. If false, waits for OAuthService events. AuthService.hasValidAccessToken() delegates to OAuthService.hasValidAccessToken().
  implication: The question is: Is OAuthService actually storing the token after the exchange? Is angular-oauth2-oidc.loadDiscoveryDocumentAndTryLogin() actually processing the callback response and extracting the token?

## Resolution

root_cause: **Two-part issue**: (1) Backend auth-service is working correctly (verified end-to-end). (2) Frontend redirect loop caused by CallbackPageComponent assuming the token has already been exchanged when the component loads, but this assumption is wrong when APP_INITIALIZER runs BEFORE the user logs in (so no auth code is detected). When the user is then redirected to /callback after login, APP_INITIALIZER doesn't run again (only on initial page load). The component waits for 'token_received' event, but it never fires because `loadDiscoveryDocumentAndTryLogin()` was already called when the app booted (before any auth code existed). The timeout triggers after 3 seconds, redirecting to /. User clicks "Login" again, creating the loop.

fix: IMPLEMENTED: (1) Added extensive console logging to trace the exact flow. (2) Modified CallbackPageComponent.ngOnInit() to manually call `oauthService.tryLoginCodeFlow()` if an authorization code is detected in the URL. This ensures the code exchange happens regardless of when loadDiscoveryDocumentAndTryLogin() was called. (3) Added 500ms delay before checking token validity to allow async operations to complete.

verification: Test with enhanced logging:
1. Clear browser cache/localStorage
2. Open DevTools Console
3. Login flow should now complete successfully
4. Check console for:
   - `[AuthService.OAuthEvents] token_received` event firing
   - `[CallbackPage] Token is valid, scheduling redirect to /app` message
   - No timeout errors
5. Browser should redirect to /app without looping to /callback

files_changed: 
- frontend/src/app/core/auth/auth.service.ts (added comprehensive logging)
- frontend/src/app/features/callback/callback-page.component.ts (added manual tryLoginCodeFlow() call and event logging)
