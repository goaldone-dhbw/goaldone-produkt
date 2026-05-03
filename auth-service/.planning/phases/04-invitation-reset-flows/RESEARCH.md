# Phase 4: Invitation & Reset Flows - Research

**Researched:** 2026-05-01
**Domain:** Identity Recovery, Organization Onboarding, Session Management
**Confidence:** HIGH

## Summary

This phase implements the critical user-facing flows for joining organizations and recovering accounts. The technical foundation relies on Spring Session JDBC for persistent, cluster-safe sessions and a generic `VerificationToken` system for secure, time-limited actions. UI will be handled via Thymeleaf with a custom CSS theme, and email delivery will support both local development (console logging) and production (SMTP).

**Primary recommendation:** Use `SpringSessionBackedSessionRegistry` to fulfill the F5.4 requirement for strict session invalidation upon password reset, ensuring security across distributed instances.

<user_constraints>
## User Constraints (from 4-CONTEXT.md)

### Locked Decisions

#### Visual Identity
- **D-01:** **Theme:** Use the **GoaldoneTheme** palette. Translate the provided preset into CSS variables in a global `base.css`.
- **D-02:** **Styling:** Vanilla CSS only. No external CSS frameworks unless strictly needed for layout (e.g., simple Grid/Flexbox).

#### Email Strategy
- **D-03:** **MailService:** Implement a `MailService` interface with two implementations:
    - `LocalMailService`: Active in `local` profile. Logs email content and action URLs to the console.
    - `SmtpMailService`: Active in `prod` profile. Uses `JavaMailSender`.
- **D-04:** **Templates:** Use Thymeleaf for email HTML templates.

#### Session Management & Security
- **D-05:** **Session Storage:** Use **Spring Session JDBC** (storing sessions in Postgres/H2) to allow for cluster-safe session management without mandatory Redis for sessions.
- **D-06:** **Strict Invalidation (F5.4):** Upon password reset, all active sessions for the user must be invalidated using the `SessionRegistry`.
- **D-07:** **Token Format:** Use cryptographically secure random strings (e.g., 32+ chars) for invitation and reset tokens.

#### Invitation & Reset Flows
- **D-08:** **Token Storage:** Use a generic `VerificationToken` entity with a `type` enum (`INVITATION`, `PASSWORD_RESET`).
- **D-09:** **Existing User UX:** 
    - If an invited user already has an account and is **logged out**, redirect to a landing page explaining they need to log in to accept the invitation.
    - If **logged in**, show an "Accept Invitation" confirmation page.
- **D-10:** **Enumeration Protection:** Password reset requests must return a generic "If an account exists, an email has been sent" message regardless of whether the email was found.

### the agent's Discretion
(No specific discretion areas provided in CONTEXT.md)

### Deferred Ideas (OUT OF SCOPE)
- **Account Linking (F190):** While the `VerificationToken` supports it, the independent linking flow (linking two active accounts) is deferred to Phase 5.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| F4.1 | Generate secure, time-limited invitation tokens. | VerificationToken entity + D-07 (32+ chars) |
| F4.2 | Landing page for invitations. | Thymeleaf templates + D-01/D-02 styling |
| F4.3 | Support setting password for new users. | UserStatus transition (INVITED -> ACTIVE) |
| F4.4 | Link existing accounts via invitation. | D-09 handling for authenticated users |
| F5.1 | Request password reset via email. | MailService (D-03) + Thymeleaf templates (D-04) |
| F5.2 | Single-use, time-limited reset tokens. | VerificationToken entity with expiry |
| F5.3 | Prevention of user enumeration. | D-10 implementation in Controller |
| F5.4 | Invalidation of all sessions after reset. | D-06: Spring Session JDBC + SessionRegistry |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-session-jdbc | 3.4.x | Database-backed sessions | Requirement D-05 |
| spring-boot-starter-mail | 3.4.x | Email infrastructure | Requirement D-03 |
| spring-boot-starter-thymeleaf| 3.4.x | Server-side UI | Requirement D-04 |
| thymeleaf-extras-springsecurity6 | 3.1.x | Security context in UI | Required for D-09 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| GreenMail | 2.1.x | Mock SMTP server | Integration testing of email flows |

**Installation:**
```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/de/goaldone/authservice/
├── config/
│   ├── SessionConfig.java       # SessionRegistry & JDBC Session setup
│   └── MailConfig.java          # Conditional MailService beans
├── domain/
│   ├── VerificationToken.java   # Generic token entity
│   └── TokenType.java           # INVITATION, PASSWORD_RESET
├── service/
│   ├── MailService.java         # Interface
│   ├── LocalMailService.java    # @Profile("local")
│   ├── SmtpMailService.java     # @Profile("prod")
│   └── VerificationService.java # Token lifecycle management
└── controller/
    ├── InvitationController.java
    └── PasswordResetController.java

src/main/resources/
├── templates/
│   ├── mail/                   # Email HTML templates
│   └── auth/                   # Invitation/Reset landing pages
└── static/
    └── css/
        └── base.css             # GoaldoneTheme CSS variables
```

