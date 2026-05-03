# Phase 5: Advanced Features & Refinement - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.  
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01  
**Phase:** 05-advanced-features-refinement  
**Areas discussed:** Account Linking Flow, Business Constraints, Domain-based Self-Registration (removed), Email Templates & German Support

---

## Area 1: Account Linking (F190) — User Experience & Flow

### Question 1: Initiation Point

| Option | Description | Selected |
|--------|-------------|----------|
| Self-contained endpoint in auth-service | New endpoint like POST /api/v1/users/me/link-account that redirects to OIDC-PKCE verify flow | |
| Resource server handles initiation | User clicks 'Link Account' in resource server UI, which redirects to auth-service for verification | |
| Reuse standard OIDC authorization endpoint | Use standard /oauth2/authorize with a custom hint parameter to trigger linking mode | |
| **Custom approach** | Account linking triggered during invitation acceptance for new org. Same email → ask to link. New email → ask for password or click "connect existing account" → login flow. | ✓ |

**User's choice:** Custom approach — Account linking is integrated into the invitation acceptance flow for new organizations.

**Notes:** 
- If invited email matches existing account (primary or secondary) → immediately ask user to authenticate and confirm linking
- If invited email is new → offer password setup OR "connect existing account" button that redirects to login
- Linking always requires authentication with the target account
- This applies every time a user is invited to a new org (consistent flow)

---

### Question 2: Account Merge Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Add new email as secondary email only | Existing account keeps its primary email, the invited email is added as a secondary UserEmail. No account merge. | |
| Full account merge — consolidate emails | Both emails belong to same User. Invited email becomes secondary, existing account gets the new membership in the org. | ✓ |
| You decide | Claude picks the cleanest approach based on the data model. | |

**User's choice:** Full account merge

**Notes:** When linking occurs, the authenticated account absorbs the new email as secondary and gains the new org membership. No duplicate user records.

---

### Question 3: Duplicate Email Edge Case

| Option | Description | Selected |
|--------|-------------|----------|
| Simply add the new org membership | Email is already theirs — no linking flow needed. Just grant access to the new org. | |
| Still trigger the linking confirmation | Confirm intent even if email is already known, for audit/UX clarity. | |
| Reject the invitation | Email already on their account — something is wrong. Return an error. | |
| **Clarification** | If a user is invited to a third org using an email that's already a secondary email on their account, they still go through the login/confirm flow. Linking always requires authentication. | ✓ |

**User's choice:** Linking always requires authentication. Every invitation triggers the same flow (login + confirm).

**Notes:** No shortcuts — every invitation to a new org requires the user to authenticate and confirm, even if the email is already known to them.

---

## Area 2: Business Constraints — Last-Admin & Last-Super-Admin

### Question 1: Validation Layer

| Option | Description | Selected |
|--------|-------------|----------|
| Service layer (UserManagementService) | Checks happen before any DB operations. Error is raised and caught at API level. | ✓ |
| Repository or entity-level | Domain model knows about constraints. Repositories or entities enforce them. | |
| API endpoint before calling service | Controller checks constraint first, then calls service. Keeps service pure. | |

**User's choice:** Service layer (UserManagementService)

**Notes:** Business logic centralized in service layer. Cleaner separation of concerns.

---

### Question 2: Protected Operations

| Option | Description | Selected |
|--------|-------------|----------|
| Delete membership | Prevent deleting the last COMPANY_ADMIN or last SUPER_ADMIN user. | ✓ |
| Demote from COMPANY_ADMIN | Prevent changing the last COMPANY_ADMIN's role to USER. | ✓ |
| Change from SUPER_ADMIN status | Prevent removing super_admin flag from the last system admin (if only 1 exists). | ✓ |
| All of the above | Protect all role-affecting operations. | ✓ |

**User's choice:** All three — full protection for delete, demote, and SUPER_ADMIN flag changes.

**Notes:** Every operation that affects admin status is protected.

---

### Question 3: Error Response

| Option | Description | Selected |
|--------|-------------|----------|
| 409 Conflict | Standard for constraint violations. User-friendly: operation conflicts with system state. | ✓ |
| 422 Unprocessable Entity | Semantically valid request, but can't be processed due to business logic. | |
| 400 Bad Request | General client error. Simple but less specific. | |
| Custom error detail per constraint | Different codes for 'last admin', 'last super-admin', etc. More granular but complexity. | |

**User's choice:** 409 Conflict

**Notes:** RFC 7807 Problem Detail response with 409 status. Error detail explains which constraint was hit (last COMPANY_ADMIN vs. last SUPER_ADMIN).

---

## Area 3: Domain-based Self-Registration

### Scope Decision

**User's choice:** Remove from Phase 5 completely.

**Reason:** User decided this feature is not needed for Phase 5. Deferred to future phase or backlog.

**Status:** Removed from Phase 5 roadmap. Will not be implemented this cycle.

---

## Area 4: Email Templates & German Language Support

### Question 1: Template Storage

| Option | Description | Selected |
|--------|-------------|----------|
| Keep file-based (Thymeleaf) | Templates stay in src/main/resources/templates/. Updates require app redeploy. Simple and familiar. | ✓ |
| Move to database | Templates stored in DB table. Allows runtime updates without redeploy. Add admin UI for editing. | |
| Hybrid: file-based with DB fallback | Default to file-based, but support DB overrides for runtime customization. Best flexibility. | |

**User's choice:** Keep file-based Thymeleaf templates

**Notes:** Simple, familiar approach. No database overhead. Templates are code, versioned with git.

---

### Question 2: Template Improvements & German Support

| Option | Description | Selected |
|--------|-------------|----------|
| Visual polish & new templates | Improve HTML layout, colors, typography. Create new templates for account linking or other Phase 5 flows. | ✓ |
| Plain-text alternatives + testing | Add plain-text versions for accessibility. Test across email clients (Gmail, Outlook, etc). | ✓ |
| Both visual and text improvements | Complete overhaul: Polish design, add new templates, generate plain-text, validate across clients. | |
| **Additional requirement** | All user-facing pages and email texts in German. | ✓ |

**User's choice:** Visual polish + new templates, plain-text alternatives + testing, **plus German language support**

**Notes:**
- All pages (invitation landing, password reset, etc.) in German
- All email templates in German
- Plain-text versions of emails for accessibility
- Visual polish to match design system
- New account linking confirmation email template

---

### Question 3: i18n Scope

| Option | Description | Selected |
|--------|-------------|----------|
| German only (for now) | Hard-code German for all templates and user-facing pages. No infrastructure for other languages yet. | ✓ |
| i18n infrastructure + German | Build message resource files (properties/YAML) to support multiple languages. Start with English + German, extensible for more. | |

**User's choice:** German only (for now)

**Notes:** Hard-coded German. No i18n framework needed. If multiple languages needed in future, can refactor into resource files later.

---

## Summary: Decisions Made

✓ Account linking integrated into invitation acceptance with full account merge  
✓ Business constraints (Last-Admin/Last-Super-Admin) enforced in service layer with 409 responses  
✓ Domain-based self-registration removed from Phase 5 scope  
✓ Email templates stay file-based Thymeleaf with German language support  
✓ Template improvements: polish, new account linking template, plain-text alternatives, email client testing  

**Scope Reduction:** Phase 5 is now 3 main features (account linking, business constraints, email templates + German) instead of 4.

---

*Discussion conducted: 2026-05-01*  
*Status: All areas discussed and decisions locked*
