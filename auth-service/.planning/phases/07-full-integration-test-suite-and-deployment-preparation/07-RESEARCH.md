# Phase 7: Full Integration Test Suite and Deployment Preparation — Research

**Research Date:** 2026-05-02  
**Research Focus:** Integration testing frameworks, Docker containerization, CI/CD pipelines, and deployment strategies for Spring Boot 3.3 / Java 21 OAuth 2.1 Authorization Server

---

## Executive Summary: What You Need to Know to Plan This Phase Well

This phase requires understanding four interconnected domains:

1. **Integration Testing Strategy** — Move from isolated @DataJpaTest/@WebMvcTest unit tests to comprehensive @SpringBootTest flows with real PostgreSQL containers
2. **Docker Containerization** — Package the application as a production-ready OCI image using Java 21 with multi-stage builds
3. **CI/CD Automation** — Implement GitHub Actions workflows that build, test, and push Docker images
4. **Deployment Readiness** — Configure environment-aware profiles, health checks, and initialization logic for production

The key insight: TestContainers + Spring Boot Test Slices provide the fastest path to high confidence in critical flows. Docker multi-stage builds minimize image size. GitHub Actions can be kept simple with shared workflows.

---

## Part 1: Integration Testing with Spring Boot & TestContainers

### Current State of the Codebase

**Existing Test Patterns:**
- `AuthorizationServerEndpointsTests.java`: Uses H2 in-memory database with `@SpringBootTest` and `MockMvc`
- `InvitationFlowIntegrationTest.java`: Full-stack test with entity setup, repo injection, and assertions
- Test dependencies already include: `spring-boot-starter-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`
- No TestContainers dependency yet (needs to be added)

**Strengths to leverage:**
- `@Transactional` on test classes for automatic rollback
- `MockMvc` patterns established for API testing
- Entity repositories already tested with `@DataJpaTest` pattern
- Security test utilities available (`@WithMockUser`)

**Gaps to address:**
- No PostgreSQL TestContainers for production-accurate testing
- H2 not a perfect PostgreSQL equivalent (different SQL dialect, sequence handling)
- Session storage (Redis) not tested in integration tests
- OAuth token flows tested with mocked clients, not real OAuth handshakes

---

### Recommended TestContainers Setup

#### 1. **Dependency Configuration**

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>jdbc</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
```

**Rationale:**
- `testcontainers` provides container lifecycle management (`@Testcontainers` annotation)
- `postgresql` includes PostgreSQL-specific configuration
- `jdbc` enables dynamic JDBC URL generation (crucial for multiple test classes with isolated containers)

#### 2. **Base Integration Test Class Pattern**

Create `src/test/java/de/goaldone/authservice/support/IntegrationTestBase.java`:

```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.test.mockmvc.print=true",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
public abstract class IntegrationTestBase {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("auth_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);  // Reuse container across test methods in same class
    
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    protected MockMvc mockMvc;
    
    @Autowired
    protected ObjectMapper objectMapper;
}
```

**Key Design Decisions:**
- `@Testcontainers` with static `@Container` field: Container created once per test class (faster than per-test)
- `withReuse(true)`: Reuse same container across test methods (must ensure test isolation with `@Transactional`)
- `RANDOM_PORT`: Allows parallel test execution without port conflicts
- `DynamicPropertyRegistry`: Injects container connection details into Spring context at runtime
- `spring.jpa.hibernate.ddl-auto=validate`: Ensure migrations run, but don't auto-create schema

#### 3. **Application Test Profile**

Create `src/main/resources/application-integration-test.yaml`:

```yaml
spring:
  datasource:
    # Injected by DynamicPropertyRegistry
    url: ${spring.datasource.url}
    username: ${spring.datasource.username}
    password: ${spring.datasource.password}
    driver-class-name: org.postgresql.Driver
    
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate  # Don't auto-create; use Liquibase migrations
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        show_sql: false
        
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: always
      table-name: SPRING_SESSION
      
