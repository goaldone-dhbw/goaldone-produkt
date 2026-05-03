# Phase 5: Member Management Rewrite & Cutover - Pattern Map

**Mapped:** 2025-01-28
**Files analyzed:** 25 (create/modify/delete)
**Analogs found:** 18 / 20

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/.../client/AuthServiceManagementClient.java` | client | request-response | `backend/.../client/ZitadelManagementClient.java` | exact |
| `backend/.../client/AuthServiceManagementException.java` | exception | — | `backend/.../exception/ZitadelApiException.java` | exact |
| `backend/.../config/AuthServiceClientConfig.java` | config | — | `backend/.../config/ZitadelConfig.java` + `RestClientConfig.java` | role-match |
| `backend/.../config/StartupValidator.java` (rewrite) | config/health | event-driven | `backend/.../config/StartupValidator.java` (current) | exact |
| `backend/.../service/MemberManagementService.java` (rewrite) | service | request-response | `backend/.../service/MemberManagementService.java` (current) | exact |
| `backend/.../service/MemberInviteService.java` (rewrite) | service | request-response | `backend/.../service/MemberInviteService.java` (current) | exact |
| `backend/.../service/MembershipDeletionService.java` (modify) | service | CRUD | itself (current) | exact |
| `backend/.../service/OrganizationManagementService.java` (modify) | service | CRUD | itself (current) | exact |
| `backend/.../service/SuperAdminService.java` (modify) | service | CRUD | itself (current) | exact |
| `backend/.../service/UserService.java` (modify) | service | CRUD | itself (current) | exact |
| `auth-service/.../controller/OrganizationManagementController.java` (modify) | controller | request-response | itself + `MembershipManagementController.java` | exact |
| `auth-service/.../config/DefaultSecurityConfig.java` (modify) | config/security | — | itself (current) | exact |
| `auth-service/.../controller/MembershipManagementController.java` (modify) | controller | request-response | itself (current) | exact |
| `auth-service/.../dto/InvitationRequest.java` (modify) | dto | — | itself (current) | exact |
| `backend/pom.xml` (modify) | config | — | itself | exact |
| `api-spec/openapi.yaml` (modify) | spec | — | itself | exact |
| Liquibase: `013-pk-unification.xml` | migration | batch/transform | `012-finalize-user-membership-model.xml` | exact |
| Liquibase: `014-add-invitation-id.xml` | migration | CRUD | `012-finalize-user-membership-model.xml` | exact |
| `backend/.../entity/MembershipEntity.java` (modify) | entity | — | itself | exact |
| `backend/.../entity/OrganizationEntity.java` (modify) | entity | — | itself | exact |
| `backend/.../entity/UserEntity.java` (modify) | entity | — | itself | exact |
| `backend/.../exception/GlobalExceptionHandler.java` (modify) | exception-handler | — | itself | exact |

## Pattern Assignments

### `backend/.../client/AuthServiceManagementClient.java` (client, request-response) — NEW

**Analog:** `backend/src/main/java/de/goaldone/backend/client/ZitadelManagementClient.java`

**Class structure pattern** (lines 57-82):
```java
@Slf4j
@Component
public class ZitadelManagementClient {

