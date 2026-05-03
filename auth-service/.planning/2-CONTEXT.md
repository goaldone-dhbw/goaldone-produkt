# Phase 2 Context: Token Customization & Resource Server Integration

This document captures the implementation decisions for Phase 2 of the `auth-service`, focusing on JWT customization and the specialized Super-Admin infrastructure.

## 1. Super-Admin Infrastructure
The platform requires a dedicated "System Admin" level that is distinct from regular organizational admins.

- **Special Organization:** A system-level organization (e.g., `SUPER_ADMIN_ORG`) must exist. Its name or slug should be configurable via `application.yaml`.
- **Validation on Startup:** The application must verify the existence of this organization and at least one user with the `SUPER_ADMIN` role within it.
- **Bootstrapping:** 
    - If no Super-Admin exists, one must be created automatically using credentials from environment variables: `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD`.
    - This user is considered "pre-verified" and does not require the standard verification flow.
- **Data Model Alignment:** 
    - The `User.superAdmin` boolean flag will be deprecated or used strictly as a cache/helper. 
    - Authority is derived from a `Membership` in the `SUPER_ADMIN_ORG` with the `Role.SUPER_ADMIN` enum value.

## 2. JWT Design & Customization
To support a separate Spring Boot Resource Server, the JWT must be optimized for Spring Security's authority mapping.

- **Format:** Scoped strings are preferred for the `authorities` claim to allow for easy mapping to `GrantedAuthority`.
- **Structure:**
    - **Global Roles:** `ROLE_SUPER_ADMIN` (if member of the system org).
    - **Org Roles:** `ORG_{org_id}_{ROLE}` (e.g., `ORG_550e8400-e29b-41d4-a716-446655440000_COMPANY_ADMIN`).
- **Additional Claims:**
    - `emails`: A list of all verified email addresses associated with the user.
    - `primary_email`: The user's primary identity.
- **Implementation:** Utilize `OAuth2TokenCustomizer<JwtEncodingContext>` within the Authorization Server configuration.

## 3. Architecture & Integration
- **Project Scope:** This repository focuses exclusively on the **Authorization Server**. No new Resource Server modules are to be created here.
- **External Resource Server:** The JWT and its custom claims are designed to be consumed by an external Resource Server that will handle business logic (Tasks, Schedules, etc.).
- **Header Context (`X-Org-Id`):** While the Resource Server uses this header to filter data, the Authorization Server must ensure the JWT provides enough information (the `orgs` list) for the Resource Server to validate that the user is actually a member of the requested Org ID.

## 4. Pending/Deferred Items
- **UI Customization:** A custom login page was originally slated for Task 2.1 in some notes but is secondary to the Token/Super-Admin logic. It will be addressed after the core security logic is verified.
- **Management API:** (Phase 3) will utilize the same JWT structure for its internal security.
