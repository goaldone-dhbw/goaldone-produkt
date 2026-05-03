# Phase 5: Advanced Features & Refinement - Context

**Gathered:** 2026-05-01  
**Status:** Ready for planning

<domain>
## Phase Boundary

Implementation of three advanced features (account linking, business constraints, email template refinement) and German language support for user-facing pages and templates. This phase completes the core auth-service feature set with critical security constraints and improved multi-language support.

**Scope Change:** Domain-based self-registration (5.3) removed from Phase 5 — deferred to future phase.

</domain>

<decisions>
## Implementation Decisions

### Account Linking Flow (F190)
- **D-01:** **Integration Point:** Account linking is triggered during **invitation acceptance** for new organizations (not as a separate self-service feature).
- **D-02:** **Same Email Detection:** If the invited email matches the primary or secondary email of an existing user's account, immediately prompt the user to authenticate and confirm the link.
- **D-03:** **New Email Handling:** If the invited email is new:
  - Default option: set a password for a new account
  - Alternative option: "Connect existing account" button → user logs in with existing credentials
- **D-04:** **Linking Verification:** All account linking requires the user to **authenticate with the target account** (OIDC-PKCE flow per F6.2).
- **D-05:** **Account Merge Behavior:** Successful linking results in **full account consolidation**:
  - The authenticated account becomes the primary account
  - The invited email is added as a secondary UserEmail (if not already present)
  - The new organization membership is added to the merged account
  - The original invitation flow is bypassed (no new user created)

### Business Constraints (Last-Admin/Last-Super-Admin)
- **D-06:** **Validation Layer:** Last-Admin and Last-Super-Admin checks live in the **service layer** (UserManagementService).
- **D-07:** **Protected Operations:** All three operations are protected:
  1. Delete membership — prevent removing the last COMPANY_ADMIN from an org
  2. Demote from COMPANY_ADMIN — prevent changing the last org admin's role to USER
  3. Change SUPER_ADMIN status — prevent removing super_admin flag from the last system admin (if only one exists)
- **D-08:** **Error Response:** When a constraint would be violated, return **409 Conflict** with RFC 7807 Problem Detail response.
- **D-09:** **Constraint Granularity:** Each constraint (last COMPANY_ADMIN vs. last SUPER_ADMIN) has its own error detail to help clients understand which constraint was hit.

### Removed: Domain-based Self-Registration
- **D-10:** **Deferred Decision:** Domain-based self-registration (5.3) is **out of scope for Phase 5**. No implementation.
  - Rationale: Feature complexity better explored in a dedicated phase after core flows are stable.
  - Candidate for Phase 6 or backlog review.

### Email Templates & German Language Support
- **D-11:** **Template Storage:** Keep **file-based Thymeleaf templates** in `src/main/resources/templates/mail/`. Updates require app redeploy.
- **D-12:** **Language Support:** All user-facing pages and email templates are written in **German** (for now). English versions can be added in a future i18n phase if needed.
- **D-13:** **Template Improvements:**
  - Visual polish: Refine HTML layout, colors, typography to match app design system
  - New templates: Create email templates for account linking confirmation flow
  - Plain-text alternatives: Generate plain-text versions of all HTML emails for accessibility and non-HTML email clients
  - Email client testing: Validate templates across Gmail, Outlook, Apple Mail, and other major clients
- **D-14:** **Account Linking Email:** New template needed for confirming account linking attempt (sent to the secondary account owner).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` § 6 (Account Linking / F190) — OIDC-PKCE flow for secondary account verification
- `.planning/REQUIREMENTS.md` § 7.3 (Last-Admin and Last-Super-Admin checks)
- `.planning/REQUIREMENTS.md` § 4 (Invitation Flow) — linking is integrated here

### Prior Phase Context
- `.planning/phases/04-invitation-reset-flows/04-CONTEXT.md` — Invitation flow architecture and existing VerificationToken system
- `.planning/phases/03-management-api/03-CONTEXT.md` — API style (RFC 7807, versioned paths, OAuth 2.1 Client Credentials)

### Domain Entities
- `src/main/java/de/goaldone/authservice/domain/User.java` — Main user entity, holds super_admin flag
- `src/main/java/de/goaldone/authservice/domain/UserEmail.java` — Primary and secondary emails
- `src/main/java/de/goaldone/authservice/domain/Membership.java` — User-Org-Role binding
- `src/main/java/de/goaldone/authservice/domain/Company.java` — Organization entity

### Existing Templates (to be updated for German and enhancements)
- `src/main/resources/templates/mail/invitation.html` — Invitation email (needs German translation + polish)
- `src/main/resources/templates/mail/password-reset.html` — Password reset email (needs German translation + polish)
- `src/main/resources/templates/auth/invitation-landing.html` — Invitation landing page (needs German translation + account linking integration)
- `src/main/resources/templates/auth/invitation-set-password.html` — New password flow (needs German translation)
- `src/main/resources/templates/auth/reset-password.html` — Password reset UI (needs German translation)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **VerificationTokenService** (`src/main/java/de/goaldone/authservice/service/VerificationTokenService.java`) — Already handles token generation and validation. Can be extended for account linking tokens if needed.
- **InvitationManagementService** — Core invitation logic. Account linking integrates here.
- **UserManagementService** — Where Last-Admin constraint checks will be added.
- **CustomUserDetailsService** — Multi-email lookup already implemented. Leveraged during linking flow.

### Established Patterns
- **OAuth 2.1 / OIDC:** Spring Authorization Server already configured. Use existing `/oauth2/authorize` endpoint with custom parameters for account linking verification.
- **Email Templates:** Thymeleaf templates use GoaldoneTheme CSS variables (Phase 4). Maintain consistent styling in new/updated templates.
- **Error Handling:** RFC 7807 Problem Detail pattern already in use (Phase 3). Use consistently for Last-Admin constraint violations.

### Integration Points
- **Invitation Flow:** Account linking integrates during `POST /api/v1/invitations/{token}/accept`. Invitation acceptance logic needs to detect email match and trigger linking flow.
- **User Management:** Last-Admin checks integrate into user deletion and role change endpoints (`DELETE /api/v1/users/{id}/memberships/{companyId}`, `PATCH /api/v1/users/{id}/memberships/{companyId}`).
- **Session Management:** Account linking may create new session. Spring Session JDBC (Phase 4) handles this.

</code_context>

<specifics>
## Specific Ideas

- **Account Linking UX:** The redirect flow from "connect existing account" button should preserve context so user knows which org they're linking to after login.
- **Last-Admin Messaging:** When constraint is hit, the error detail should suggest alternatives (e.g., "Promote another user to COMPANY_ADMIN first, then you can remove this membership").
- **German Content:** Use consistent terminology (e.g., "Verbinden" for linking, "Administrator" for admin roles, "Organisation" for org). Document these terms for consistency.

</specifics>

<deferred>
## Deferred Ideas

- **Domain-based Self-Registration** (originally 5.3) — Deferred to future phase. Candidate for Phase 6 or backlog. Consider after core flows are proven stable.
- **Multi-language i18n Infrastructure** — German is hard-coded for Phase 5. A future phase can introduce message resource files (properties/YAML) for English + other languages if needed.
- **Rich User Search** — Already deferred from Phase 3. Remains out of scope.

</deferred>

---

*Phase: 05-advanced-features-refinement*  
*Context gathered: 2026-05-01*
