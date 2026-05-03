# Requirements: auth-service

## Functional Requirements

### 1. Authentication & Authorization (OAuth 2.1 / OIDC)
- **F1.1:** Support standard OIDC discovery (`.well-known/openid-configuration`).
- **F1.2:** JWKS endpoint for public key distribution.
- **F1.3:** Authorization Code Flow with PKCE.
- **F1.4:** Token Introspection and Revocation.
- **F1.5:** OIDC UserInfo endpoint.
- **F1.6:** Client Credentials Grant for internal Management API access.

### 2. User & Identity Management
- **F2.1:** Support multiple verified email addresses per user.
- **F2.2:** Primary email designation.
- **F2.3:** User statuses: INVITED, ACTIVE, DISABLED.
- **F2.4:** Secure password hashing (BCrypt/Argon2).

### 3. Organization Management
- **F3.1:** Multi-Org support: users can belong to multiple organizations.
- **F3.2:** Roles within organizations: SUPER_ADMIN, COMPANY_ADMIN, USER.
- **F3.3:** Domain-based self-registration (automatic org assignment based on email domain).

### 4. Invitation Flow
- **F4.1:** Generate secure, time-limited invitation tokens.
- **F4.2:** Landing page for invitations.
- **F4.3:** Support setting password for new users.
- **F4.4:** Support linking existing accounts to a new organization via invitation.

### 5. Password Reset
- **F5.1:** Request password reset via email.
- **F5.2:** Secure, single-use, time-limited reset tokens.
- **F5.3:** Prevention of user enumeration (constant response time/message).
- **F5.4:** Invalidation of all active sessions after password reset.

### 6. Account Linking (F190)
- **F6.1:** Independent account linking for logged-in users.
- **F6.2:** OIDC-PKCE flow for verifying the identity of the secondary account.

### 7. Resource Server & Access Control
- **F7.1:** JWT validation via JWKS.
- **F7.2:** Custom JWT claims: `emails`, `super_admin`, `orgs` (id, slug, role).
- **F7.3:** Last-Admin and Last-Super-Admin checks for deletion/role changes.
- **F7.4:** Restriction: Super-Admins (without company membership) must not access business tasks (Tasks/Schedule/Breaks).

## Non-Functional Requirements
- **NF1:** Security: OAuth 2.1 compliant, PKCE mandatory.
- **NF2:** Scalability: Support for multiple organizations and high user volume.
- **NF3:** Maintainability: Clean architecture, comprehensive test coverage.
- **NF4:** Reliability: Secure token management, persistent JWKS.
