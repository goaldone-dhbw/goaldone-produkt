# Testing Patterns

**Analysis Date:** 2026-05-02

## Test Framework

**Backend:**
- Runner: JUnit 5 (Jupiter)
- Config: `backend/pom.xml` with `spring-boot-starter-test` and `spring-security-test`
- Assertion: AssertJ and JUnit assertions
- Mocking: Mockito with `@ExtendWith(MockitoExtension.class)`
- HTTP Mocking: WireMock 3.12.0 for external API stubbing

Run Commands:
```bash
./mvnw test                              # Run all tests
./mvnw -Dtest=MyTest test               # Run single test class
./mvnw clean package                    # Build and test
```

**Frontend:**
- Runner: Vitest 4.0.8 (Angular 21 compatible via ng test)
- Config: Default Angular test setup with jsdom (see package.json)
- Assertion: Vitest expect()
- Mocking: Vitest's `vi` object for spy/mock functions
- Angular Testing: TestBed for dependency injection, `TestBed.runInInjectionContext()`

Run Commands:
```bash
npm test                # Watch mode (ng test)
npm run test:ci        # Once, outputs junit.xml
```

## Test File Organization

**Backend:**
- Location: `backend/src/test/java/de/goaldone/backend/`
- Naming: `*Test.java` and `*IntegrationTest.java`
- Convention: Parallel structure to src/main (services test in `service/`, controllers in `controller/`, etc.)
- Maven includes: `**/*Tests.java` and `**/*Test.java` (see pom.xml lines 213-216)

**Frontend:**
- Location: Co-located with implementation (e.g., `auth.service.ts` → `auth.service.spec.ts`)
- Naming: `*.spec.ts`
- Structure:
  - `src/app/core/auth/` → auth service, guard, interceptor tests
  - `src/app/features/` → feature component tests
  - `src/app/shared/` → shared component tests

## Test Structure

**Backend Unit Test Pattern (Mockito):**

From `JitProvisioningServiceTest.java`:
```java
@ExtendWith(MockitoExtension.class)
class JitProvisioningServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private JitProvisioningService jitProvisioningService;

    @Test
    void provisionUser_userAlreadyExists_updatesLastSeenAt() {
        // Arrange
        String sub = "existing-user";
        Jwt jwt = buildJwt(sub, "zitadel-org-1", "Org Name");
        
        UserAccountEntity existingUser = new UserAccountEntity();
        existingUser.setId(UUID.randomUUID());
        existingUser.setZitadelSub(sub);
        
        when(userAccountRepository.findByZitadelSub(sub)).thenReturn(Optional.of(existingUser));

        // Act
        jitProvisioningService.provisionUser(jwt);

        // Assert
        verify(userIdentityRepository, never()).save(any());
        assertTrue(existingUser.getLastSeenAt().isAfter(Instant.now().minusSeconds(10)));
        verify(userAccountRepository).save(existingUser);
    }
}
```

**Backend Integration Test Pattern (Spring Boot Test):**

From `MemberManagementIntegrationTest.java`:
```java
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    "zitadel.management-api-url=http://localhost:8099"
})
@ActiveProfiles("local")
class MemberManagementIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();
    
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        userAccountRepository.deleteAll();
    }

    @Test
    void inviteMember_Success() throws Exception {
        stubEmailNotExists();
        stubAddHumanUser("user-new");
        
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }
}
```

**Frontend Test Pattern (Vitest + Angular TestBed):**

From `auth.service.spec.ts`:
```typescript
describe('AuthService', () => {
  let service: AuthService;
  let oauthService: OAuthService;
  let router: Router;

  beforeEach(() => {
    const oauthServiceMock = {
      configure: vi.fn(),
      loadDiscoveryDocumentAndTryLogin: vi.fn(() => Promise.resolve(true)),
      setupAutomaticSilentRefresh: vi.fn(),
      hasValidAccessToken: vi.fn(() => true),
      getAccessToken: vi.fn(() => 'test-token'),
      initLoginFlow: vi.fn(),
      logOut: vi.fn(),
      events: eventsSubject.asObservable(),
    };

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: OAuthService, useValue: oauthServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });

    service = TestBed.inject(AuthService);
    oauthService = TestBed.inject(OAuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should configure OAuthService with correct settings', async () => {
    await service.initialize();

    expect(oauthService.configure).toHaveBeenCalledWith(
      expect.objectContaining({
        scope: 'openid profile email offline_access urn:zitadel:iam:user:resourceowner',
        responseType: 'code',
        useSilentRefresh: false,
      }),
    );
  });
});
```

## Mocking

**Backend Framework:** Mockito

