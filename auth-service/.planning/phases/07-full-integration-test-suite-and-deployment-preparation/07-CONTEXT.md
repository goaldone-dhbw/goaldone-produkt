# Phase 7: Full Integration Test Suite and Deployment Preparation — Context

**Created:** 2026-05-02  
**Status:** Ready for planning

---

## Phase Boundary

Phase 7 delivers a comprehensive integration test suite for all critical OAuth/OIDC flows and deployment-ready packaging for production environments. This includes:

1. **Integration Tests** — API-level tests covering authentication, authorization, user/org management, invitations, password reset, and account linking flows using TestContainers for real database testing
2. **Deployment Preparation** — Docker containerization, environment configuration, CI/CD pipeline, and deployment documentation

---

## Implementation Decisions

### Test Coverage & Framework
- **D-01:** Use Spring Boot `@SpringBootTest` with TestContainers for PostgreSQL integration tests (matches production database, eliminates test/prod divergence)
- **D-02:** Test scope includes all critical user flows: OAuth token issuance, invitation acceptance, password reset, account linking, business constraints (last-admin, role validation), and error scenarios
- **D-03:** Test organization: API controller integration tests + critical path end-to-end scenarios; unit tests remain developer responsibility within modules
- **D-04:** Use H2 in-memory database for unit tests (fast), PostgreSQL TestContainers for integration tests (production-accurate)

### Deployment & Containerization
- **D-05:** Package application as Docker container using official OpenJDK 21 base image with multi-stage build (optimized image size)
- **D-06:** Configuration via environment variables for all sensitive values (database URL, Redis connection, mail settings, OAuth secrets)
- **D-07:** Include health check endpoint for container orchestration platforms (Kubernetes, Docker Compose)
- **D-08:** Liquibase migrations run automatically on application startup (schema initialization handled by app)

### CI/CD Pipeline
- **D-09:** GitHub Actions for CI/CD (Java build cache, Docker build, test execution)
- **D-10:** Test execution required before merge: all integration tests must pass, coverage threshold enforced (target 70% coverage)
- **D-11:** Automated Docker image push to registry on successful main branch builds

### Environments & Secrets
- **D-12:** Three environments (dev, staging, prod) managed via Spring profiles and environment variables
- **D-13:** Development: H2 database, in-memory Redis mock, local SMTP
- **D-14:** Staging: Real PostgreSQL, Redis, mail service (production-like without production data)
- **D-15:** Production: PostgreSQL, Redis, production mail service, strict security headers, HTTPS enforced

### Deployment Documentation
- **D-16:** Provide deployment guide covering: Docker image usage, environment variable configuration, Liquibase migration approach, health check verification
- **D-17:** Include docker-compose.yml for local full-stack testing (includes PostgreSQL, Redis, auth-service)

### Claude's Discretion
- Test framework specifics (JUnit 5 + AssertJ vs alternatives) — Claude will choose based on existing codebase patterns
- Docker registry choice (Docker Hub vs ECR vs other) — Claude will select appropriate registry based on deployment target
- Specific CI/CD secrets management approach — Claude will implement standard GitHub Secrets pattern

---

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Documentation
- `.planning/PROJECT.md` — Architecture overview, tech stack, project vision
- `.planning/ROADMAP.md` — Phase dependencies and project phases

### Prior Phase Context
- `.planning/phases/06-database-schema-and-local-dev/06-CONTEXT.md` — H2 database setup, profile configuration patterns, session storage approach
- `.planning/phases/06-database-schema-and-local-dev/06-02-PLAN.md` — Local development configuration that should be preserved/extended

### Spring Boot & Testing Standards
- `pom.xml` — Current testing dependencies (already includes spring-boot-starter-test, H2, PostgreSQL driver)
- Existing test files: `src/test/java/de/goaldone/authservice/` — Patterns for @SpringBootTest, @DataJpaTest, security testing

---

## Existing Code Insights

### Reusable Assets
- **Spring Boot Test Starters** — pom.xml already includes comprehensive testing dependencies (actuator-test, jpa-test, security-test, etc.)
- **Existing Integration Tests** — `AuthorizationServerEndpointsTests.java`, `InvitationFlowTests.java`, `PasswordResetTests.java` provide patterns for API testing
- **Test Security Setup** — `SessionSecurityTests.java` and `TokenCustomizationTests.java` show how to test with Spring Security
- **H2 Configuration** — Phase 6 already set up H2 for dev profile; test profile should leverage this

### Established Patterns
- **Profile-based Configuration** — Phase 6 uses `spring.profiles.active` for dev/prod separation; test profile follows same pattern
- **Entity Testing** — `UserRepositoryTests.java` shows @DataJpaTest pattern; extend for other entities if needed
- **Spring Security Testing** — `CustomUserDetailsServiceTests.java` demonstrates @WithMockUser and custom authentication principal testing

### Integration Points
- Liquibase migrations must be applied before integration tests run (TestContainers + automatic Spring initialization handles this)
- OAuth2 token issuance tests should use existing test client configuration from Phase 2
- Session-based tests should cover both Redis (prod) and H2 (dev) behavior

---

## Specific Ideas

- Use **@Testcontainers** annotation for automatic container lifecycle management (cleaner than manual container handling)
- Consider **Spring Boot Test Slices** (@WebMvcTest, @DataJpaTest, @RestClientTest) for targeted unit tests; @SpringBootTest only for true integration scenarios
- Docker image should include **JVM options for container resource constraints** (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`)
- CI/CD should build Docker image once and reuse across staging/prod (image immutability)

---

## Deferred Ideas

- **Integration testing with actual email delivery** (SendGrid, AWS SES) — Phase 8+ (production monitoring/observability)
- **Load testing & performance benchmarking** — Separate phase (Phase 8+)
- **API contract testing** (Pact) — Can be added later if integrating with external services
- **Blue-green deployment strategy** — Out of scope (infrastructure-level concern for ops)

---

*Phase: 07-full-integration-test-suite-and-deployment-preparation*  
*Context created: 2026-05-02*