  # Mock Redis for session storage (embedded)
  redis:
    url: redis://localhost:6379
    # Use EmbeddedRedis or MockRedis for testing
    
logging:
  level:
    de.goaldone: DEBUG
    org.springframework.security: WARN
    org.springframework.session: DEBUG
    org.hibernate.SQL: DEBUG
```

**Important:** This profile ensures:
- Liquibase migrations run before tests (schema matches production)
- PostgreSQL dialect used throughout
- Session storage configured consistently
- Detailed logging for debugging integration test failures

---

### Test Coverage Strategy

#### Recommended Test Organization

```
src/test/java/de/goaldone/authservice/
├── integration/          # Full @SpringBootTest flows with containers
│   ├── oauth/            # OAuth 2.1 flows
│   ├── user/             # User management APIs
│   ├── invitation/       # Invitation lifecycle
│   ├── password/         # Password reset flows
│   ├── account/          # Account linking flows
│   └── constraint/       # Business rule enforcement
├── service/              # @DataJpaTest for service logic (H2 acceptable)
├── repository/           # @DataJpaTest for data access (H2 acceptable)
├── security/             # Security context testing with @WithMockUser
├── support/              # Test utilities, base classes, fixtures
└── contract/             # API contract tests (future)
```

#### Critical Flows to Test (API-Level Integration Tests)

**OAuth Token Issuance Flow:**
- [TEST-O-01] Client credentials flow (M2M)
- [TEST-O-02] Authorization code flow with custom principal
- [TEST-O-03] Token refresh with multi-org claims
- [TEST-O-04] JWKS endpoint returns valid RSA keys
- [TEST-O-05] OIDC configuration endpoint completeness

**User Management Flow:**
- [TEST-U-01] Create user via management API (M2M secured)
- [TEST-U-02] List users with pagination
- [TEST-U-03] Update user email (verified flag transitions)
- [TEST-U-04] Cannot delete user with active memberships
- [TEST-U-05] Super-admin bootstrap on startup

**Invitation Flow (Complete End-to-End):**
- [TEST-I-01] Create invitation → send email → display landing page
- [TEST-I-02] New account path: invitation → set password → login → membership assigned
- [TEST-I-03] Existing user path: invitation → account linking → confirmation → membership assigned
- [TEST-I-04] Invalid/expired tokens → 404 not found (no enumeration)
- [TEST-I-05] Acceptance reason captured and persisted

**Password Reset Flow:**
- [TEST-P-01] Request reset → token sent to email → landing page
- [TEST-P-02] Set new password → session invalidated → must re-login
- [TEST-P-03] Old sessions don't work after reset
- [TEST-P-04] Expired tokens → 404 (no enumeration)
- [TEST-P-05] Invalid token format → 400 Bad Request

**Account Linking:**
- [TEST-A-01] Independent account linking (different email)
- [TEST-A-02] Confirmation required for new email
- [TEST-A-03] Cannot link to already-registered email
- [TEST-A-04] Linked email can be used for login (multi-email support)

**Business Constraints:**
- [TEST-B-01] Last COMPANY_ADMIN cannot be removed from org
- [TEST-B-02] Last SUPER_ADMIN cannot change status (prevent lockout)
- [TEST-B-03] Domain-based self-registration enabled/disabled per company
- [TEST-B-04] Super-admins cannot access business task APIs
- [TEST-B-05] Role-based access control enforced at API boundary

**Session Management:**
- [TEST-S-01] H2 sessions work in dev profile
- [TEST-S-02] Redis sessions work with TestContainers in integration tests
- [TEST-S-03] Session persistence across container restarts (if using Redis)
- [TEST-S-04] JSESSIONID cookie set correctly
- [TEST-S-05] Concurrent sessions don't interfere

**Error & Security Scenarios:**
- [TEST-E-01] SQL injection attempts blocked
- [TEST-E-02] XSS in email templates escaped
- [TEST-E-03] CSRF tokens validated on POST
- [TEST-E-04] Unauthorized requests rejected (401)
- [TEST-E-05] Forbidden requests rejected (403)
- [TEST-E-06] 404 for enumeration attacks (invitations, password reset)

#### Test Scope Matrix

| Test Type | Tool | Database | Session | Speed | Coverage |
|-----------|------|----------|---------|-------|----------|
| Unit (logic) | JUnit 5 | H2 (fast) | Mock | <1s | Functions, edge cases |
| Repository (JPA) | @DataJpaTest | H2 | N/A | <5s | Query correctness |
| Service | @DataJpaTest + service mock | H2 | N/A | <5s | Business logic |
| Controller | @WebMvcTest | None | Mock | <1s | Request/response mapping |
| **Integration** | **@SpringBootTest + TestContainers** | **PostgreSQL** | **JDBC** | **2-5s each** | **Real scenarios** |
| End-to-End (E2E) | Testcontainers | PostgreSQL + Redis | JDBC | 5-10s | Full flow with all services |

**Coverage Target:** 70% overall, with:
- 100% of OAuth/OIDC endpoints (critical for compliance)
- 95% of user/invitation/password flows (business-critical)
- 85% of management APIs (security-critical)
- 50-70% of utility/formatting code (lower priority)

---

### Testing Spring Security & Custom Principals

The codebase uses custom authentication principals for multi-org support. Testing this requires:

```java
@SpringBootTest
@AutoConfigureMockMvc
public class SecurityIntegrationTest extends IntegrationTestBase {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private CompanyRepository companyRepository;
    
    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void testUserCanAccessOnlyOwnCompanyData() throws Exception {
        // Setup
        Company company = companyRepository.save(Company.builder()
            .name("My Org")
            .build());
        
        // Test: User should see company data
        mockMvc.perform(get("/api/v1/companies/" + company.getId()))
            .andExpect(status().isOk());
    }
    
