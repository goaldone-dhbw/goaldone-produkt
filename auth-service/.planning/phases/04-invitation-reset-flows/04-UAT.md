---
status: testing
phase: 04-invitation-reset-flows
source: 04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md
started: 2026-05-01T19:54:00Z
updated: 2026-05-01T19:54:00Z
---

## Current Test

number: 1
name: Cold Start Smoke Test
expected: |
  Kill any running server. Clear ephemeral state (temp DBs, caches). Start the application from scratch. Server boots without errors, seed/migration completes, and a basic health check or homepage load returns live data.
awaiting: user response

## Tests

### 1. Cold Start Smoke Test
expected: Server boots without errors, migrations apply cleanly, and basic health check/homepage responds
result: [pending]

### 2. Invitation Landing Page Loads with Valid Token
expected: Navigating to /invitation?token={valid_token} displays the invitation landing page without errors
result: [pending]

### 3. Logged-Out Existing User Prompted to Log In
expected: When a logged-out user with an existing account accesses an invitation token, they see a message prompting them to log in with a link to the login page
result: [pending]

### 4. New User Accesses Password-Setting Form
expected: When a new user accesses an invitation token, they are redirected to /invitation/set-password with a password form showing their email
result: [pending]

### 5. New User Can Set Password and Activate Account
expected: Filling in password and confirm password fields, then submitting creates the user account (transitions from INVITED to ACTIVE) and redirects to login page
result: [pending]

### 6. Authenticated User Can Accept Invitation
expected: When logged-in user clicks "Accept" on invitation landing page, they are immediately added as a member and redirected to home page
result: [pending]

### 7. Forgot Password Form Displays
expected: GET /forgot-password shows an email input field with label and submit button
result: [pending]

### 8. Password Reset Email Sent
expected: Submitting a valid email on forgot password form triggers an email being sent with a password reset link (visible in console for local profile)
result: [pending]

### 9. Enumeration Protection on Forgot Password
expected: Submitting a non-existent email on forgot password form shows the same success message as a valid email (no user enumeration)
result: [pending]

### 10. Password Reset Form Displays with Valid Token
expected: Accessing /reset-password?token={valid_token} shows password and confirm password input fields
result: [pending]

### 11. User Can Reset Password
expected: Filling password fields on reset form and submitting updates the user password and redirects to login page
result: [pending]

### 12. Invalid Token on Reset Page Redirects
expected: Accessing /reset-password with an invalid or expired token redirects to forgot password page
result: [pending]

### 13. User is Logged Out After Password Reset
expected: User is forced to re-authenticate after resetting their password (existing sessions invalidated)
result: [pending]

### 14. Email Templates Have Theme Styling
expected: Invitation and password reset emails contain GoaldoneTheme colors (primary #63729c, accent #a85791) and proper HTML structure
result: [pending]

### 15. CSS Theme Variables Defined
expected: The stylesheet contains CSS custom properties for theme colors (--primary, --accent, --surface) and spacing variables
result: [pending]

## Summary

total: 15
passed: 0
issues: 0
pending: 15
skipped: 0

## Gaps

[none yet]