**JWT Mocking Pattern (Critical for Auth Testing):**

From `JitProvisioningFilterTest.java` and `MemberManagementIntegrationTest.java`:

**Unit Test JWT Construction (via Mockito):**
```java
private Jwt buildJwt(String sub) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(sub)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    return Jwt.withTokenValue("token")
        .claims(c -> c.putAll(claims.getClaims()))
        .headers(h -> h.put("alg", "HS256"))
        .build();
}

@Test
void jwtAuthentication_provisioningThrows_swallowsExceptionAndContinues() throws ServletException, IOException {
    Jwt jwt = buildJwt("test-sub");
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(auth);
    
    jitProvisioningFilter.doFilterInternal(request, response, filterChain);
}
```

**Integration Test JWT with MockMvc (Must use .authorities()):**

From `MemberManagementIntegrationTest.java`:
```java
private Jwt buildJwt(String sub, String role) {
    Map<String, Object> rolesClaim = new HashMap<>();
    rolesClaim.put(role, new HashMap<>());

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(sub)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuer("http://localhost:8099")
        .claim("email", sub + "@example.com")
        .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
        .build();

    return Jwt.withTokenValue("token")
        .claims(c -> c.putAll(claims.getClaims()))
        .headers(h -> h.put("alg", "HS256"))
        .build();
}

@Test
void inviteMember_Success() throws Exception {
    mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
        .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
}
```

**CRITICAL JWT Testing Note:** MockMvc `jwt()` post-processor requires explicit `.authorities(...)` call. Custom JWT claim-to-authority conversion via `JwtAuthenticationConverter` is NOT auto-applied in tests. See memory note: "MockMvc jwt() needs explicit .authorities(...) — custom JwtAuthenticationConverter is not auto-applied".

**WireMock Stubbing Pattern:**

From `MemberManagementIntegrationTest.java`:
```java
private void stubEmailNotExists() {
    wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users.*"))
        .willReturn(okJson("{\"result\": []}")));
    wireMockServer.stubFor(WireMock.post(urlPathMatching("/zitadel\\.user\\.v2\\.Users/.*"))
        .willReturn(okJson("{\"result\": []}")));
}

private void stubAddHumanUser(String userId) {
    wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/human"))
        .willReturn(okJson("{\"userId\": \"" + userId + "\"}")));
}
```

**Frontend Mocking Pattern (Vitest):**

From `auth.service.spec.ts` and `auth.interceptor.spec.ts`:
```typescript
const authServiceMock = {
  getAccessToken: vi.fn(() => 'test-token'),
};

TestBed.configureTestingModule({
  providers: [{ provide: AuthService, useValue: authServiceMock }],
});

// Mocking return values
vi.mocked(oauthService.getAccessToken).mockReturnValue('test-token');
```

**What to Mock:**
- External dependencies: Repository, Service, OAuthService, HTTP clients (WireMock)
- Database state: Pre-populate via `.save()` calls in `@BeforeEach`
- SecurityContext: Explicitly set with mock JWT for integration tests
- HTTP responses: Stub all external service calls (Zitadel APIs via WireMock)

**What NOT to Mock:**
- Entities themselves: Construct real instances with `.new` and `.set()`
- Core business logic: Test actual service/filter logic, not mocked versions
- Spring's security context holder: Use real SecurityContextHolder with mocked JWT
- Database: Use real H2 in-memory DB for integration tests (no mocking)

## Fixtures and Factories

**Test Data Pattern:**

From `JitProvisioningServiceTest.java`:
```java
private Jwt buildJwt(String sub, String zitadelOrgId, String orgName) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(sub)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .claim("urn:zitadel:iam:user:resourceowner:id", zitadelOrgId)
        .claim("urn:zitadel:iam:user:resourceowner:name", orgName)
        .build();
    return Jwt.withTokenValue("token")
        .claims(c -> c.putAll(claims.getClaims()))
        .headers(h -> h.put("alg", "HS256"))
        .build();
}
```

From `MemberManagementIntegrationTest.java`:
```java
@BeforeEach
void setUp() {
    // WireMock setup
    if (!wireMockServer.isRunning()) {
        wireMockServer.start();
    }
    wireMockServer.resetAll();

    // MockMvc setup with Spring Security
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    // Database cleanup
    userAccountRepository.deleteAll();
    userIdentityRepository.deleteAll();
    organizationRepository.deleteAll();

    // Create test data
    UserIdentityEntity identity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
    userIdentityRepository.save(identity);

    myOrg = new OrganizationEntity(UUID.randomUUID(), "org-123", "My Org", Instant.now());
    organizationRepository.save(myOrg);

    myAdminAccount = new UserAccountEntity(
        UUID.randomUUID(), "admin-sub",
        myOrg.getId(), identity.getId(),
        Instant.now(), Instant.now(), new ArrayList<>());
    userAccountRepository.save(myAdminAccount);
}
```

