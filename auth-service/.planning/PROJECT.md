# Project: auth-service

## Overview
A self-hosted OAuth 2.1 / OIDC Authorization Server and Spring Boot Resource Server designed for Multi-Org, Multi-Email-Account support with a custom invitation flow and role-based access control.

## Vision & Goals
- Provide a robust authentication and authorization foundation for the Goaldone ecosystem.
- Support complex organizational structures (Multi-Org) and flexible user identities (Multi-Email).
- Implement secure invitation, password reset, and account linking flows.
- Ensure strict separation between Super-Admins and business-related tasks (Tasks/Schedule/Breaks).

## Tech Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.3.x
- **Security:** Spring Authorization Server 1.3+
- **Persistence:** JPA, PostgreSQL
- **Migrations:** Liquibase
- **Templates:** Thymeleaf
- **Optional:** Redis (Token Store), Actuator (Health)

## Architecture
Three logical components (currently as modules or integrated in `auth-service` project):
1. **auth-service (Port 9000):** Authorization Server + Management API.
2. **resource-server (Port 8080):** Business API (validates JWT via JWKS).
3. **invitation-flow:** Handles invitation, password set/reset, and account linking.

## Key Features
- OAuth 2.1 / OIDC 1.0 Compliance.
- Multi-Email Account Support (one user, multiple verified emails).
- Multi-Org Membership with roles (SUPER_ADMIN, COMPANY_ADMIN, USER).
- Custom Invitation Flow (new user signup or linking existing account).
- Password Reset Flow (secure tokens, no user enumeration).
- Independent Account Linking (F190).
- Domain-based Self-Registration.
- Business Constraints: Last-Admin/Last-Super-Admin checks.
- Role-based Access: Super-Admins restricted from business tasks.
