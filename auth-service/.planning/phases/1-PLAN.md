# Implementation Plan: Phase 1 - Foundation & Auth Server Core

This plan outlines the steps to establish the foundation of the `auth-service`, including project configuration, database schema, and the core Spring Authorization Server setup.

## Tasks

### [ ] Task 1.1: Setup Spring Authorization Server skeleton
- **Description:** Add necessary dependencies and configure the core security filters for the Authorization Server.
- **Actions:**
    - Update `pom.xml` with `spring-security-oauth2-authorization-server`.
    - Create `AuthorizationServerConfig` and `DefaultSecurityConfig`.
    - Configure protocol endpoints (Discovery, Token, etc.).
- **Verification Strategy:**
    - `curl -s http://localhost:9000/.well-known/openid-configuration` should return the issuer and endpoint mapping.

### [ ] Task 1.2: Configure JWKS endpoint and validate with a test client
- **Description:** Implement key management for JWT signing and register a static test client.
- **Actions:**
    - Generate a static RSA keypair and expose via `JWKSource`.
    - Register a `test-client` with `openid` and `profile` scopes.
- **Verification Strategy:**
    - `curl -s http://localhost:9000/oauth2/jwks` should return the public key set.

### [ ] Task 1.3: Implement initial Database Schema with Liquibase
- **Description:** Setup tiered configuration, Docker infrastructure, and create the tables for Users, Emails, Organizations, and Memberships.
- **Actions:**
    - Create `application-local.yaml` (H2) and `application-prod.yaml` (Postgres).
    - Create `docker-compose.yaml` with PostgreSQL for the `prod` profile.
    - Implement Liquibase changelogs for `users`, `user_emails`, `companies`, and `memberships`.
- **Verification Strategy:**
    - Run `./mvnw compile liquibase:update` and verify table existence in H2/Postgres.
    - Verify `docker compose config` is valid.

### [ ] Task 1.4: Implement JPA entities and Custom UserDetailsService
- **Description:** Implement the data access layer (including multi-org entities) and security bridge to support authentication via any verified email address.
- **Actions:**
    - Create `User`, `UserEmail`, `Company`, and `Membership` JPA entities.
    - Implement `UserRepository` with custom `@Query` for multi-email lookup.
    - Implement `CustomUserDetailsService` and wire it into the security configuration.
- **Verification Strategy:**
    - Unit tests for `UserRepository` and `CustomUserDetailsService` verifying lookup by both primary and secondary emails.
    - Persistence tests for all entities including multi-org mappings.

## Execution Waves
- **Wave 1:** Configuration, Docker & Database Schema (Plan 01-01)
- **Wave 2:** Auth Server Skeleton & JWKS (Plan 01-02)
- **Wave 3:** Identity Provider & Multi-Email/Multi-Org Support (Plan 01-03)

---
*Note: This document provides a high-level overview. Detailed executor instructions are located in `.planning/phases/01-foundation/`.*