    private final Zitadel zitadel;
    private final RestClient restClient;
    private final String serviceAccountToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZitadelManagementClient(
            Zitadel zitadel,
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String managementApiUrl,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        this.zitadel = zitadel;
        this.restClient = restClientBuilder.baseUrl(managementApiUrl).build();
        this.serviceAccountToken = serviceAccountToken;
    }
```

**New AuthServiceManagementClient should follow same structure but:**
- Remove `Zitadel zitadel` dependency entirely
- Inject a pre-configured `RestClient` bean (from `AuthServiceClientConfig`) instead of building one
- No `serviceAccountToken` — token injection handled by the RestClient interceptor via `OAuth2AuthorizedClientManager`
- All methods use `restClient.get()/post()/patch()/delete()` with typed response DTOs

**Error handling pattern** (lines 84-102):
```java
public boolean emailExists(String email) {
    try {
        // ... API call ...
    } catch (ApiException e) {
        String errorMsg = String.format("Failed to check email existence: HTTP %d", e.getCode());
        log.error(errorMsg);
        throw new ZitadelApiException(errorMsg, e);
    } catch (Exception e) {
        log.error("Failed to check email existence for {}: {}", normalizedEmail, e.getMessage());
        throw new ZitadelApiException("Failed to check email existence: " + e.getMessage(), e);
    }
}
```
→ Replace `ZitadelApiException` with `AuthServiceManagementException`. Catch `RestClientResponseException` instead of `ApiException` and extract HTTP status code.

**Methods the new client must expose** (derived from all service usages):
- `createInvitation(InvitationRequest)` → POST /api/v1/invitations
- `cancelInvitation(UUID token)` → DELETE /api/v1/invitations/{token}
- `getMembers(UUID companyId)` → GET /api/v1/organizations/{id}/members
- `deleteMembership(UUID userId, UUID companyId)` → DELETE /api/v1/users/{userId}/memberships/{companyId}
- `updateMembershipRole(UUID userId, UUID companyId, Role newRole)` → PATCH /api/v1/users/{userId}/memberships/{companyId}
- `healthCheck()` → GET /api/v1/... (for StartupValidator)

---

### `backend/.../client/AuthServiceManagementException.java` (exception) — NEW

**Analog:** `backend/src/main/java/de/goaldone/backend/exception/ZitadelApiException.java` (lines 1-23)

```java
package de.goaldone.backend.exception;

public class ZitadelApiException extends RuntimeException {
    public ZitadelApiException(String message) {
        super(message);
    }
    public ZitadelApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

→ Copy exact structure, rename to `AuthServiceManagementException`. Add optional `int statusCode` field to carry HTTP status from auth-service for 409 mapping.

---

### `backend/.../config/AuthServiceClientConfig.java` (config) — NEW

**Analog 1:** `backend/src/main/java/de/goaldone/backend/config/ZitadelConfig.java` (lines 1-28)
```java
@Configuration
public class ZitadelConfig {
    @Bean
    public Zitadel zitadel(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        return Zitadel.withAccessToken(issuerUri, serviceAccountToken);
    }
}
```

**Analog 2:** `backend/src/main/java/de/goaldone/backend/config/RestClientConfig.java` (lines 1-37)
```java
@Configuration
public class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}
```

→ New config should define:
1. `OAuth2AuthorizedClientManager` bean (client_credentials provider)
2. A dedicated `RestClient` bean (qualified/named `authServiceRestClient`) using the existing `RestClient.Builder` + OAuth2 interceptor + baseUrl from `${auth-service.base-url}`
3. Per RESEARCH.md pattern lines 208-248

---

### `backend/.../config/StartupValidator.java` (config/health, rewrite)

**Analog:** itself — `backend/src/main/java/de/goaldone/backend/config/StartupValidator.java` (lines 1-84)

**Keep this structural pattern:**
```java
@Component
@Slf4j
public class StartupValidator {
    // Constructor injection of client
    
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @EventListener(ApplicationReadyEvent.class)
    public void validateZitadelConfiguration() {
        if ("test".equals(activeProfile) || "local".equals(activeProfile)) {
            log.debug("Skipping validation in {} profile", activeProfile);
            return;
        }
        // ... validation logic ...
    }
}
```

→ Replace `ZitadelManagementClient` injection with `AuthServiceManagementClient`. Replace org-exists + super-admin checks with a single health/reachability check. Log warning on failure (not exception).

---

### `backend/.../service/MemberManagementService.java` (service, rewrite)

**Analog:** itself — `backend/src/main/java/de/goaldone/backend/service/MemberManagementService.java` (lines 1-253)

**Service class structure to preserve** (lines 28-44):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberManagementService {
    private final ZitadelManagementClient zitadelManagementClient;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipDeletionService membershipDeletionService;
    private final UserService userService;
```

→ Replace `ZitadelManagementClient` with `AuthServiceManagementClient`. Remove `@Value` fields for `goaldoneProjectId` and `mainOrgId` (Zitadel concepts).

**listMembers pattern** (lines 52-141): Currently fetches from Zitadel then cross-references local memberships. New version:
1. Call `authServiceClient.getMembers(orgId)` — single call returns all data
2. Map response DTOs to `MemberResponse` objects
3. Much simpler — no cross-referencing needed

**changeMemberRole pattern** (lines 150-193): Currently validates then calls Zitadel. New version per D-18:
1. Call `authServiceClient.updateMembershipRole(userId, companyId, newRole)` — auth-service returns 409 if last-admin
2. Catch 409 → throw `ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED")`
3. Update local `MembershipEntity` only after success

**removeMember pattern** (lines 201-247): New version per D-18:
1. Call `authServiceClient.deleteMembership(userId, companyId)` — auth-service returns 409 if last-admin
2. Catch 409 → throw `ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_REMOVED")`
3. Delete local membership only after success

**getCallerSub helper** (lines 249-252) — keep:
```java
private String getCallerSub() {
    Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return jwt.getSubject();
}
```

---

### `backend/.../service/MemberInviteService.java` (service, rewrite)

**Analog:** itself — `backend/src/main/java/de/goaldone/backend/service/MemberInviteService.java` (lines 1-109)

**Structure to preserve** (lines 22-37):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberInviteService {
    private final ZitadelManagementClient zitadelManagementClient;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserService userService;
```

→ Replace `ZitadelManagementClient` with `AuthServiceManagementClient`. Remove `@Value` for projectId/mainOrgId.

**inviteMember new flow** (per D-06, D-07):
1. Call `authServiceClient.createInvitation(email, companyId, inviterId, role)`
2. Auth-service creates invitation + sends email, returns `InvitationResponse` with `id`
3. Create `MembershipEntity(status=INVITED, invitation_id=response.id, auth_user_id=null)`

**reinviteMember** (per D-08): Call auth-service createInvitation again, update `invitation_id` on existing entity.

**cancelInvitation** (per D-08): Mark local `MembershipEntity` as cancelled AND call `authServiceClient.cancelInvitation(token)`.

---

### `backend/.../service/MembershipDeletionService.java` (service, modify)

**Current code** (lines 1-56):
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipDeletionService {
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ZitadelManagementClient zitadelManagementClient;

    @Transactional
    public void deleteMembership(UUID membershipId) {
        MembershipEntity membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new IllegalStateException("Membership not found"));
        UUID userId = membership.getUser().getId();
        String authUserId = membership.getUser().getAuthUserId();
        long count = membershipRepository.countByUserId(userId);
        zitadelManagementClient.deleteUser(authUserId);
        membershipRepository.delete(membership);
        // ...
    }
}
```

→ Replace `zitadelManagementClient.deleteUser(authUserId)` with `authServiceClient.deleteMembership(userId, companyId)`. After PK unification, `userId` is the auth-service UUID directly.

---

### `backend/.../service/OrganizationManagementService.java` (service, modify)

**Current code** (lines 1-180): Heavy Zitadel integration for org creation (addOrganization, addHumanUser, addUserGrant, createInviteCode).

→ Replace all `zitadelManagementClient.*` calls with `authServiceClient.*` equivalents. The compensation pattern (lines 123-178) should be preserved but call auth-service instead.

---

### `backend/.../service/SuperAdminService.java` (service, modify)

**Current code** (lines 1-173): Uses `zitadelClient.listUserIdsByRole`, `zitadelClient.getUser`, `zitadelClient.addHumanUser`, etc.

→ Replace all `zitadelClient.*` calls with `authServiceClient.*` equivalents. The listSuperAdmins/inviteSuperAdmin/deleteSuperAdmin flow structure remains.

---

### `backend/.../service/UserService.java` (service, modify)

**Current code** (lines 1-138): References `ZitadelManagementClient` but primarily uses repositories. Only implicit dependency through field injection.

→ Remove `ZitadelManagementClient` field and `@Value` fields for zitadel config. After PK unification, `findByUserAuthUserId` becomes `findByUserId` (auth-service UUID = PK).

---

### `auth-service/.../controller/OrganizationManagementController.java` (controller, modify)

**Analog:** itself — `auth-service/.../controller/OrganizationManagementController.java` (lines 1-73)

**Existing endpoint pattern** (lines 39-48):
```java
@GetMapping("/{id}")
@Operation(summary = "Get organization by ID", description = "Retrieves an organization by its UUID")
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Organization found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
})
public ResponseEntity<CompanyResponse> getOrganizationById(@PathVariable @Parameter(description = "Organization UUID") UUID id) {
    return ResponseEntity.ok(organizationService.getOrganizationById(id));
}
```

→ Add new endpoint following same pattern:
```java
@GetMapping("/{id}/members")
@Operation(summary = "List organization members", description = "Returns all active and pending members of an organization")
@ApiResponses(value = { /* 200, 401, 404 */ })
public ResponseEntity<List<MemberListItemResponse>> listMembers(@PathVariable @Parameter(description = "Organization UUID") UUID id) {
    return ResponseEntity.ok(organizationService.listMembers(id));
}
```

---

### `auth-service/.../config/DefaultSecurityConfig.java` (config/security, modify)

**Current code** (lines 28-40):
```java
@Bean
@Order(1)
public SecurityFilterChain managementApiSecurityFilterChain(HttpSecurity http) throws Exception {
    http
            .securityMatcher("/api/v1/**")
            .authorizeHttpRequests((authorize) -> authorize
                    .requestMatchers("/api/v1/invitations/*/status").permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer((resourceServer) -> resourceServer
                    .jwt(Customizer.withDefaults()));
    return http.build();
}
```

→ Add scope guard: change `.anyRequest().authenticated()` to `.anyRequest().hasAuthority("SCOPE_mgmt:admin")` (or use `.access()` with custom expression). Keep `/api/v1/invitations/*/status` as permitAll.

---

### `auth-service/.../controller/MembershipManagementController.java` (controller, modify)

**Current code** (lines 1-117): Has `deleteMembership` and `updateMembershipRole` with TODO comments at lines 69 and 114.

→ Implement the actual service calls. Replace TODO comments with:
- `deleteMembership`: call `userManagementService.deleteMembership(userId, companyId)`
- `updateMembershipRole`: call `userManagementService.updateMembershipRole(userId, companyId, newRole)`

---

### `auth-service/.../dto/InvitationRequest.java` (dto, modify)

**Current code** (lines 1-30):
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvitationRequest", description = "Request body for creating an invitation")
public class InvitationRequest {
    @NotBlank @Email
    private String email;
    @NotNull
    private UUID companyId;
    @NotNull
    private UUID inviterId;
}
```

→ Add `role` field:
```java
@Schema(description = "Role to assign upon acceptance", example = "USER")
private Role role;
```

---

### Liquibase Changesets (migration)

**Analog:** `backend/src/main/resources/db/changelog/changes/012-finalize-user-membership-model.xml`

**Changeset structure pattern** (lines 1-8):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <changeSet id="012-1" author="gemini-cli">
```

**Master include pattern** (`db.changelog-master.xml`, line 19):
```xml
<include file="changes/012-finalize-user-membership-model.xml" relativeToChangelogFile="true"/>
```

→ Create `013-pk-unification.xml` and `014-add-invitation-id.xml` following same conventions. Register in master with `<include>`. Use `id="013-1"`, `id="014-1"` etc.

**013 PK unification must:**
1. `UPDATE organizations SET id = auth_company_id::uuid` (cast String to UUID)
2. `UPDATE users SET id = auth_user_id::uuid` (cast String to UUID)
3. Update all FK references (memberships.organization_id, memberships.user_id)
4. Drop `auth_company_id` and `auth_user_id` columns
5. Handle nullable `auth_user_id` for invited-but-not-logged-in users

**014 invitation_id must:**
1. `addColumn` to memberships: `invitation_id UUID nullable`
2. `addColumn` to memberships: `status VARCHAR(20) DEFAULT 'ACTIVE'`

---

### `backend/.../controller/MemberManagementController.java` (controller, no change expected)

**Current code** (lines 1-61) — interface implementation pattern preserved:
```java
@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {
    private final MemberInviteService memberInviteService;
    private final MemberManagementService memberManagementService;

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#xOrgID)")
    public ResponseEntity<Void> inviteMember(UUID xOrgID, InviteMemberRequest inviteMemberRequest) {
```

→ Method signatures may change when OpenAPI spec changes `userId` from `String` to `UUID`. Controller delegates to services; no Zitadel references here.

---

### `backend/.../exception/GlobalExceptionHandler.java` (exception-handler, modify)

**Current ZitadelApiException handler** (lines 69-74):
```java
@ExceptionHandler(ZitadelApiException.class)
public ProblemDetail handleZitadelApi(ZitadelApiException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    pd.setType(URI.create("https://goaldone.de/errors/upstream-error"));
    return pd;
}
```

→ Replace with `AuthServiceManagementException` handler using same pattern. Keep `BAD_GATEWAY` status and `upstream-error` type URI.

---

## Shared Patterns

### Service Class Structure
**Source:** All backend services (e.g., `MemberManagementService.java` lines 28-31)
**Apply to:** All rewritten services
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceName {
    private final AuthServiceManagementClient authServiceClient;  // replaces ZitadelManagementClient
    private final MembershipRepository membershipRepository;
    // ...
}
```

### Error Handling — Auth-Service 409 Mapping
**Source:** `MemberManagementService.java` lines 187-189
**Apply to:** `changeMemberRole`, `removeMember`
```java
// Current pattern — keep ResponseStatusException with CONFLICT:
throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED");
```
→ New pattern: catch `AuthServiceManagementException` when statusCode=409, rethrow as `ResponseStatusException(CONFLICT, ...)`.

### Dual Sync Pattern (D-18)
**Apply to:** `changeMemberRole`, `removeMember`, `inviteMember`, `cancelInvitation`
```
1. Call auth-service first
2. If auth-service returns error → throw, do NOT touch local DB
3. If auth-service succeeds → update local MembershipEntity
```

### Auth-Service Controller Annotations
**Source:** `auth-service/.../controller/InvitationManagementController.java` (lines 19-23)
**Apply to:** New members endpoint in OrganizationManagementController
```java
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management endpoints")
```

### Auth-Service DTO Pattern
**Source:** `auth-service/.../dto/CompanyResponse.java` (lines 1-27)
**Apply to:** New `MemberListItemResponse` DTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MemberListItemResponse", description = "Response containing member information")
public class MemberListItemResponse {
    @Schema(description = "User UUID") private UUID userId;
    @Schema(description = "Email address") private String email;
    // ... firstName, lastName, role, status
}
```

### Liquibase Master Registration
**Source:** `db.changelog-master.xml` (lines 1-21)
**Apply to:** New changesets 013, 014
```xml
<include file="changes/013-pk-unification.xml" relativeToChangelogFile="true"/>
<include file="changes/014-add-invitation-id.xml" relativeToChangelogFile="true"/>
```

### Entity Pattern
**Source:** `MembershipEntity.java` (lines 1-47), `OrganizationEntity.java` (lines 1-37), `UserEntity.java` (lines 1-39)
**Apply to:** Entity modifications
```java
@Entity
@Table(name = "tablename")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityName {
    @Id
    private UUID id;
    // ...
}
```

### Backend Controller — OpenAPI Interface Implementation
**Source:** `MemberManagementController.java` (lines 1-61)
**Apply to:** Any controller changes after OpenAPI update
```java
@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {
    // implements generated interface; @PreAuthorize on overrides
}
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `backend/.../config/AuthServiceClientConfig.java` (OAuth2 client_credentials) | config | — | No existing OAuth2 client_credentials config in codebase. Use RESEARCH.md Pattern 1 (lines 201-248) for `OAuth2AuthorizedClientManager` + `RestClient` with interceptor. |

## Metadata

**Analog search scope:** `backend/src/main/java/de/goaldone/backend/`, `auth-service/src/main/java/de/goaldone/authservice/`, `backend/src/main/resources/db/changelog/`
**Files scanned:** ~45
**Pattern extraction date:** 2025-01-28