    @Test
    void testCustomPrincipalWithMultiOrgClaims() throws Exception {
        // Use SecurityContextHolder to set custom principal
        User user = createTestUser("user@example.com");
        Company company1 = createTestCompany("Company 1");
        Company company2 = createTestCompany("Company 2");
        
        createMembership(user, company1, COMPANY_ADMIN);
        createMembership(user, company2, USER);
        
        CustomAuthenticationPrincipal principal = new CustomAuthenticationPrincipal(
            user.getId(),
            user.getPrimaryEmail().getEmail(),
            List.of(company1.getId(), company2.getId()),
            List.of(COMPANY_ADMIN, USER)
        );
        
        // Set in security context
        Authentication auth = new TestingAuthenticationToken(principal, "password", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        // Test: Token should include both orgs
        mockMvc.perform(get("/oauth2/authorize?client_id=test&response_type=code&redirect_uri=http://localhost:8080/callback"))
            .andExpect(status().isOk())
            // Verify token claims include both orgs
    }
}
```

**Key Techniques:**
- `@WithMockUser` for simple role-based testing
- `SecurityContextHolder` + `TestingAuthenticationToken` for complex principals
- Custom `@WithMockOAuthUser` annotation for OAuth-specific scenarios (create this)
- Verify JWT token claims using JWT decoder

---

### Session Testing (Redis vs H2 Behavior)

Session storage differs between environments:
- **Dev (H2):** In-memory, lost on restart (acceptable for dev)
- **Prod (Redis):** Persistent, shared across instances (required for multi-instance)
- **Integration Tests:** Need to validate both

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class SessionPersistenceTest extends IntegrationTestBase {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withStartupAttempts(3);
    
    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        if (redis.isRunning()) {
            registry.add("spring.redis.host", redis::getHost);
            registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        }
    }
    
    @Test
    void testSessionPersistsWithRedis() throws Exception {
        // Login and get session cookie
        String response = mockMvc.perform(post("/login")
            .param("email", "user@example.com")
            .param("password", "password"))
            .andExpect(status().isFound())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");
        
        String sessionId = extractSessionId(response);
        
        // Verify session stored in Redis
        // (Would need Spring Session Redis test utilities)
        
        // Use session cookie in subsequent request
        mockMvc.perform(get("/api/v1/me")
            .header("Cookie", "JSESSIONID=" + sessionId))
            .andExpect(status().isOk());
    }
}
```

---

## Part 2: Docker Containerization for Java 21

### Current State

**Existing Assets:**
- `docker-compose.yaml`: PostgreSQL service definition
- `docker-compose-dev.yml`: PostgreSQL + Redis for local development
- `pom.xml`: Maven build configured, Spring Boot plugin present
- No Dockerfile yet

**Gap:** No production-ready Docker image definition

---

### Multi-Stage Dockerfile for Spring Boot

Create `Dockerfile` at project root:

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy build files
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Cache Maven dependencies (separate layer)
RUN ./mvnw dependency:go-offline -B -DskipTests=true

# Copy source
COPY src src

# Build application
RUN ./mvnw clean package -B -DskipTests=true -Dmaven.compiler.fork=false

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR from builder
COPY --from=builder --chown=appuser:appgroup /build/target/auth-service-*.jar app.jar

# Set user
USER appuser

# JVM options for containerized environments
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -Xms512m -Xmx1024m"

# Health check for orchestration platforms
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:9000/actuator/health || exit 1

# Expose port
EXPOSE 9000

# Start application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```

**Design Rationale:**

| Decision | Rationale |
|----------|-----------|
| **alpine base images** | Smaller image size (~150MB vs 400MB), security patches faster |
| **eclipse-temurin (OpenJDK)** | Open-source, widely used, good compatibility with Spring Boot |
| **Multi-stage build** | Separates build environment from runtime; final image doesn't include maven/compiler |
| **Dependency caching** | `mvn dependency:go-offline` creates separate layer for faster rebuilds when code changes |
| **Non-root user** | Security hardening; prevents container escape from compromising host |
| **JVM options for containers** | `-XX:+UseG1GC`: Garbage collector optimized for containers; MaxRAMPercentage respects container limits |
| **HEALTHCHECK** | Enables Kubernetes/Docker to detect and restart unhealthy containers |
| **ENTRYPOINT with `sh -c`** | Allows environment variable substitution at runtime (required for `$JAVA_OPTS`, `$DB_URL`, etc.) |

---

### Environment Variables & Configuration

Spring Boot application needs to read configuration from environment:

**Required Variables (all services):**
```bash
DB_URL=jdbc:postgresql://auth-db:5432/auth_service
DB_USERNAME=postgres
DB_PASSWORD=<secret>
SUPER_ADMIN_EMAIL=admin@goaldone.de
SUPER_ADMIN_PASSWORD=<secret>
```

**Production Variables:**
```bash
REDIS_HOST=redis.internal
REDIS_PORT=6379
REDIS_PASSWORD=<secret>

MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=<secret>

JWT_ISSUER=https://auth.goaldone.de
CORS_ALLOWED_ORIGINS=https://app.goaldone.de

# Security
SERVER_SSL_ENABLED=true
SERVER_SSL_KEY_STORE_TYPE=PKCS12
SERVER_SSL_KEY_STORE=file:///app/config/keystore.p12
SERVER_SSL_KEY_STORE_PASSWORD=<secret>

SPRING_PROFILES_ACTIVE=prod
```

**Configuration via application-prod.yaml (with env var substitution):**

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
    timeout: 2000
    
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
  
