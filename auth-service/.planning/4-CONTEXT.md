# Phase 4: Invitation & Reset Flows - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Implementation of the user-facing flows for joining organizations (Invitations) and account recovery (Password Reset). This phase focuses on Thymeleaf-based UI, secure token handling, and multi-org UX logic.

</domain>

<decisions>
## Implementation Decisions

### Visual Identity
- **D-01:** **Theme:** Use the **GoaldoneTheme** palette. Translate the provided preset into CSS variables in a global `base.css`.
- **D-02:** **Styling:** Vanilla CSS only. No external CSS frameworks unless strictly needed for layout (e.g., simple Grid/Flexbox).

### Email Strategy
- **D-03:** **MailService:** Implement a `MailService` interface with two implementations:
    - `LocalMailService`: Active in `local` profile. Logs email content and action URLs to the console.
    - `SmtpMailService`: Active in `prod` profile. Uses `JavaMailSender`.
- **D-04:** **Templates:** Use Thymeleaf for email HTML templates.

### Session Management & Security
- **D-05:** **Session Storage:** Use **Spring Session JDBC** (storing sessions in Postgres/H2) to allow for cluster-safe session management without mandatory Redis for sessions.
- **D-06:** **Strict Invalidation (F5.4):** Upon password reset, all active sessions for the user must be invalidated using the `SessionRegistry`.
- **D-07:** **Token Format:** Use cryptographically secure random strings (e.g., 32+ chars) for invitation and reset tokens.

### Invitation & Reset Flows
- **D-08:** **Token Storage:** Use a generic `VerificationToken` entity with a `type` enum (`INVITATION`, `PASSWORD_RESET`).
- **D-09:** **Existing User UX:** 
    - If an invited user already has an account and is **logged out**, redirect to a landing page explaining they need to log in to accept the invitation.
    - If **logged in**, show an "Accept Invitation" confirmation page.
- **D-10:** **Enumeration Protection:** Password reset requests must return a generic "If an account exists, an email has been sent" message regardless of whether the email was found.

</decisions>

<canonical_refs>
## Canonical References

### Requirements
- `.planning/REQUIREMENTS.md` — Section 4 (Invitations), 5 (Password Reset).

### Domain Entities
- `src/main/java/de/goaldone/authservice/domain/Invitation.java`
- `src/main/java/de/goaldone/authservice/domain/User.java`

</canonical_refs>

<code_context>
## Existing Code Insights

### UI Assets
- Currently `src/main/resources/templates` and `static` are empty. Phase 4 will initialize these.

### Custom Patterns
- **UserStatus:** Use the existing `UserStatus` enum (INVITED, ACTIVE, DISABLED). Invitations for new users should transition them to ACTIVE upon password set.

</code_context>

<specifics>
## Specific Ideas
- **GoaldoneTheme Colors:**
    - Primary 500: `#63729c`
    - Surface 500 (Light): `#556daa`
    - Accent 500: `#a85791`
</specifics>

<deferred>
## Deferred Ideas
- **Account Linking (F190):** While the `VerificationToken` supports it, the independent linking flow (linking two active accounts) is deferred to Phase 5.
</deferred>

---

*Phase: 04-invitation-reset-flows*
*Context gathered: 2026-05-01*
