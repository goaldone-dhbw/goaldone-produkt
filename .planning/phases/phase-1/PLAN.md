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
  - auth-service/src/main/java/de/goaldone/authservice/security/JdbcJWKSource.java
  - auth-service/src/main/resources/db/changelog/07-seed-clients.xml
autonomous: true
requirements: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, INFRA-04]

must_haves:
  truths:
    - "Auth-service JWKS endpoint returns the same Key ID (kid) after a service restart."
    - "JWT issued for the 'frontend' client contains 'authorities', 'user_id', 'primary_email', 'emails', and 'active_org_id' claims."
    - "RegisteredClientRepository uses the database (jdbc) instead of in-memory storage."
  artifacts:
    - path: "auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java"
      provides: "JDBC-backed registry and persistent JWK source"
    - path: "auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java"
      provides: "Hardened claims with multi-org support"
    - path: "auth-service/src/main/resources/db/changelog/06-auth-server-schema.xml"
      provides: "Spring Authorization Server database schema"
  key_links:
    - from: "auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java"
      to: "auth-service/src/main/java/de/goaldone/authservice/security/JdbcJWKSource.java"
      via: "bean injection"
    - from: "auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java"
      to: "JWT Claims"
      via: "context.getClaims()"
---

<objective>
Harden the Auth-Service by moving from in-memory configuration to persistent, production-ready storage for clients, authorizations, and RSA keys. This ensures stable JWT issuance and consistent claims required by the backend and frontend.

Purpose: Establish a reliable foundation for the Zitadel replacement.
Output: Persistent Auth-Service with stabilized token contract.
</objective>

<execution_context>
@$HOME/.gemini/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java
@auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement Persistent Schema and Registry</name>
  <files>
    auth-service/src/main/resources/db/changelog/06-auth-server-schema.xml,
    auth-service/src/main/resources/db/changelog/db.changelog-master.xml,
    auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java
  </files>
  <action>
    1. Create a new Liquibase changelog `06-auth-server-schema.xml` containing the standard Spring Authorization Server tables: `oauth2_registered_client`, `oauth2_authorization`, and `oauth2_authorization_consent`. Include an `rsa_keys` table with columns: `id` (UUID), `key_id` (VARCHAR), `public_key` (TEXT), `private_key` (TEXT), `created_at` (TIMESTAMP).
    2. Register the new changelog in `db.changelog-master.xml`.
    3. Update `AuthorizationServerConfig.java`:
       - Replace `InMemoryRegisteredClientRepository` with `JdbcRegisteredClientRepository`.
       - Define `JdbcOAuth2AuthorizationService` and `JdbcOAuth2AuthorizationConsentService` beans.
       - Ensure these beans use the `JdbcTemplate` or `DataSource`.
  </action>
  <verify>
    <automated>./mvnw -f auth-service/pom.xml compile</automated>
  </verify>
  <done>Database schema created and Spring Auth Server configured to use JDBC repositories.</done>
</task>

<task type="auto">
  <name>Task 2: Implement Persistent RSA Key Management and Client Seeding</name>
  <files>
    auth-service/src/main/java/de/goaldone/authservice/security/JdbcJWKSource.java,
    auth-service/src/main/java/de/goaldone/authservice/config/AuthorizationServerConfig.java,
    auth-service/src/main/resources/db/changelog/07-seed-clients.xml,
    auth-service/src/main/resources/db/changelog/db.changelog-master.xml
  </files>
  <action>
    1. Create `JdbcJWKSource.java` that implements `JWKSource<SecurityContext>`. It should:
       - Attempt to load an RSA key from the `rsa_keys` table.
       - If no key exists, generate a new 2048-bit RSA key pair, store it in the table, and return it.
       - Ensure the `keyID` is stable (stored in DB).
    2. Update `AuthorizationServerConfig.java` to use `JdbcJWKSource` instead of the current in-memory `generateRsaKey()` approach.
    3. Create `07-seed-clients.xml` to register the 'frontend' and 'mgmt-client' clients in `oauth2_registered_client`.
       - Frontend: clientId='frontend', clientSecret='', methods='none' (public client), grants='authorization_code,refresh_token', redirectUris='http://localhost:4200/callback', scopes='openid,profile,email,offline_access'.
       - Mgmt: clientId='mgmt-client', clientSecret='{noop}mgmt-secret', methods='client_secret_basic', grants='client_credentials', scopes='mgmt:admin'.
    4. Register `07-seed-clients.xml` in `db.changelog-master.xml`.
  </action>
  <verify>
    <automated>./mvnw -f auth-service/pom.xml test</automated>
  </verify>
  <done>RSA keys are persisted in the database and the 'frontend' client is registered.</done>
</task>

<task type="auto">
  <name>Task 3: Harden Token Claims and Multi-Org Strategy</name>
  <files>
    auth-service/src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java
  </files>
  <action>
    1. Update `TokenCustomizerConfig.java` to fulfill AUTH-05 and the Multi-Org strategy:
       - Ensure `authorities` claim is a flat set of strings.
       - Ensure `user_id`, `primary_email`, and `emails` are present and correctly typed (per CustomUserDetails).
       - Implement `active_org_id` strategy: If the user has organization memberships, set the first one's ID as `active_org_id` in the JWT claims.
    2. Add null-safety and logging to the customizer to prevent token issuance failures if user details are partially missing.
  </action>
  <verify>
    <automated>./mvnw -f auth-service/pom.xml test</automated>
  </verify>
  <done>JWTs contain all required claims including active_org_id.</done>
</task>

</tasks>

<verification>
1. Restart the auth-service and verify `/oauth2/jwks` returns a consistent `kid`.
2. Use `curl` or a test to request a token for the 'frontend' client and inspect the JWT payload (e.g., via jwt.io) for required claims.
</verification>

<success_criteria>
- RSA key persistence verified across restarts.
- 'frontend' client successfully registered in DB.
- JWT tokens include: authorities, user_id, primary_email, emails, and active_org_id.
- JWKS endpoint responds with the persisted key.
</success_criteria>

<output>
After completion, create `.planning/phases/01-auth-hardening/01-01-SUMMARY.md`
</output>
