# Plan 01-01 Summary: Auth-Service Hardening

## 🚀 Overview
Successfully hardened the Auth-Service by implementing persistent key management, JDBC-backed client registry with environment-based seeding, and aligned JWT claims with the Multi-Org Option B strategy.

## ✅ Completed Tasks

### Task 1: JDBC Registry and Environment-based Client Seeding
- Created Liquibase changelog `06-auth-server-schema.xml` with standard Spring Auth Server tables (`oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`).
- Fixed database compatibility issue by using `BYTEA` instead of `BLOB` for binary fields.
- Updated `AuthorizationServerConfig.java` to use `JdbcRegisteredClientRepository`.
- Implemented `ClientSeedingRunner.java` which seeds clients from environment variables:
  - `FRONTEND_CLIENT_ID` (default: `goaldone-web`)
  - `MGMT_CLIENT_ID` (default: `mgmt-client`)
- Verified via `ClientSeedingIntegrationTest`.

### Task 2: Persistent Filesystem RSA Keys and JWKS Caching
- Updated `jwkSource()` in `AuthorizationServerConfig.java` to persist the JWK to `var/auth-service/keys/jwk.json`.
- This ensures stable Key IDs (`kid`) across restarts.
- Implemented `CacheControlFilter` in `WebConfig.java` to add `Cache-Control: public, max-age=3600` to the `/oauth2/jwks` endpoint.
- Verified via `KeyPersistenceIntegrationTest`.

### Task 3: Multi-Org Claim Strategy (Option B)
- Updated `TokenCustomizerConfig.java` to include custom claims in generated JWTs:
  - `user_id`: UUID of the authenticated user.
  - `primary_email`: User's primary email address.
  - `emails`: List of all verified email addresses.
  - `super_admin`: Boolean indicating if the user is a global super admin.
  - `orgs`: List of membership objects `[{id, slug, role}]`.
- Verified via `TokenClaimsIntegrationTest`.

## 🧪 Verification Results
- **ClientSeedingIntegrationTest:** PASSED
- **KeyPersistenceIntegrationTest:** PASSED
- **TokenClaimsIntegrationTest:** PASSED
- **Full Build:** SUCCESSful compilation and execution.

## 🔗 Key Links
- Configuration: `auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java`
- Token Logic: `auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java`
- Database: `auth-service/src/main/resources/db/changelog/06-auth-server-schema.xml`
