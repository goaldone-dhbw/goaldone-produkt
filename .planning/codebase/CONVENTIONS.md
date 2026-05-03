# Coding Conventions

**Analysis Date:** 2026-05-02

## Naming Patterns

**Files:**
- PascalCase for classes: `JitProvisioningService.java`, `AuthService.ts`, `UserAccountEntity.java`
- camelCase for files (Frontend): `auth.service.ts`, `auth.guard.ts`, `auth.interceptor.ts`
- Suffix conventions:
  - Services: `*Service.java` (e.g., `JitProvisioningService`, `UserIdentityService`)
  - Entities: `*Entity.java` (e.g., `UserAccountEntity`, `OrganizationEntity`)
  - Repositories: `*Repository.java` (e.g., `UserAccountRepository`)
  - Controllers: `*Controller.java` (generated from OpenAPI)
  - Exceptions: `*Exception.java` (e.g., `EmailAlreadyInUseException`)
  - Filters: `*Filter.java` (e.g., `JitProvisioningFilter`)
  - Angular services: `*.service.ts`
  - Angular guards: `*.guard.ts`
  - Angular interceptors: `*.interceptor.ts`

**Functions/Methods:**
- camelCase consistently: `provisionUser()`, `decodeJwtToken()`, `buildJwt()`, `getUserRoles()`
- Private methods prefixed with underscore in some cases (limited usage): see `_decodeJwtToken()` pattern not used (methods use descriptive prefixes instead)
- Getter/setter naming: `getAccessToken()`, `hasValidAccessToken()`, `setLastSeenAt()`

**Variables:**
- camelCase for local variables and fields: `sub`, `jwtAuth`, `orgId`, `zitadelOrgId`, `callerSub`, `isProd`
- Private fields in services use camelCase: `userAccountRepository`, `oauthService`
- Lombok-generated fields: `id`, `zitadelSub`, `organizationId`, `userIdentityId`, `lastSeenAt`
- Constants in UPPERCASE: `DB_CLOSE_DELAY`, `USER_AGENT` (rare usage)

**Types (Java):**
- Custom exceptions: `*Exception` extends appropriate parent (e.g., `EmailAlreadyInUseException extends ConflictException`)
- DTOs/Models: Auto-generated from `api-spec/openapi.yaml` to `de.goaldone.backend.model.*` and `src/app/api/`
- Entities: Plain POJOs with Lombok annotations (no explicit getters/setters in source)
- Repository interfaces: Extend `JpaRepository<EntityType, ID>`

**Types (TypeScript/Angular):**
- Services: `@Injectable({ providedIn: 'root' })`
- Enums for roles and statuses: `MemberRole`, `MemberStatus`
- Types extracted from decoded JWT claims

## Code Style

**Formatting:**
- Frontend: Prettier (printWidth: 100, singleQuote: true)
- Backend: Spring Boot conventions (4-space indentation, inherited from Maven parent)

**Linting:**
- Frontend: TypeScript strict mode enabled (see `tsconfig.json`)
  - `noImplicitOverride: true`
  - `noPropertyAccessFromIndexSignature: true`
  - `noImplicitReturns: true`
  - `noFallthroughCasesInSwitch: true`
- Backend: Java 21 target, verified by Maven compiler

**Documentation:**
- All public methods/classes must have Javadoc in English
- Example from `JitProvisioningService.java` (lines 19-27):
  ```java
  /**
   * Service for Just-In-Time (JIT) provisioning of users and organizations.
   * When a user logs in for the first time with a JWT from Zitadel, this service
   * ensures that the corresponding local records (UserAccount, UserIdentity, and Organization) are created.
   */
  @Service
  @RequiredArgsConstructor
  @Slf4j
  public class JitProvisioningService {
  ```
- Example from `JitProvisioningFilter.java` (lines 18-23):
  ```java
  /**
   * Security filter that performs Just-In-Time (JIT) provisioning for users upon their first request.
   * It intercepts incoming requests, extracts the JWT from the security context, and ensures that
   * a corresponding UserIdentityEntity and UserAccountEntity exist in the local database...
   */
  ```

## Import Organization

**Order (Java):**
1. Standard library imports (`java.*`, `jakarta.*`)
2. Third-party framework imports (Spring, Lombok)
3. Security-specific imports (`org.springframework.security.*`)
4. Application-specific imports (de.goaldone.backend.*)

Example from `JitProvisioningServiceTest.java`:
```java
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
```

**Order (TypeScript):**
1. Angular core imports
2. RxJS operators
3. Local service imports
4. Test utilities (vitest)

Example from `auth.service.spec.ts`:
```typescript
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';
```

**Path Aliases:**
- Not detected in current setup; relative imports used throughout

## Error Handling

**Backend Exception Strategy:**
Custom exceptions mapped to HTTP status codes via `GlobalExceptionHandler` at `de.goaldone.backend.exception.GlobalExceptionHandler`.

- Extend `ConflictException` for 409 status (e.g., `EmailAlreadyInUseException`)
- Extend `RuntimeException` for unrecoverable errors (e.g., organization creation race condition)
- Response format: RFC 7807 Problem Details with `.setType(URI.create("https://goaldone.de/errors/..."))`

Example pattern from `GlobalExceptionHandler.java`:
```java
@ExceptionHandler(ConflictException.class)
public ProblemDetail handleConflict(ConflictException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setType(URI.create("https://goaldone.de/errors/conflict"));
    return pd;
}
```