**Frontend Fixture Pattern (Helper Functions):**

From `auth.service.spec.ts`:
```typescript
const createMockJwt = (payload: any): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payloadStr = btoa(JSON.stringify(payload));
  const signature = 'mock-signature';
  return `${header}.${payloadStr}.${signature}`;
};

it('should decode a valid JWT token payload', () => {
  const payload = { sub: '123', name: 'John Doe' };
  const token = createMockJwt(payload);
  vi.mocked(oauthService.getAccessToken).mockReturnValue(token);

  const decoded = service.getDecodedAccessToken();
  expect(decoded).toEqual(payload);
});
```

**Location:**
- Backend: Inline `buildXxx()` methods in test classes (no separate factories)
- Frontend: Inline helper functions or constants within test blocks
- No shared test fixtures directory detected; each test class is self-contained

## Coverage

**Requirements:** Not enforced (no codecov, no enforced minimum)

**JaCoCo Plugin:**
- Included in `backend/pom.xml` (lines 219-233)
- Generates reports at `target/site/jacoco/index.html` after test phase
- No coverage gates configured

View Coverage:
```bash
./mvnw test                # Generates JaCoCo report
# Open target/site/jacoco/index.html in browser
```

## Test Types

**Backend:**

**Unit Tests:**
- Scope: Single service/filter in isolation
- Approach: Mockito mocks for all dependencies, no Spring context
- Example: `JitProvisioningServiceTest` (27 mocks, 7 tests)
- No database: All DB access mocked via repositories

**Filter/Handler Tests:**
- Scope: Security filter or exception handler logic
- Approach: Mock HTTP request/response, SecurityContext
- Example: `JitProvisioningFilterTest` tests filter with mocked service

**Integration Tests:**
- Scope: Full Spring Boot context, real DB (H2), HTTP layer
- Approach: `@SpringBootTest` with `MockMvc`, profiles set to `local`
- Database: Real H2 in-memory, cleaned in `@BeforeEach`
- External services: WireMock stubs
- Example: `MemberManagementIntegrationTest`, `AccountLinkingIntegrationTest`
- JWT testing: Use MockMvc `.with(jwt().jwt(...).authorities(...))`

**Frontend:**

**Component/Service Tests:**
- Scope: Single service or component
- Approach: Vitest with TestBed dependency injection
- No HTTP: Mock all HTTP calls via `HttpClient` mocks
- Example: `auth.service.spec.ts`, `auth.guard.spec.ts`

**Guard Tests:**
- Example: `auth.guard.spec.ts`
- Tests: Returns true/false based on token presence
- Uses `TestBed.runInInjectionContext()` for injection context

**Interceptor Tests:**
- Example: `auth.interceptor.spec.ts`
- Tests: Header injection, conditional behavior
- Uses `next` function mock to capture modified request

## Error Handling in Tests

**Backend Auth Testing:**

From `JitProvisioningFilterTest.java`:
```java
@Test
void jwtAuthentication_provisioningThrows_swallowsExceptionAndContinues() throws ServletException, IOException {
    Jwt jwt = buildJwt("test-sub");
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(auth);

    doThrow(new RuntimeException("Test error")).when(jitProvisioningService).provisionUser(jwt);

    jitProvisioningFilter.doFilterInternal(request, response, filterChain);

    verify(jitProvisioningService).provisionUser(jwt);
    verify(filterChain).doFilter(request, response);  // Request continues despite exception
}
```

From `JitProvisioningServiceTest.java`:
```java
@Test
void provisionUser_orgCreationRaceConditionAndNotFound_throwsRuntimeException() {
    // When org creation fails and retry fetch returns empty
    when(organizationRepository.save(any(OrganizationEntity.class)))
        .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));
    when(organizationRepository.findByZitadelOrgId(zitadelOrgId))
        .thenReturn(Optional.empty())  // First call: not found
        .thenReturn(Optional.empty()); // Second call (in catch): still not found

    assertThrows(RuntimeException.class, () ->
        jitProvisioningService.provisionUser(jwt)
    );
}
```

From `MemberManagementIntegrationTest.java`:
```java
@Test
void inviteMember_EmailConflict() throws Exception {
    stubEmailExists();

    mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
        .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("EMAIL_ALREADY_IN_USE: existing@example.com"));
}

@Test
void inviteMember_Forbidden_NoAdminRole() throws Exception {
    mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
        .with(jwt().jwt(buildJwt("admin-sub", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
}
```

