# Stack Research

**Domain:** Spring Security integration with custom OAuth2 Authorization Server (Zitadel migration)
**Researched:** 2026-05-02
**Confidence:** HIGH

## Context: What Exists and What Changes

The backend is already a Spring Security OAuth2 resource server validating Zitadel JWTs. The migration
replaces the issuer (Zitadel → auth-service) and the JWT claim structure. Almost nothing in the
Spring Security resource server wiring changes — the issuer-uri property and JwtAuthenticationConverter
claim name are the two concrete changes required. The auth-service already exposes a JWKS endpoint.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Spring Boot | 4.0.6 | Application framework for both backend and auth-service | Already in use; auth-service pom.xml uses 4.0.6, backend uses 4.0.5 — align to 4.0.6 |
| Spring Security | 7.0.x (managed by Boot 4) | JWT resource server + authorization server | Spring Authorization Server merged into Spring Security 7.0 — single dependency, single release cycle |
| spring-security-oauth2-authorization-server | 7.0.x (via spring-security.version) | Auth-service OAuth2 authorization server logic | Already declared in auth-service pom.xml as `org.springframework.security:spring-security-oauth2-authorization-server` |
| spring-boot-starter-security-oauth2-resource-server | managed by Boot 4 | Backend JWT validation | Already declared in backend pom.xml; no version change needed |
| Nimbus JOSE + JWT | managed transitively | JWT decode/encode, JWKS fetch | Included transitively by Spring Security; do not declare explicitly |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-security-test | managed by Boot 4 | MockMvc JWT testing | All integration tests that previously used Zitadel claims must be updated to use auth-service claim structure |
| WireMock (wiremock-standalone) | 3.12.0 | Stub JWKS endpoint in tests | Use in integration tests where real auth-service is not running; already in backend pom.xml |
| spring-boot-starter-oauth2-authorization-server | managed by Boot | Simplified auth-service starter | Can replace the manual spring-security-oauth2-authorization-server declaration in auth-service pom.xml for Spring Boot projects |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| AuthorizationServerSettings (Spring) | Configure auth-service protocol endpoints | Default JWKS endpoint is `/oauth2/jwks`; OIDC discovery is `/.well-known/openid-configuration` |
| spring.security.oauth2.resourceserver.jwt.issuer-uri | Backend YAML config | Change value from Zitadel domain to auth-service URL; enables automatic JWKS discovery |
| spring.security.oauth2.resourceserver.jwt.jwk-set-uri | Backend YAML config (optional) | Set explicitly to skip provider discovery at startup; improves startup independence |

## Installation

No new Maven dependencies required for the backend. Only configuration changes and code changes.

For the backend `pom.xml`, verify that the Zitadel SDK dependency is removed:

```xml
<!-- REMOVE: no longer needed after migration -->
<dependency>
    <groupId>io.github.zitadel</groupId>
    <artifactId>client</artifactId>
    <version>4.1.2</version>
</dependency>
```

The auth-service already has the correct dependencies. No additions needed there.

## Specific Configuration Prescriptions

### 1. Backend: application.yaml — issuer-uri change

Replace the Zitadel issuer with the auth-service issuer. Two options:

**Option A — issuer-uri only (OIDC discovery, recommended for production):**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_SERVICE_ISSUER_URI:http://localhost:9000}
```
Spring Security fetches `<issuer>/.well-known/openid-configuration` on first JWT validation
request and resolves the `jwks_uri` automatically. Startup is not coupled to auth-service availability.
The `iss` claim in every JWT is validated against this value.

**Option B — jwk-set-uri + issuer-uri (explicit, recommended for local dev robustness):**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_SERVICE_ISSUER_URI:http://localhost:9000}
          jwk-set-uri: ${AUTH_SERVICE_JWKS_URI:http://localhost:9000/oauth2/jwks}
```
When `jwk-set-uri` is set, Spring skips provider discovery and goes directly to the JWKS endpoint.
The `iss` claim is still validated against `issuer-uri`. This is more predictable for local dev
where discovery may fail due to redirect handling.

The auth-service JWKS endpoint is `/oauth2/jwks` by default (Spring Authorization Server default,
confirmed in `AuthorizationServerSettings.builder().build()`).

### 2. Backend: SecurityConfig.java — JwtAuthenticationConverter rewrite

The existing converter reads Zitadel's `urn:zitadel:iam:org:project:roles` nested map structure.
The auth-service emits a flat `authorities` claim (a Set<String>). Replace the entire converter body.

