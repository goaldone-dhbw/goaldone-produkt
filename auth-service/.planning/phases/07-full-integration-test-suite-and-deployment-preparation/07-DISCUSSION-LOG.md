# Phase 7: Full Integration Test Suite and Deployment Preparation — Discussion Log

> **Audit trail only.** Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02  
**Phase:** 07-full-integration-test-suite-and-deployment-preparation  
**Mode:** Auto-selection (--auto flag)

---

## Test Coverage & Framework

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Boot @SpringBootTest with TestContainers | Real PostgreSQL database, closest to production | ✓ |
| MockMvc with embedded H2 | Faster, less realistic | |
| REST Assured with external test server | External dependency, slower | |

**Rationale:** TestContainers provides production-accurate testing without requiring developers to manage external services. Matches Phase 6's decision to use real PostgreSQL for accuracy.

---

## Deployment & Containerization

| Option | Description | Selected |
|--------|-------------|----------|
| Docker container with multi-stage build | Standard approach, optimized size | ✓ |
| JAR deployment with systemd | Lower-level control, more manual | |
| Native image (GraalVM) | Fast startup, complex build | |

**Rationale:** Docker container is industry standard for Java apps, enables consistent dev→staging→prod pipeline. Multi-stage build keeps image size minimal.

**Configuration approach:** Environment variables for all secrets (database URL, Redis, mail settings, OAuth secrets) — stateless configuration enables cloud-native deployment.

---

## CI/CD Pipeline

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub Actions | Native GitHub integration, Java build cache, free for public | ✓ |
| GitLab CI | Excellent, but requires separate system | |
| Jenkins | Powerful but requires self-hosted infrastructure | |

**Rationale:** GitHub Actions integrates naturally with this project hosted on GitHub. Java caching reduces build time. Test execution gated before merge (70% coverage threshold).

---

## Test Database Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| PostgreSQL via TestContainers (integration tests) | Exact production match, no test/prod divergence | ✓ |
| H2 in-memory (unit tests) | Fast, suitable for isolated unit tests | ✓ |
| External test PostgreSQL instance | Shared state, slower CI/CD | |

**Rationale:** Dual approach — H2 for unit tests (fast), PostgreSQL TestContainers for integration tests. This follows existing Phase 6 pattern where dev uses H2 and real environments use PostgreSQL.

---

## Environment Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Three profiles (dev, staging, prod) with environment variables | Spring profile best practice, cloud-native | ✓ |
| Single config file with conditional logic | Less flexible, harder to manage secrets | |
| Externalized configuration server (Spring Cloud Config) | Overkill for this stage | |

**Rationale:** Spring profiles with environment variables is the standard Java/Spring pattern. Enables dev/staging/prod separation without code changes.

---

## Migration Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Automatic on startup (Liquibase auto-apply) | Simple, ensures schema consistency | ✓ |
| Manual migration step before deployment | Error-prone, requires coordination | |
| Blue-green deployment with separate migration | Complex, not needed at this scale | |

**Rationale:** Automatic Liquibase migration on startup ensures the application and schema are always in sync. TestContainers will use this automatic approach for testing.

---

## Claude's Discretion

Areas where downstream agents have flexibility:

1. **Test Framework Specifics** — JUnit 5 vs alternatives, assertion library (AssertJ vs Hamcrest), mocking approach (Mockito vs others)
2. **Docker Registry** — Where to push images (Docker Hub, ECR, GCR, Nexus)
3. **CI/CD Secrets Management** — Standard GitHub Secrets implementation with environment-specific secrets
4. **Test Coverage Threshold** — Specific percentage (recommended 70%) can be adjusted based on team capacity

---

## Deferred Ideas

- **Integration testing with actual email delivery services** (SendGrid, AWS SES) — Belongs in Phase 8 (production observability)
- **Load testing & performance benchmarking** — Separate phase (Phase 8+)
- **API contract testing (Pact)** — Can be added later if integrating with external services
- **Blue-green deployment infrastructure** — Out of scope (ops/infrastructure concern)

---

*Decision: Auto-selected all gray areas with recommended defaults*  
*Time: 2026-05-02*