  jpa:
    hibernate:
      ddl-auto: validate  # NEVER use create/update in prod
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc.fetch_size: 50
        jdbc.batch_size: 20
        
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml

server:
  port: 9000
  ssl:
    enabled: ${SERVER_SSL_ENABLED:false}
    key-store: ${SERVER_SSL_KEY_STORE}
    key-store-type: ${SERVER_SSL_KEY_STORE_TYPE:PKCS12}
    key-store-password: ${SERVER_SSL_KEY_STORE_PASSWORD}

logging:
  level:
    root: INFO
    de.goaldone: INFO
    org.springframework.security: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/auth-service/auth-service.log
    max-size: 100MB
    max-history: 30
```

**Key Configuration Patterns:**

1. **All secrets via environment variables** — Never hardcode in config
2. **Connection pooling tuned for containers** — HikariCP with appropriate limits
3. **Liquibase migrations auto-run** — Schema initialization on startup
4. **Validation mode (not create)** — Prevents accidental schema changes
5. **Logging to both stdout and file** — Container logs visible + persistent logs

---

### Health Check Implementation

Add to Spring Boot controller:

```java
@RestController
@RequestMapping("/actuator/health")
public class CustomHealthController {
    
    @Autowired
    private HealthIndicator dbHealth;
    
    @Autowired
    private HealthIndicator redisHealth;
    
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        // Liveness probe: is app running?
        return ResponseEntity.ok(new HealthResponse("UP", "Application is running"));
    }
    
    @GetMapping("/live")
    public ResponseEntity<HealthResponse> liveness() {
        // Kubernetes liveness: restart if not responding
        return ResponseEntity.ok(new HealthResponse("UP", "Application is alive"));
    }
    
    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> readiness() {
        // Kubernetes readiness: remove from load balancer if not ready
        try {
            // Check database connectivity
            dbHealth.getHealth(true);
            
            // Check Redis connectivity (if configured)
            if (isRedisEnabled()) {
                redisHealth.getHealth(true);
            }
            
            return ResponseEntity.ok(new HealthResponse("UP", "Application is ready"));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse("DOWN", e.getMessage()));
        }
    }
}
```

**Kubernetes probes:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/live
    port: 9000
  initialDelaySeconds: 40
  periodSeconds: 10
  timeoutSeconds: 5

readinessProbe:
  httpGet:
    path: /actuator/health/ready
    port: 9000
  initialDelaySeconds: 20
  periodSeconds: 5
  timeoutSeconds: 3
```

---

### Docker Compose for Full-Stack Testing

Update `docker-compose.yaml`:

```yaml
version: '3.8'

services:
  auth-db:
    image: postgres:16-alpine
    container_name: auth-service-db
    environment:
      POSTGRES_DB: auth_service
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
    ports:
      - "5432:5432"
    volumes:
      - auth_db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - auth-network

  auth-redis:
    image: redis:7-alpine
    container_name: auth-service-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - auth-network

  auth-service:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: auth-service
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://auth-db:5432/auth_service
      DB_USERNAME: ${DB_USERNAME:-postgres}
      DB_PASSWORD: ${DB_PASSWORD:-postgres}
      REDIS_HOST: auth-redis
      REDIS_PORT: 6379
      SUPER_ADMIN_EMAIL: ${SUPER_ADMIN_EMAIL:-admin@goaldone.de}
      SUPER_ADMIN_PASSWORD: ${SUPER_ADMIN_PASSWORD:-admin}
    ports:
      - "9000:9000"
    depends_on:
      auth-db:
        condition: service_healthy
      auth-redis:
        condition: service_healthy
    volumes:
      - ./logs:/var/log/auth-service
    networks:
      - auth-network
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:9000/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  auth_db_data:
  redis_data:

networks:
  auth-network:
    driver: bridge
```

**Usage:**
```bash
# Start full stack
docker-compose up -d

# View logs
docker-compose logs -f auth-service

# Run integration tests against running containers
mvn verify -Pintegration-test

# Cleanup
docker-compose down -v
```

---

## Part 3: CI/CD Pipeline with GitHub Actions

### Current State

**Gap:** No `.github/workflows/` directory exists. Need to create:
- Build & Test workflow (runs on PR)
- Docker Image build workflow (runs on main)
- Release workflow (optional for production deployments)

---

### Build & Test Workflow

Create `.github/workflows/build-and-test.yml`:

```yaml
name: Build and Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    # Run on Java 21
    strategy:
      matrix:
        java-version: [ '21' ]
    
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: auth_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
      
      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 6379:6379
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK ${{ matrix.java-version }}
      uses: actions/setup-java@v4
      with:
        java-version: ${{ matrix.java-version }}
        distribution: 'temurin'
        cache: maven  # Cache Maven dependencies
        cache-dependency-path: '**/pom.xml'
    
    - name: Build with Maven
      env:
        DB_URL: jdbc:postgresql://localhost:5432/auth_test
        DB_USERNAME: test
        DB_PASSWORD: test
        REDIS_HOST: localhost
        REDIS_PORT: 6379
      run: |
        mvn clean compile -B -DskipTests=true
    
    - name: Run Unit Tests
      env:
        DB_URL: jdbc:postgresql://localhost:5432/auth_test
        DB_USERNAME: test
        DB_PASSWORD: test
        REDIS_HOST: localhost
        REDIS_PORT: 6379
      run: |
        mvn test -B \
          -Dspring.test.database.replace=any \
          -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
    
    - name: Run Integration Tests
      env:
        DB_URL: jdbc:postgresql://localhost:5432/auth_test
        DB_USERNAME: test
        DB_PASSWORD: test
        REDIS_HOST: localhost
        REDIS_PORT: 6379
      run: |
        mvn verify -B -Pintegration-test
    
    - name: Generate Coverage Report
      run: |
        mvn jacoco:report
    
    - name: Upload Coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        file: ./target/site/jacoco/jacoco.xml
        flags: unittests
        name: codecov-umbrella
        fail_ci_if_error: false  # Don't fail if codecov is down
    
    - name: Check Coverage Threshold (70%)
      run: |
        COVERAGE=$(grep -oP 'TOTAL.*?\K[0-9]+' ./target/site/jacoco/index.html | head -1)
        if [ "$COVERAGE" -lt 70 ]; then
          echo "Coverage $COVERAGE% is below 70% threshold"
          exit 1
        fi
        echo "Coverage $COVERAGE% meets threshold"
    
    - name: Archive Test Results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results-java-${{ matrix.java-version }}
        path: target/surefire-reports
        retention-days: 30

  security-scan:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    
    - name: Run Snyk Security Scan
      uses: snyk/actions/maven@master
      env:
        SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
      with:
        args: --fail-on=high
    
    - name: Check for vulnerable dependencies
      run: |
        mvn dependency-check:check || true
```

**Key Features:**
- **Service containers for PostgreSQL & Redis** — No TestContainers needed (GitHub provides services)
- **Maven dependency caching** — Speeds up subsequent builds (huge time saver)
- **Coverage threshold enforcement** — Prevents coverage regression
- **Artifact storage** — Keeps test results for debugging
- **Security scanning** — Snyk for dependency vulnerabilities

---

### Docker Image Build Workflow

Create `.github/workflows/docker-build-push.yml`:

```yaml
name: Build and Push Docker Image

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  workflow_dispatch:  # Manual trigger

jobs:
  build:
    runs-on: ubuntu-latest
    
    permissions:
      contents: read
      packages: write
    
    steps:
    - uses: actions/checkout@v4
    
    # Build once, tag with multiple registries
    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v2
    
    - name: Log in to Docker Hub
      uses: docker/login-action@v2
      with:
        username: ${{ secrets.DOCKERHUB_USERNAME }}
        password: ${{ secrets.DOCKERHUB_TOKEN }}
    
    - name: Log in to GitHub Container Registry
      uses: docker/login-action@v2
      with:
        registry: ghcr.io
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: |
          docker.io/${{ secrets.DOCKERHUB_USERNAME }}/auth-service
          ghcr.io/${{ github.repository }}
        tags: |
          type=ref,event=branch
          type=semver,pattern={{version}}
          type=semver,pattern={{major}}.{{minor}}
          type=sha,prefix={{branch}}-
          type=raw,value=latest,enable={{is_default_branch}}
    
    - name: Build and push
      uses: docker/build-push-action@v5
      with:
        context: .
        push: ${{ github.event_name != 'pull_request' }}
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}
        cache-from: type=registry,ref=docker.io/${{ secrets.DOCKERHUB_USERNAME }}/auth-service:buildcache
        cache-to: type=registry,ref=docker.io/${{ secrets.DOCKERHUB_USERNAME }}/auth-service:buildcache,mode=max
    
    - name: Scan image for vulnerabilities
      run: |
        docker run --rm \
          -v /var/run/docker.sock:/var/run/docker.sock \
          aquasec/trivy image \
          docker.io/${{ secrets.DOCKERHUB_USERNAME }}/auth-service:${{ github.sha }}
```

**Features:**
- **Build once, push to multiple registries** — Docker Hub + GitHub Container Registry
- **Semantic versioning tags** — `v1.2.3` → `1.2.3`, `1.2`, `latest`
- **Build cache preservation** — Docker registry cache for faster builds
- **Vulnerability scanning** — Trivy checks final image for CVEs

---

### Secrets Management

**GitHub Secrets to configure** (in repo settings):

| Secret | Purpose | Example |
|--------|---------|---------|
| `DOCKERHUB_USERNAME` | Docker Hub push | `joinsider` |
| `DOCKERHUB_TOKEN` | Docker Hub auth token | Personal access token |
| `SNYK_TOKEN` | Snyk security scanning | From snyk.io account |
| `DB_PASSWORD_PROD` | Production database password | Random 32 chars |
| `REDIS_PASSWORD_PROD` | Production Redis password | Random 32 chars |
| `SUPER_ADMIN_PASSWORD_PROD` | Initial admin password | Temporary, must change on first login |
| `MAIL_PASSWORD_PROD` | SendGrid/mail service key | From mail provider |

**Do NOT commit:**
- `.env` files with real secrets
- `application-prod.yaml` with hardcoded secrets
- Private keys or certificates
- Database credentials

---

## Part 4: Deployment Considerations

### Environment-Specific Configuration

**Three profiles: dev, staging, prod**

| Aspect | Dev | Staging | Prod |
|--------|-----|---------|------|
| **Database** | H2 in-memory | PostgreSQL (RDS) | PostgreSQL (RDS) |
| **Session** | JDBC (H2) | JDBC (PostgreSQL) | Redis |
| **Mail** | Log to console | Test SMTP | SendGrid/SES |
| **Cache** | H2 | Redis (optional) | Redis (required) |
| **HTTPS** | No | Self-signed OK | Let's Encrypt / ACM |
| **CORS** | `localhost:*` | Staging domain | Production domain only |
| **Logging** | DEBUG | INFO | INFO (structured) |
| **Secrets** | Defaults in config | .env file | AWS Secrets Manager |

**Configuration per profile:**

```yaml
# application-staging.yaml
spring:
  datasource:
    url: jdbc:postgresql://auth-db-staging.rds.amazonaws.com:5432/auth_service
    hikari:
      maximum-pool-size: 10
  redis:
    host: auth-redis-staging.elasticache.amazonaws.com
    port: 6379
  mail:
    host: smtp.sendgrid.net
    username: apikey

# application-prod.yaml
spring:
  datasource:
    url: jdbc:postgresql://auth-db-prod.rds.amazonaws.com:5432/auth_service
    hikari:
      maximum-pool-size: 20
  redis:
    host: auth-redis-prod.elasticache.amazonaws.com
    port: 6379
  session:
    store-type: redis  # Use Redis, not JDBC in prod
```

---

### Liquibase Migration Strategy in Containers

**Design Pattern: Auto-migrate on startup**

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    drop-first: false  # Never drop in prod
    tag: ${APP_VERSION}  # Tag each migration with version