Common exceptions:
- `EmailAlreadyInUseException` → 409 CONFLICT
- `LinkTokenExpiredException` → 410 GONE
- `AlreadyLinkedException` → 409 CONFLICT
- `NotMemberOfOrganizationException` → 403 FORBIDDEN
- `NotLinkedException` → 400 BAD_REQUEST
- `WorkingTimeValidationException` → 400 BAD_REQUEST
- `ZitadelApiException` → 502 BAD_GATEWAY

**Backend Filter Error Handling:**
Errors swallowed and logged; requests continue. See `JitProvisioningFilter.java` (lines 46-51):
```java
try {
    jitProvisioningService.provisionUser(jwt);
} catch (Exception e) {
    log.error("JIT provisioning failed for user {}", jwt.getSubject(), e);
    // Don't fail the request, but log the error for monitoring
}
```

**Frontend Auth Error Handling:**
Token errors trigger logout → clear storage → redirect home → initiate new login flow. See `auth.service.ts` (lines 34-40):
```typescript
this.oauthService.events
  .pipe(filter((e) => e.type === 'token_refresh_error' || e.type === 'token_error'))
  .subscribe(() => {
    this.oauthService.logOut(true);
    this.router.navigateByUrl('/');
    this.oauthService.initLoginFlow();
  });
```

## Logging

**Framework:** SLF4J (via Lombok `@Slf4j` annotation in Java)

**Patterns:**
- Info-level for provisioning success: `log.info("Provisioned new user {} in organization {} with identity {}", ...)`
- Debug-level for race condition handling: `log.debug("Organization {} already created by another thread, fetching existing", ...)`
- Error-level for failure states: `log.error("JIT provisioning failed for user {}", jwt.getSubject(), e)`
- Frontend uses custom `LoggerService` for centralized error handling

## Comments

**When to Comment:**
- Business logic explaining trade-offs or race condition handling (e.g., JIT provisioning)
- Inline comments for multi-step processes (e.g., JWT claim extraction sequence in `AuthService`)
- No comments for self-explanatory code

**JSDoc/TSDoc:**
- Java: Extensive Javadoc on all public classes and methods
- TypeScript: Minimal JSDoc; inline comments preferred for complex token decoding

Example from `auth.service.ts` (lines 63-82) — inline comment explaining JWT decode logic:
```typescript
private decodeJwtToken(token: string): any {
    try {
      if (!token) {
        return null;
      }
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null; // Not a valid JWT format (header.payload.signature)
      }
      const payload = parts[1];
      // Robust URL-safe Base64 decoding
      const decodedPayload = decodeURIComponent(atob(payload).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(decodedPayload);
    } catch (e) {
      this.logger.error("Error decoding JWT token:", e);
      return null;
    }
}
```

## Function Design

**Size:**
- Prefer smaller, focused functions (< 50 lines typical)
- Example: `buildJwt()` helper methods 15-20 lines (test fixtures)
- Service methods vary: simple CRUD < 30 lines, complex logic like `provisionUser()` up to 40 lines

**Parameters:**
- Positional arguments for 1-2 parameters
- Named parameters via records/objects for > 3 parameters
- DTOs for complex request/response structures

**Return Values:**
- Explicit null returns for missing data (preferred over Optional in some contexts for simplicity)
- Optional for repository queries
- Collections never null; return empty list/map instead
- Example from `JitProvisioningServiceTest.java`: `List.of(user1, user2)` instead of null

**Transactional Boundaries:**
- `@Transactional` on service methods that modify multiple entities
- Race condition handling via exception catching and retry logic (not pessimistic locking)

## Module Design

**Exports:**
- Java: Service/Repository classes annotated with Spring stereotypes (`@Service`, `@Repository`)
- Angular: Services injectable via `@Injectable({ providedIn: 'root' })` for singleton pattern

**Barrel Files:**
- Not used; direct imports preferred
- Angular tests import specific services: `import { AuthService } from './auth.service';`

## Authentication & JWT Patterns (Auth-Specific)

**Backend JWT Extraction:**
- Extracted via Spring Security's `JwtAuthenticationToken` in `SecurityContext`
- Converted to `Jwt` object for claim access
- Subject claim: `jwt.getSubject()` → user's Zitadel subject ID
- Organization claim: `jwt.getClaimAsString("urn:zitadel:iam:user:resourceowner:id")` → Zitadel org ID
- Roles claim: `jwt.getClaimAsString("urn:zitadel:iam:org:project:roles")` → role map

**Frontend JWT Decoding:**
- Manual Base64 decode of JWT payload (no library used)
- Robust decoding handling URL-safe Base64 (+ and / encoded as %2B, %2F)
- Two role claim support:
  1. Generic `roles` array
  2. Zitadel-specific `urn:zitadel:iam:org:project:roles` object
- Organization ID extraction with fallback chain: `org_id` → `organisation_id` → `urn:zitadel:iam:user:resourceowner:id`

**Example from `auth.service.ts` (getUserRoles, lines 89-112):**
```typescript
getUserRoles(): string[] {
    const decodedToken = this.getDecodedAccessToken();
    if (!decodedToken) {
      return [];
    }

    const rolesKey = Object.keys(decodedToken).find(key =>
      key === 'roles' ||
      key === 'urn:zitadel:iam:org:project:roles' ||
      (key.startsWith('urn:zitadel:iam:org:project:') && key.endsWith(':roles'))
    );

    const rolesObj = rolesKey ? decodedToken[rolesKey] : {};

    return typeof rolesObj === 'object' && !Array.isArray(rolesObj)
      ? Object.keys(rolesObj)
      : Array.isArray(rolesObj)
        ? rolesObj
        : [];
}
```

---

*Convention analysis: 2026-05-02*
