# Phase 2: Token Customization & Resource Server Integration - Research

**Researched:** 2025-05-15
**Domain:** Spring Security 7.0, Spring Authorization Server, JWT Claims, Startup Bootstrapping
**Confidence:** HIGH

## Summary

This phase focuses on enhancing the `auth-service` to support a Multi-Org model with specialized Super-Admin infrastructure. Key findings include the transition of Spring Authorization Server into the Spring Security 7.0 core, which simplifies token customization. We will use `OAuth2TokenCustomizer` to inject custom claims (`authorities`, `emails`, `primary_email`) into the JWT. Startup validation and bootstrapping will be handled via a prioritized `ApplicationRunner` to ensure the platform has a functional Super-Admin user and organization before accepting traffic.

**Primary recommendation:** Use `OAuth2TokenCustomizer<JwtEncodingContext>` for claim injection and a custom `ApplicationRunner` for startup validation and data bootstrapping.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Special Organization:** A system-level organization (e.g., `SUPER_ADMIN_ORG`) must exist, configurable via `application.yaml`.
- **Validation on Startup:** Verify existence of org and at least one `SUPER_ADMIN` user.
- **Bootstrapping:** Create Super-Admin from `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD` if missing.
- **Authority Derivation:** Derived from `Membership` in `SUPER_ADMIN_ORG` with `Role.SUPER_ADMIN`.
- **JWT Format:** Scoped strings for `authorities`: `ROLE_SUPER_ADMIN`, `ORG_{id}_{ROLE}`.
- **Additional Claims:** `emails` (list of verified), `primary_email`.
- **Resource Server:** External; design JWT for easy consumption.

### the agent's Discretion
- **Spring Boot Startup Hook:** Recommendation of `ApplicationRunner` vs `EventListener`.
- **JWT Claim Mapping:** Determination of "Spring Security friendly" claim names.
- **Data Model Migration:** Implementation of `verified` flag on `UserEmail`.

### Deferred Ideas (OUT OF SCOPE)
- **UI Customization:** Custom login page addressed after core security logic.
- **Management API:** Phase 3.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SA-01 | Super-Admin Bootstrap | `ApplicationRunner` + `Order(1)` ensures this runs before traffic. |
| JWT-01 | Custom Claims Injection | `OAuth2TokenCustomizer` is the standard hook in Spring Security 7.0. |
| JWT-02 | Multi-Org Authorities | `ORG_{id}_{ROLE}` format can be mapped by Resource Servers using custom `JwtAuthenticationConverter`. |
| RS-01 | Resource Server Integration | `authorities` claim name is standard and easy to configure in external services. |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Framework | Current project parent |
| Spring Security | 7.0.5 | Security Core | Includes Auth Server features natively |
| Spring Data JPA | - | Persistence | Standard for SQL interaction |
| Liquibase | 4.27.0 | Migrations | Managed schema evolution |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|--------------|
| Lombok | - | Boilerplate | Data objects & logging |
| AssertJ | - | Testing | Fluent assertions in tests |

**Version verification:**
Verified Spring Security 7.0.5 via `mvn dependency:list`. 
Verified Java 21 via `java -version`.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/de/goaldone/authservice/
├── config/
│   ├── TokenCustomizerConfig.java   # OAuth2TokenCustomizer registration
│   └── SuperAdminProperties.java    # Type-safe config for Super Admin org
├── security/
│   ├── UserAuthorityMapper.java     # Helper for generating ORG_... strings
│   └── CustomUserDetailsService.java # Enhanced to load memberships
└── startup/
    └── BootstrapRunner.java         # Startup validation and data seeding