```

**Considerations:**
1. **Migrate before app accepts traffic** — Start with `readiness = DOWN`, run migrations, then set `readiness = UP`
2. **Zero-downtime rolling updates** — Ensure migrations are backward-compatible (add columns with defaults, don't drop)
3. **Rollback strategy** — Liquibase tags enable rollback if needed
4. **Long-running migrations** — Monitor migration time; set `connect-timeout` appropriately

```bash
# Verify migration can complete before timeout
# Set Kubernetes startupProbe to allow enough time for long migrations
startupProbe:
  httpGet:
    path: /actuator/health/ready
    port: 9000
  failureThreshold: 60  # 60 * 10s = 10 minutes
  periodSeconds: 10
```

---

### PostgreSQL Connection Pooling in Containers

HikariCP is the default Spring Data datasource. Configure for container environment:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Don't exceed DB max connections / num_replicas
      minimum-idle: 5
      connection-timeout: 30000  # 30 seconds
      idle-timeout: 600000  # 10 minutes
      max-lifetime: 1800000  # 30 minutes
      connection-test-query: "SELECT 1"
      leak-detection-threshold: 60000  # Warn if connection held > 60s
```

**Container-specific tuning:**
- **Connection pool size** — Rule of thumb: `connections = (core_count * 2) + spinning_disk_count`
  - For 2-core container: 4-6 connections
  - For 4-core container: 8-10 connections
- **Max lifetime** — Shorter than database idle timeout to prevent unexpected closes
- **Test query** — Validates connection health before handing to application

---

### Logging and Observability

Container logs must be accessible via `docker logs` and structured for aggregation:

```yaml
logging:
  level:
    root: INFO
    de.goaldone: INFO
    org.springframework.security: WARN
  pattern:
    console: >-
      {
        "timestamp":"%d{ISO8601}",
        "level":"%p",
        "logger":"%c",
        "thread":"%t",
        "message":"%msg",
        "exception":"%ex"
      }
```

**Stdout only in containers** — Let container orchestration (Docker, Kubernetes) handle log aggregation:
- Docker logs: `docker logs <container>`
- Kubernetes logs: `kubectl logs <pod>`
- CloudWatch/Stackdriver: Log agents collect from container stdout

---

### Validation Architecture: How to Verify Everything Works

#### Pre-Deployment Checklist

**1. Local Development Validation**
```bash
# Start with dev profile
mvn spring-boot:run -Dspring.profiles.active=dev

# Verify endpoints
curl http://localhost:9000/.well-known/openid-configuration
curl http://localhost:9000/actuator/health

# Run invitation flow manually
# Login → create invitation → accept → verify membership created
```

**2. Integration Test Validation**
```bash
# Run full test suite with TestContainers
mvn verify -Pintegration-test

# Check coverage report
open target/site/jacoco/index.html
```

**3. Docker Image Validation**
```bash
# Build image locally
docker build -t auth-service:test .

# Run with docker-compose
docker-compose up -d
sleep 30  # Wait for startup

# Verify health
curl http://localhost:9000/actuator/health/ready

# Test endpoint
curl -X POST http://localhost:9000/oauth2/token \
  -d grant_type=client_credentials \
  -d client_id=test \
  -d client_secret=test

# Cleanup
docker-compose down -v
```

**4. Staging Deployment Validation**
```bash
# Deploy to staging
kubectl apply -f k8s/staging/

# Wait for rollout
kubectl rollout status deployment/auth-service -n auth-staging

# Smoke tests
# - Login flow works
# - OIDC discovery endpoint responds
# - Health checks pass
# - Logs are structured and visible
```

**5. Production Readiness Validation**
- [ ] All integration tests pass in CI
- [ ] Code coverage >= 70%
- [ ] No high/critical security vulnerabilities
- [ ] Docker image scanned and clean
- [ ] Staging environment passes manual QA
- [ ] Database migration tested on staging
- [ ] Rollback procedure documented and tested
- [ ] On-call runbook created
- [ ] Monitoring and alerts configured
- [ ] Disaster recovery plan in place

---

