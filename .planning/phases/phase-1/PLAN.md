---
phase: 01-auth-hardening
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - auth-service/src/main/resources/db/changelog/06-auth-server-schema.xml
  - auth-service/src/main/resources/db/changelog/db.changelog-master.xml
  - auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java
  - auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java
  - auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java
  - auth-service/src/main/java/de/goaldone/authservice/config/WebConfig.java
autonomous: true
requirements: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, INFRA-04]

must_haves:
  truths:
    - "Auth-service JWKS endpoint returns a stable Key ID (kid) persisted on the filesystem."
    - "JWT contains 'orgs' array of objects {id, slug, role} and 'super_admin' boolean."
    - "OAuth2 client details are loaded from environment variables and seeded into JDBC repository."
    - "JWKS endpoint has Cache-Control headers."
  artifacts:
    - path: "auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java"
      provides: "JDBC-backed registries and filesystem-based JWK source"
    - path: "auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java"
      provides: "Environment-based client seeding logic"
    - path: "auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java"
      provides: "Option B multi-org claim strategy"
  key_links:
    - from: "auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java"
      to: "/keys/signing-key.pem"
      via: "filesystem I/O"
    - from: "auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java"
      to: "JdbcRegisteredClientRepository"
      via: "save() if missing"
---

<objective>
Harden the Auth-Service by implementing persistent RSA key management on the filesystem, switching to a JDBC-backed client registry seeded from environment variables, and aligning the JWT claim shape with the Multi-Org Option B strategy.

Purpose: Provide a stable, production-ready identity foundation.
Output: Persistent Auth-Service with hardened token contract and configuration.
</objective>

<execution_context>
@$HOME/.gemini/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@1-CONTEXT.md
@.planning/phases/phase-1/VALIDATION.md
@auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java
@auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java
</context>

<tasks>

<task type="auto">
  <name>Task 1: JDBC Registry and Environment-based Client Seeding</name>
  <files>
    auth-service/src/main/resources/db/changelog/06-auth-server-schema.xml,
    auth-service/src/main/resources/db/changelog/db.changelog-master.xml,
    auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java,
    auth-service/src/main/java/de/goaldone/authservice/startup/ClientSeedingRunner.java
  </files>
  <action>
    1. Create Liquibase changelog `06-auth-server-schema.xml` with standard Spring Auth Server tables (`oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`).
    2. Register in `db.changelog-master.xml`.
    3. Update `AuthorizationServerConfig.java`:
       - Replace `InMemoryRegisteredClientRepository` with `JdbcRegisteredClientRepository`.
       - Define `JdbcOAuth2AuthorizationService` and `JdbcOAuth2AuthorizationConsentService` beans.
    4. Create `ClientSeedingRunner.java` (ApplicationRunner) that:
       - Reads `FRONTEND_CLIENT_ID` (default: 'goaldone-web'), `FRONTEND_CLIENT_SECRET`, `FRONTEND_REDIRECT_URIS` (default: 'http://localhost:4200/callback'), `FRONTEND_POST_LOGOUT_REDIRECT_URIS` from environment.
       - Checks if `FRONTEND_CLIENT_ID` exists in `RegisteredClientRepository`.
       - If missing, builds and saves a new `RegisteredClient` with:
         - Scopes: `openid, profile, email, offline_access`
         - Grants: `authorization_code, refresh_token`
         - Methods: `none` for public client (PKCE) or `client_secret_basic` if secret provided.
       - Also ensure 'mgmt-client' is seeded if missing.
  </action>
  <verify>
    <automated>mvn -f auth-service/pom.xml test -Dtest=ClientSeedingIntegrationTest</automated>
  </verify>
  <done>Clients are seeded from ENV into JDBC repository.</done>
</task>

<task type="auto">
  <name>Task 2: Persistent Filesystem RSA Keys and JWKS Caching</name>
  <files>
    auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java,
    auth-service/src/main/java/de/goaldone/authservice/config/WebConfig.java
  </files>
  <action>
    1. Update `jwkSource()` in `AuthorizationServerConfig.java`:
       - Use `APP_KEY_PATH` (default: `/keys/signing-key.pem`) to locate the key.
       - Logic: Check if file exists. If so, load RSA Key. If not, generate new 2048-bit RSA key pair, save to path, and ensure directory exists.
       - Implementation MUST ensure stable `kid` (e.g., store `kid` in a separate `.kid` file or use a stable thumbprint).
    2. Create `WebConfig.java` to add a Filter or Interceptor that adds `Cache-Control: public, max-age=3600` to the `/oauth2/jwks` endpoint.
  </action>
  <verify>
    <automated>mvn -f auth-service/pom.xml test -Dtest=KeyPersistenceIntegrationTest</automated>
  </verify>
  <done>RSA keys persist on filesystem and JWKS is cacheable.</done>
</task>

<task type="auto">
  <name>Task 3: Multi-Org Claim Strategy (Option B)</name>
  <files>
    auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java
  </files>
  <behavior>
    - Token must include 'orgs' claim as list of objects: {id, slug, role}
    - Token must include 'super_admin' boolean claim
    - Standard claims: authorities, user_id, primary_email, emails
  </behavior>
  <action>
    1. Update `TokenCustomizerConfig.java`:
       - Extract memberships from `CustomUserDetails`.
       - Map memberships to List of Maps: `[{id: UUID, slug: String, role: String}]`.
       - Add `orgs` claim to JWT.
       - Add `super_admin` claim (boolean) from `userDetails.getUser().isSuperAdmin()`.
       - Ensure `authorities` is a flat Set of Strings.
  </action>
  <verify>
    <automated>mvn -f auth-service/pom.xml test -Dtest=TokenClaimsIntegrationTest</automated>
  </verify>
  <done>JWT tokens fulfill Multi-Org Option B requirement.</done>
</task>

</tasks>

<verification>
See .planning/phases/phase-1/VALIDATION.md for detailed verification steps.
</verification>

<success_criteria>
- RegisteredClientRepository uses JDBC.
- Frontend client seeded from environment variables.
- RSA keys persisted at APP_KEY_PATH; kid is stable.
- /oauth2/jwks endpoint returns Cache-Control headers.
- JWT contains 'orgs' (array of objects) and 'super_admin' (boolean) claims.
</success_criteria>

<output>
After completion, create `.planning/phases/phase-1/01-01-SUMMARY.md`
</output>
