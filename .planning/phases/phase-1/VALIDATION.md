# Phase 1: Auth-Service Hardening - Validation Plan

This document defines the automated and manual verification steps required to ensure the `auth-service` is hardened according to the Phase 1 specifications.

## 1. Automated Verification (CI/CD)

The following tests must pass in the `auth-service` module:

### Key Persistence Tests
- **Test ID:** `AUTH-VAL-01`
- **Goal:** Verify RSA keys are persisted to the filesystem and reused on restart.
- **Method:** 
  1. Start application with a temporary `APP_KEY_PATH`.
  2. Capture the `kid` from `/oauth2/jwks`.
  3. Shutdown application.
  4. Restart application with same `APP_KEY_PATH`.
  5. Verify `kid` remains identical.
- **Command:** `mvn test -Dtest=KeyPersistenceIntegrationTest`

### Client Seeding Tests
- **Test ID:** `AUTH-VAL-02`
- **Goal:** Verify OAuth2 clients are seeded from environment variables if not present in DB.
- **Method:**
  1. Start application with `FRONTEND_CLIENT_ID=val-test`.
  2. Verify `val-test` exists in `JdbcRegisteredClientRepository`.
- **Command:** `mvn test -Dtest=ClientSeedingIntegrationTest`

### Multi-Org Claim Shape Tests
- **Test ID:** `AUTH-VAL-03`
- **Goal:** Verify JWT contains the `orgs` array of objects and `super_admin` boolean.
- **Method:**
  1. Authenticate a user with multiple memberships.
  2. Decode the issued JWT.
  3. Assert `orgs` is present as `[{id, slug, role}]`.
  4. Assert `super_admin` is present as boolean.
- **Command:** `mvn test -Dtest=TokenClaimsIntegrationTest`

## 2. Manual Verification (UAT)

### JWKS Cacheability
- **Step 1:** `curl -v http://localhost:8081/oauth2/jwks`
- **Check:** Ensure `Cache-Control` header is present and allows caching (e.g., `max-age=3600`).

### Persistent Key across Docker Restart
- **Step 1:** Start the stack: `docker compose up -d auth-service`
- **Step 2:** Get `kid`: `curl -s http://localhost:8081/oauth2/jwks | jq -r '.keys[0].kid'`
- **Step 3:** Restart container: `docker compose restart auth-service`
- **Step 4:** Verify `kid` is the same.

## 3. Success Criteria Checklist
- [ ] `JdbcRegisteredClientRepository` is used.
- [ ] No hardcoded client secrets or URIs in source code for frontend.
- [ ] `orgs` claim matches Option B strategy from `1-CONTEXT.md`.
- [ ] Keys stored at `APP_KEY_PATH`.
- [ ] `kid` is stable across restarts.
- [ ] `/oauth2/jwks` is cacheable.
