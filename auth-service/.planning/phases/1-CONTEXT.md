# Context: Phase 1 - Foundation & Auth Server Core

This document captures the implementation decisions for Phase 1 of the `auth-service`. These decisions guide the downstream researcher and planner agents.

## 1. Technical Stack & Configuration
- **Spring Version:** Align with Spring Boot 4 (as specified in `pom.xml`).
- **Environment Management:**
    - Use a `.env-example` for documentation.
    - `application.yaml`: Base configuration with placeholders for environment variables.
    - `application-local.yaml`: Optimized for local development using H2 (PostgreSQL mode).
    - `application-prod.yaml`: Optimized for Docker Compose and production using PostgreSQL.
- **Database:**
    - Local: H2 (in-memory) with `MODE=PostgreSQL`.
    - Production/Docker: PostgreSQL.
    - Migration: Liquibase for all schema changes.

## 2. Domain Model: User & Multi-Email
- **Entities:**
    - `User`: Primary identity (ID, Password, Status, etc.).
    - `UserEmail`: Linked to a `User`. Contains the email address and a `is_primary` flag.
- **Lookup:** Authentication must support looking up a `User` by any of their verified addresses in the `UserEmail` table.

## 3. Multi-Org Role Schema
To support users having different roles in different companies, the following schema is adopted:

- **Tables:**
    - `users`: Standard user table.
    - `companies`: Table for organizations/companies.
    - `memberships`: A join table connecting `users` and `companies`.
        - Columns: `user_id`, `company_id`, `role`.
- **Roles:** `SUPER_ADMIN`, `COMPANY_ADMIN`, `USER`.
    - Note: `SUPER_ADMIN` is a global flag on the `User` entity, while `COMPANY_ADMIN` and `USER` are specific to a `membership` record.
- **JWT Representation:**
    - The JWT will contain a custom claim `orgs` which is an array of objects:
      ```json
      "orgs": [
        { "id": "uuid", "name": "Company A", "role": "COMPANY_ADMIN" },
        { "id": "uuid", "name": "Company B", "role": "USER" }
      ]
      ```
    - The `super_admin` flag will be a separate boolean claim.

## 4. Key Implementation Details
- **Spring Authorization Server:** Initialize with standard OIDC/OAuth 2.1 settings.
- **JWKS:** For Phase 1, use a static keypair for simplicity, but prepare for externalization in later phases.
- **Testing:** Ensure unit tests cover multi-email lookup and role-based claim generation.