**Current (Zitadel-specific, remove this):**
```java
jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
    Map<String, Object> rolesClaim = jwt.getClaimAsMap("urn:zitadel:iam:org:project:roles");
    if (rolesClaim == null) return List.of();
    return rolesClaim.keySet().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());
});
```

**New (auth-service claim structure):**
Use `JwtGrantedAuthoritiesConverter` — it handles the `authorities` claim natively:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthoritiesClaimName("authorities"); // matches TokenCustomizerConfig
    authoritiesConverter.setAuthorityPrefix("");                 // auth-service already prefixes: ROLE_USER, ORG_{id}_ADMIN

    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return jwtConverter;
}
```

The auth-service `TokenCustomizerConfig` emits authorities in the form: `ROLE_USER`,
`ROLE_SUPER_ADMIN`, `ORG_{companyId}_{roleName}`. No additional prefix should be added by the
converter — hence `setAuthorityPrefix("")`.

The `JwtGrantedAuthoritiesConverter` handles both `List<String>` and `Collection<String>` claim
values automatically, which matches the `Set<String>` the auth-service emits.

### 3. Backend: JitProvisioningService — rewrite required

The current implementation reads Zitadel-specific JWT claims:
- `urn:zitadel:iam:user:resourceowner:id` (Zitadel org ID)
- `urn:zitadel:iam:user:resourceowner:name` (org name)

And uses Zitadel-specific DB fields: `zitadelSub`, `zitadelOrgId`.

Post-migration, the JIT provisioning service must read from auth-service JWT claims:
- `sub` — user's UUID (already the subject)
- `user_id` — UUID string (auth-service `TokenCustomizerConfig` emits this)
- `primary_email` — emitted by auth-service

The JIT provisioning model itself changes: in the new model, organizations are managed in auth-service,
not provisioned from JWT claims. The backend's JIT provisioning should only upsert a local
`UserAccountEntity` referencing the auth-service user ID, not create organizations from JWT data.
Organization membership is read from `authorities` claims (e.g., `ORG_{id}_ADMIN`).

### 4. Testing: Integration tests — JWT claim structure change

Tests using `MockMvc.perform(get(...).with(jwt()))` must be updated to match the auth-service
claim structure. Use `.authorities(...)` explicitly because the custom `JwtGrantedAuthoritiesConverter`
is not auto-applied in `@WebMvcTest` slices (see project memory: "MockMvc jwt() needs explicit
.authorities(...) — custom JwtAuthenticationConverter is not auto-applied"):

```java
mockMvc.perform(get("/api/v1/orgs/{id}/members", orgId)
    .with(jwt()
        .jwt(j -> j
            .subject("550e8400-e29b-41d4-a716-446655440000")
            .claim("user_id", "550e8400-e29b-41d4-a716-446655440000")
            .claim("primary_email", "user@example.com")
            .claim("authorities", List.of("ROLE_USER", "ORG_" + orgId + "_ADMIN"))
        )
        .authorities(
            new SimpleGrantedAuthority("ROLE_USER"),
            new SimpleGrantedAuthority("ORG_" + orgId + "_ADMIN")
        )
    )
)
```

### 5. Auth-service: RegisteredClientRepository — must be moved to database

The current `AuthorizationServerConfig.registeredClientRepository()` uses `InMemoryRegisteredClientRepository`
with a hardcoded `test-client`. For production:
- Use `JdbcRegisteredClientRepository` backed by the Spring Authorization Server schema
- The frontend Angular client must be registered with correct redirect URIs and scopes
- Client secret must be properly encoded (BCrypt, not `{noop}`)

This is a production-readiness concern, not a backend-resource-server concern, but it blocks
frontend OAuth2 flow working end-to-end.

### 6. Auth-service: RSA key pair — must be externalized

The current `jwkSource()` bean calls `generateRsaKey()` on every startup, producing a new key pair.
This means all previously issued JWTs become invalid on auth-service restart.

For production and even local dev stability:
```yaml
# Load from environment or file instead of generating
auth:
  rsa-private-key: classpath:keys/private.pem  # or ${RSA_PRIVATE_KEY_PATH}
  rsa-public-key: classpath:keys/public.pem
