---
status: awaiting_human_verify
trigger: "Startpage doesn't show correct button based on auth state - always shows Login instead of Zur App Gehen after successful login"
created: 2026-05-03T00:15:00Z
updated: 2026-05-03T21:35:00Z
---

## Current Focus
hypothesis: Token is NOT being properly stored in browser localStorage after the callback component calls `tryLoginCodeFlow()`. This causes `hasValidAccessToken()` to return false, showing "Login" button.
test: Added detailed logging to auth.service.ts and callback-page.component.ts to track localStorage keys and token retrieval
expecting: Console logs will show if token is being stored and when startpage checks, whether token can be retrieved
next_action: USER TESTING - Follow steps to trigger login flow and report console output

## Symptoms

expected: After successful login and redirect to startpage, button should show "Zur App Gehen"
actual: Startpage always shows "Login" button even after user logs in
errors: None visible in console
reproduction: Login successfully → redirected to /callback → then to / (startpage) → button still shows "Login"
started: After auth-service custom implementation and redirect loop fix

## Eliminated

- hypothesis: Property binding is only evaluated once at init time
  evidence: Angular property bindings DO call getters on every change detection cycle, not just at init time
  timestamp: 2026-05-03T00:20
  why_eliminated: The getter should be re-evaluated whenever Angular runs change detection

## Evidence

- timestamp: 2026-05-03T00:15
  checked: StartPageComponent button logic
  found: Component has `get isLoggedIn()` getter that returns `this.authService.hasValidAccessToken()`. Template uses this in property binding: `[label]="isLoggedIn ? 'Zur App' : 'Login'"`.
  implication: Angular property bindings call getters on every change detection cycle. So if the getter returns false, it means `hasValidAccessToken()` is returning false.

- timestamp: 2026-05-03T00:16
  checked: CallbackPageComponent redirect behavior
  found: After successful token exchange, CallbackPageComponent calls `this.router.navigateByUrl('/app')` (line 58, 78). So the user should go /callback → /app, NOT back to / (startpage).
  implication: But the problem statement says user is seeing "Login" button on startpage after login. This means user is somehow on the startpage, not /app. Either: (1) User manually navigates back to /, OR (2) There's a different issue.

- timestamp: 2026-05-03T00:17
  checked: Complete user flow
  found: (1) User clicks "Login" on startpage → calls `initLoginFlow()` which is OAuthService.initLoginFlow() (redirects to auth-service /authorize endpoint). (2) User logs in at auth-service → redirected to /callback?code=...&state=.... (3) CallbackPageComponent processes token exchange → navigates to /app. Expected flow is clear. But the problem says user sees "Login" button on startpage after login.
  implication: Hypothesis: User is somehow navigating back to / (startpage) after successful login, or callback component redirect fails. The startpage component's `isLoggedIn` getter is called at init time. If component is destroyed and recreated (e.g., user navigates to / after login), the getter would be called again.

- timestamp: 2026-05-03T00:18
  checked: StartPageComponent change detection
  found: Component is standalone, no explicit change detection strategy set, uses default `OnPush` is not set so uses `Default` (checks component tree on every zone.run). Template uses simple property binding `[label]="isLoggedIn ? ..."`. When getter is evaluated, it calls `hasValidAccessToken()` which delegates to OAuthService.
  implication: The getter SHOULD be re-evaluated on any change detection cycle. If user navigates back to /, a new StartPageComponent instance should be created, and the getter should be called fresh.

- timestamp: 2026-05-03T00:19
  checked: How OAuthService.hasValidAccessToken() works
  found: This is from angular-oauth2-oidc library. It checks if there's a valid access token stored in browser storage (localStorage by default).
  implication: If the token is properly stored by the callback component, subsequent calls to hasValidAccessToken() should return true.

- timestamp: 2026-05-03T21:35
  checked: Added instrumentation
  found: (1) AuthService.hasValidAccessToken() now logs token existence and length. (2) AuthService.initialize() logs localStorage keys and contents after loadDiscoveryDocumentAndTryLogin(). (3) CallbackPageComponent logs localStorage after manual tryLoginCodeFlow(). (4) StartPageComponent logs when getter is called.
  implication: Test should reveal exactly when/where token is being stored and why getter returns false.

## Test Instructions for User

**Objective:** Determine why `hasValidAccessToken()` returns false after successful login

**Prerequisites:**
- Clear all browser storage: Right-click on page → Inspect → Application tab → Clear site data
- Open DevTools: Press F12 or Cmd+Shift+I
- Go to Console tab
- Navigate to http://localhost:4200

**Steps:**
1. On startpage, click "Login" button
2. You'll be redirected to auth-service login page (localhost:9000)
3. Complete the login with your test credentials
4. After clicking login, you'll be redirected to /callback
5. Watch the console for logs

**What to Report:**

Please copy-paste the ENTIRE console output (all messages starting with `[AuthService`, `[CallbackPage`, `[StartPage]`), specifically looking for:

1. When localStorage keys are first logged after initialization
2. When token exchange completes (look for "Manual tryLoginCodeFlow completed")
3. What tokens are stored after exchange (access_token, id_token, refresh_token)
4. When startpage component checks isLoggedIn and what it finds

**Expected vs Actual:**

*Expected flow:*
```
[CallbackPage] Authorization code detected in URL, manually triggering tryLogin...
[CallbackPage] Manual tryLoginCodeFlow completed
[CallbackPage] After tryLoginCodeFlow - checking storage:
[CallbackPage] access_token: eyJ...xxxxx (long JWT token)
[CallbackPage] id_token: eyJ...xxxxx (long JWT token)
[CallbackPage] refreshToken: xxxxx (refresh token)
[CallbackPage] hasValidAccessToken() after initial wait: true
[CallbackPage] Token is valid, scheduling redirect to /app
[CallbackPage] Token valid, navigating to /app
```

*If you see timeouts or missing tokens:*
```
[CallbackPage] 3-second timeout check. Token valid? false Has error? false
```