```

### Pattern 1: Token Customization
**What:** Using `OAuth2TokenCustomizer` to enrich the JWT.
**When to use:** Adding domain-specific claims that aren't part of the standard OIDC set.
**Example:**
```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
    return (context) -> {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claims((claims) -> {
                Set<String> authorities = context.getPrincipal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                claims.put("authorities", authorities);
                // Additional user info
                if (context.getPrincipal().getPrincipal() instanceof CustomUserDetails user) {
                    claims.put("primary_email", user.getUsername());
                    claims.put("emails", user.getVerifiedEmails());
                }
            });
        }
    };
}
```

### Pattern 2: Fail-Fast Startup Validation
**What:** Implementing `ApplicationRunner` to check preconditions.
**When to use:** Ensuring the system is in a valid state (e.g., has a Super Admin) before becoming ready.
**Example:**
```java
@Component
@Order(1)
public class BootstrapRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        if (!validateSuperAdminExists()) {
            if (shouldBootstrap()) {
                createSuperAdmin();
            } else {
                throw new IllegalStateException("Missing Super Admin configuration");
            }
        }
    }
}
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT Generation | Custom JWT library | Spring Security 7.0 | Built-in rotation, signing, and OIDC compliance |
| Startup Logic | Static blocks | `ApplicationRunner` | Proper bean lifecycle management and logging |
| Schema Management | Manual SQL | Liquibase | Versioned, rollback-capable, and team-friendly |

## Common Pitfalls

### Pitfall 1: User Principal Type
**What goes wrong:** `context.getPrincipal().getPrincipal()` returning a String (username) instead of `UserDetails` object.
**Why it happens:** Depending on the authentication provider used during the flow (e.g. form login vs client credentials).
**How to avoid:** Use `UserDetailsService` or a helper to reload the user if the principal is just a name.

### Pitfall 2: Authority Mapping on Resource Server
**What goes wrong:** Resource Server fails to recognize `ORG_{id}_{ROLE}` as a role.
**Why it happens:** Default `JwtGrantedAuthoritiesConverter` expects `SCOPE_` prefix and looks for `scope`/`scp` claims.
**How to avoid:** Configure the Resource Server to use the `authorities` claim and set `authorityPrefix` to empty string or custom logic.

## Code Examples

### Resource Server Authority Mapping (Friendly Config)
```java
// Example of how the EXTERNAL Resource Server should be configured
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    converter.setAuthoritiesClaimName("authorities");
    converter.setAuthorityPrefix(""); // Accept ORG_ and ROLE_ prefixes as-is

    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
    return jwtConverter;
}
```

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | Runtime | ✓ | 21 | - |
| Maven | Build | ✓ | 3.9.x | - |
| PostgreSQL | Persistence | ✓ | 15+ | H2 (Local) |
| Redis | Session/Cache | ✗ | - | In-memory (Mock) |

**Missing dependencies with fallback:**
- Redis: Project uses `spring-boot-starter-data-redis`. For Phase 2 development, local profile can use in-memory mock or be disabled if not strictly required for JWT generation.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockMvc |
| Quick run command | `./mvnw test -Dtest=AuthorizationServerEndpointsTests` |
| Full suite command | `./mvnw test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SA-01 | Super-Admin Bootstrap | Integration | `mvn test -Dtest=BootstrapRunnerTests` | ❌ Wave 0 |
| JWT-01 | Authorities in Token | Integration | `mvn test -Dtest=TokenCustomizationTests` | ❌ Wave 0 |
| JWT-02 | Emails in Token | Integration | `mvn test -Dtest=TokenCustomizationTests` | ❌ Wave 0 |

### Wave 0 Gaps
- [ ] `BootstrapRunnerTests.java` — covers SA-01 (Super-Admin creation)
- [ ] `TokenCustomizationTests.java` — covers JWT-01, JWT-02 (Custom claim verification)
- [ ] `UserEmail` migration — add `verified` column.

## Sources

### Primary (HIGH confidence)
- `org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer` - Official API docs.
- Spring Security 7.0 Release Notes (Merged Authorization Server).
- `pom.xml` - Project configuration.

### Secondary (MEDIUM confidence)
- Community examples of `ExpressionJwtGrantedAuthoritiesConverter` for nested claims.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Verified via POM and shell.
- Architecture: HIGH - Spring standard patterns.
- Pitfalls: MEDIUM - Dependent on external Resource Server implementation.

**Research date:** 2025-05-15
**Valid until:** 2025-06-15