```

```java
@Bean
public JWKSource<SecurityContext> jwkSource(
        @Value("${auth.rsa-private-key}") RSAPrivateKey privateKey,
        @Value("${auth.rsa-public-key}") RSAPublicKey publicKey) {
    RSAKey rsaKey = new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID("goaldone-key-1")  // stable key ID
            .build();
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
}
```

For local dev, generate once with `keytool` or `openssl` and commit the dev keypair (not for prod).

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| `JwtGrantedAuthoritiesConverter` with `setAuthoritiesClaimName("authorities")` | Custom lambda converter (existing pattern) | Only if the authorities claim has a non-standard structure (e.g., nested objects). Auth-service emits a flat list — JwtGrantedAuthoritiesConverter handles this natively |
| `issuer-uri` pointing to auth-service | Hardcoded `jwk-set-uri` only without `issuer-uri` | Use `jwk-set-uri`-only when `iss` claim validation is intentionally skipped (not recommended; validation prevents token substitution attacks) |
| `JdbcRegisteredClientRepository` | `InMemoryRegisteredClientRepository` | In-memory is acceptable for local dev/testing only; never use in production |
| Externalized RSA keypair via PEM files | PKCS12 keystore | Both work; PEM files are simpler to manage in container environments with secrets injection |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Hardcoded `jwk-set-uri` without `issuer-uri` | Bypasses `iss` claim validation; tokens from any JWKS-compatible issuer would be accepted | Always set `issuer-uri`; optionally set `jwk-set-uri` in addition |
| `InMemoryRegisteredClientRepository` in production | Loses all registered clients on restart; no persistence | `JdbcRegisteredClientRepository` with Spring Authorization Server schema tables |
| Regenerating RSA key pair on every startup | Every restart invalidates all issued JWTs; users get logged out | Externalize keypair to PEM files or a secret manager |
| Reading Zitadel claims (`urn:zitadel:iam:*`) after migration | These claims no longer exist in auth-service JWTs; code will silently return null | Read `sub`, `user_id`, `authorities`, `primary_email` claims from auth-service JWT |
| Zitadel Java SDK (`io.github.zitadel:client`) after migration | Dead dependency once Zitadel is removed; adds classpath bloat and potential security surface | Remove from backend pom.xml; any user management calls now go to auth-service REST API |
| `{noop}` client secrets in auth-service | Plaintext secrets in production; violates security baseline | BCrypt-encoded secrets: `{bcrypt}$2a$10$...` |

## JWKS Caching Behavior (Production Note)

Spring Security's `NimbusJwtDecoder` caches the JWKS response in memory. Default behavior:
- Fetches JWKS on first JWT validation request (not at startup when using `issuer-uri`)
- Caches the JWK set; re-fetches on cache miss or when a `kid` (key ID) is not found in cache
- No time-based expiry by default — cache refresh is triggered by unknown `kid` in incoming JWT

For production with key rotation planned:
- Ensure the auth-service RSA key has a stable `keyID` (e.g., `"goaldone-key-1"`)
- For key rotation: publish new key alongside old key in JWKS before switching signing
- The backend will automatically pick up the new key when it sees an unknown `kid`

## Version Compatibility

| Component | Version | Compatible With | Notes |
|-----------|---------|-----------------|-------|
| Spring Boot (backend) | 4.0.5 | Spring Security 7.0.x | Upgrade to 4.0.6 to align with auth-service; minor patch, no breaking changes expected |
| Spring Boot (auth-service) | 4.0.6 | Spring Security 7.0.x | Current; Spring Authorization Server 7.0.x included via `spring-security-oauth2-authorization-server` |
| spring-security-oauth2-authorization-server | 7.0.x | Spring Boot 4.0.x | No longer a separate project; part of Spring Security 7 release cycle. Version managed via `spring-security.version` property |
| Java | 21 | All above | Already set in both pom.xml files; no change needed |
| WireMock | 3.12.0 | JUnit 5, Spring Boot 4 | Used to stub JWKS endpoint in backend integration tests; no change needed |

## Sources

- [Spring Security Docs: OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — issuer-uri vs jwk-set-uri, JwtAuthenticationConverter, testing patterns; HIGH confidence
- [Spring Security Docs: Getting Started with Authorization Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/getting-started.html) — Maven coordinates for Spring Security 7 authorization server; HIGH confidence
- [Spring Blog: Authorization Server moving to Spring Security 7.0](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/) — Version consolidation, migration impact; HIGH confidence
- [Spring Boot Dependency Versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/index.html) — Spring Boot 4.0.6 managed dependencies; HIGH confidence
- Codebase inspection: `AuthorizationServerConfig.java`, `TokenCustomizerConfig.java`, `CustomUserDetails.java`, `SecurityConfig.java`, `JitProvisioningService.java`, `pom.xml` (both modules) — direct source; HIGH confidence

---
*Stack research for: Zitadel → Custom Auth Service migration, Spring Security resource server integration*
*Researched: 2026-05-02*