**Frontend Auth Testing:**

From `auth.service.spec.ts`:
```typescript
describe('Token Information Extraction', () => {
  it('should return null if no access token is available', () => {
    vi.mocked(oauthService.getAccessToken).mockReturnValue('');
    expect(service.getDecodedAccessToken()).toBeNull();
  });

  it('should return null if token format is invalid', () => {
    vi.mocked(oauthService.getAccessToken).mockReturnValue('invalid.token');
    expect(service.getDecodedAccessToken()).toBeNull();
  });
});

describe('Error Handling', () => {
  it('should handle token_refresh_error event', async () => {
    await service.initialize();
    eventsSubject.next({ type: 'token_refresh_error' });

    expect(oauthService.logOut).toHaveBeenCalledWith(true);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(oauthService.initLoginFlow).toHaveBeenCalled();
  });
});
```

## Async Testing

**Backend:**
- Integration tests with `@Transactional` services: Spring handles commit/rollback
- No explicit async/await; Mockito handles method call verification

**Frontend:**

From `auth.service.spec.ts`:
```typescript
describe('initialize', () => {
  it('should configure OAuthService with correct settings', async () => {
    await service.initialize();

    expect(oauthService.configure).toHaveBeenCalledWith(
      expect.objectContaining({
        scope: 'openid profile email offline_access urn:zitadel:iam:user:resourceowner',
        responseType: 'code',
      }),
    );
  });
});
```

## UserAccountEntity Test Setup

**FK Constraint Critical Note (from memory):**
`UserAccountEntity` has foreign key to `UserIdentityEntity`. Must save identity BEFORE account. H2 enforces FK constraints.

**Pattern from `MemberManagementIntegrationTest.java`:**
```java
@BeforeEach
void setUp() {
    // 1. Create and save UserIdentity FIRST
    UserIdentityEntity identity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
    userIdentityRepository.save(identity);

    // 2. Create and save Organization (if needed)
    myOrg = new OrganizationEntity(UUID.randomUUID(), "org-123", "My Org", Instant.now());
    organizationRepository.save(myOrg);

    // 3. THEN create and save UserAccount with FK references
    myAdminAccount = new UserAccountEntity(
        UUID.randomUUID(), "admin-sub",
        myOrg.getId(), identity.getId(),  // FKs set here
        Instant.now(), Instant.now(), new ArrayList<>());
    userAccountRepository.save(myAdminAccount);
}
```

**Violation Example (WRONG):**
```java
// This will fail: FK constraint violation
UserAccountEntity account = new UserAccountEntity(
    UUID.randomUUID(), "user-sub",
    UUID.randomUUID(), UUID.randomUUID(),  // Non-existent IDs!
    Instant.now(), Instant.now(), new ArrayList<>());
userAccountRepository.save(account);  // ERROR: FK constraint
```

## Common Test Patterns

**JWT Provisioning Integration Test:**

Complete example from `MemberManagementIntegrationTest.java`:
```java
@Test
void inviteMember_Success() throws Exception {
    // Arrange: Stub all external Zitadel APIs
    stubEmailNotExists();
    stubAddHumanUser("user-new");
    stubAddUserGrant();
    stubCreateInviteCode();

    Map<String, String> body = new LinkedHashMap<>();
    body.put("email", "new@example.com");
    body.put("firstName", "Max");
    body.put("lastName", "Mustermann");
    body.put("role", "USER");

    // Act & Assert: Call endpoint with JWT auth
    mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
        .with(jwt()
            .jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))  // Construct JWT with role claim
            .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))  // CRITICAL: explicit authorities
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
}
```

**Race Condition Test:**

From `JitProvisioningServiceTest.java`:
```java
@Test
void provisionUser_orgCreationRaceCondition_retriesAndSucceeds() {
    // Simulate: First thread creates org, second thread races and gets constraint violation
    when(organizationRepository.findByZitadelOrgId(zitadelOrgId))
        .thenReturn(Optional.empty())  // First call: not found
        .thenReturn(Optional.of(existingOrg)); // Second call (in catch): now found

    when(organizationRepository.save(any(OrganizationEntity.class)))
        .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

    jitProvisioningService.provisionUser(jwt);

    verify(organizationRepository, times(1)).save(any(OrganizationEntity.class));
    verify(organizationRepository, times(2)).findByZitadelOrgId(zitadelOrgId);
}
```

**Security Context Cleanup:**

From `JitProvisioningFilterTest.java`:
```java
@AfterEach
void tearDown() {
    SecurityContextHolder.clearContext();  // Always clean after each test
}
```

---

*Testing analysis: 2026-05-02*