### Pattern 1: Session Invalidation (F5.4)
**What:** Using `SpringSessionBackedSessionRegistry` to find and expire all sessions for a specific user ID after a password change.
**When to use:** Password reset completion, security-critical account updates.
**Example:**
```java
// Source: https://docs.spring.io/spring-session/reference/configuration/jdbc.html#jdbc-sessionregistry
@Autowired
private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

public void invalidateUserSessions(String username) {
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
    sessions.values().forEach(session -> sessionRepository.deleteById(session.getId()));
}
```

### Pattern 2: Enumeration-Safe Response
**What:** Returning a success view regardless of whether the email exists in the system.
**When to use:** Password reset requests, invitation requests.
**Example:**
```java
@PostMapping("/reset-request")
public String handleResetRequest(@RequestParam String email) {
    // 1. Always trigger the service (which handles async email if user exists)
    passwordResetService.initiateReset(email);
    
    // 2. Always return the same "Check your email" view
    return "auth/reset-requested";
}
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Session Management | Custom cookies/db-tables | Spring Session JDBC | Handles serialization, cleanup, and security headers out of the box. |
| Token Storage | String field on User | VerificationToken Entity | Decouples security tokens from core user data; allows multiple active tokens of different types. |
| Email Delivery | Direct JavaMail calls | Spring Mail + MailService interface | Allows easy swapping for local console logging (D-03). |

## Common Pitfalls

### Pitfall 1: Session Schema Initialization
**What goes wrong:** App fails to start or session persistence fails because `SPRING_SESSION` tables don't exist.
**How to avoid:** Use `spring.session.jdbc.initialize-schema=always` in local/H2 and provide Liquibase scripts for production/Postgres to ensure schema is managed correctly.

### Pitfall 2: Token Collision or Length
**What goes wrong:** Using short or predictable tokens.
**How to avoid:** Follow D-07: Use `SecureRandom` to generate 32+ character strings (Base64 or Hex).

### Pitfall 3: Context Path in Emails
**What goes wrong:** Email links pointing to `localhost:8080` in production.
**How to avoid:** Use a configurable `app.base-url` property to construct absolute URLs for email links.

## Code Examples

### VerificationToken Lookup
```java
// Source: Standard JPA Repository Pattern
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenAndType(String token, TokenType type);
    
    @Modifying
    @Query("DELETE FROM VerificationToken v WHERE v.expiryDate < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
```

### MailService Interface
```java
public interface MailService {
    void sendInvitation(String to, String inviteUrl, String orgName);
    void sendPasswordReset(String to, String resetUrl);
}
```

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Runtime | ✓ | 21.0.11 | — |
| Maven | Build | ✓ | 3.9.15 | — |
| Docker | Postgres (Prod) | ✓ | 24.10 | — |
| SMTP Server| Email (Prod) | ✗ | — | Use Console logging (D-03) |

**Missing dependencies with fallback:**
- **SMTP Server:** Will use `LocalMailService` (logging to console) as per D-03.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test |
| Config file | src/main/resources/application-local.yaml |
| Quick run command | `./mvnw test -Dtest=InvitationFlowTests,PasswordResetTests` |
| Full suite command | `./mvnw test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F5.3 | Enumeration Protection | Integration | `./mvnw test -Dtest=PasswordResetTests#testEnumerationProtection` | ❌ Wave 0 |
| F5.4 | Session Invalidation | Integration | `./mvnw test -Dtest=SessionSecurityTests#testSessionInvalidation` | ❌ Wave 0 |
| F4.1 | Token Security | Unit | `./mvnw test -Dtest=TokenGenerationTests` | ❌ Wave 0 |

### Wave 0 Gaps
- [ ] `src/test/java/de/goaldone/authservice/security/SessionSecurityTests.java` — covers F5.4
- [ ] `src/test/java/de/goaldone/authservice/controller/PasswordResetTests.java` — covers F5.3
- [ ] `src/test/java/de/goaldone/authservice/controller/InvitationFlowTests.java` — covers F4.1-F4.4

## Sources

### Primary (HIGH confidence)
- [Spring Session JDBC Documentation](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [Spring Mail Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.email)
- [Thymeleaf & Spring Security Integration](https://www.thymeleaf.org/doc/articles/springsecurity.html)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Core Spring libraries used.
- Architecture: HIGH - Follows decisions in 4-CONTEXT.md.
- Pitfalls: MEDIUM - Dependent on environment-specific SMTP/DB behavior.

**Research date:** 2026-05-01
**Valid until:** 2026-06-01