## Part 5: Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| **TestContainers slow in CI** | Medium | Tests timeout, blocks merge | Cache Docker images in CI; use container reuse |
| **H2 tests pass, PostgreSQL fails** | Medium | Bug escapes to staging/prod | Always test with PostgreSQL in integration tests |
| **Database migrations lock tables** | Low | Service unavailable during deploy | Test migrations on prod-like DB; use Liquibase changesets carefully |
| **Session data lost during container restart** | Medium | Users logged out unexpectedly | Use Redis (not JDBC) in prod; document behavior in staging |
| **Docker image size too large** | Low | Slow deployments, wasted storage | Multi-stage build; use alpine base; remove build artifacts |
| **Secrets exposed in Docker image** | High | Security breach | Use build arguments, environment variables; scan image |
| **Service fails to start in container** | Medium | Deployment fails | Comprehensive health checks; test startup sequence locally |
| **CPU/Memory limits cause OOMKill** | Medium | Service crashes unexpectedly | Right-size JVM heap; monitor in staging; use container resource requests/limits |
| **GitHub Actions secrets leaked** | Low | Credentials compromised | Use environment-specific secrets; rotate regularly; audit access logs |
| **Integration tests are flaky** | Medium | CI/CD blocks merges randomly | Use proper transaction rollback; avoid race conditions; increase timeouts |

---

## Part 6: Recommended Approaches Summary

### Integration Testing Framework

**Recommended Choice: Spring Boot @SpringBootTest + TestContainers + JUnit 5**

**Why:**
- Matches existing patterns in codebase (already using @SpringBootTest)
- TestContainers provides production-accurate PostgreSQL testing
- JUnit 5 is standard for Spring Boot 3.x
- AssertJ for readable assertions (already included)

**Dependencies to add:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### Docker/Containerization Best Practices

**Recommended Approach:**

1. **Multi-stage Dockerfile** using `eclipse-temurin:21-jre-alpine`
2. **Non-root user** for security
3. **HEALTHCHECK** endpoint for orchestration
4. **Environment variables** for all configuration
5. **JVM options** tuned for containers (`-XX:+UseG1GC -XX:MaxRAMPercentage=75.0`)

**Image size target:** < 250MB

### CI/CD Patterns

**Recommended GitHub Actions Setup:**

1. **Build & Test workflow** — Runs on PR and push to main/develop
   - Maven dependency caching for speed
   - Service containers for PostgreSQL/Redis
   - Coverage threshold enforcement
   - Security scanning (Snyk/OWASP)

2. **Docker Build & Push workflow** — Runs on push to main
   - Multi-registry push (Docker Hub + GHCR)
   - Semantic versioning
   - Build cache preservation
   - Image vulnerability scanning (Trivy)

### Validation Architecture

**Test Coverage Matrix:**

- **Unit tests (H2, fast):** Business logic, edge cases → Target 50-70%
- **Integration tests (PostgreSQL, slower):** Critical flows, API boundaries → Target 70-90%
- **E2E tests (optional):** Full user journeys → Target 100% of business-critical paths

**Deployment Validation:**

1. Local: Dev profile works, health checks pass
2. CI: All tests pass, coverage OK, no vulnerabilities
3. Staging: Manual QA, load testing (future)
4. Prod: Canary deployment with monitoring

---

## Appendix: Spring Boot Version Note

**Project uses Spring Boot 4.0.6** (parent version in pom.xml), which:
- Uses Spring Framework 6.2.x
- Requires Java 17+ (project specifies Java 21)
- Spring Authorization Server 1.3+ compatible
- Spring Session JDBC fully supported
- TestContainers JUnit 5 integration built-in

No version conflicts anticipated.

---

## File Structure After Phase 7 Completion

```
auth-service/
├── .github/
│   └── workflows/
│       ├── build-and-test.yml         # PR + main branch testing
│       ├── docker-build-push.yml      # Docker image building
│       └── security-scan.yml          # Vulnerability scanning
├── Dockerfile                          # Multi-stage build
├── docker-compose.yaml                 # Production-like compose
├── src/
│   ├── main/
│   │   ├── java/...
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-dev.yaml
│   │       ├── application-staging.yaml
│   │       ├── application-prod.yaml
│   │       └── application-integration-test.yaml  # NEW
│   └── test/
│       ├── java/
│       │   └── de/goaldone/authservice/
│       │       ├── integration/         # NEW: Integration tests
│       │       ├── support/             # NEW: Test utilities
│       │       └── ... (existing tests)
│       └── resources/
│           └── application-test.yaml    # TEST profile
├── k8s/                                 # NEW: Kubernetes manifests (future)
│   ├── staging/
│   └── prod/
├── docs/                                # NEW: Deployment guides
│   ├── DEPLOYMENT.md
│   ├── DOCKER.md
│   ├── CI-CD.md
│   └── TROUBLESHOOTING.md
└── pom.xml                             # Updated with TestContainers
```

---

**Research Complete: 2026-05-02**

This research provides the technical foundation for Phase 7 planning. The next step is to break down the phase into specific, executable plans (07-01-PLAN, 07-02-PLAN, etc.) that address integration testing, Docker containerization, and CI/CD implementation.
