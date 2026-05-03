# Phase 2 Validation: Token Customization & Resource Server Integration

## Goal
JWT tokens tailored for Resource Servers and Super-Admin bootstrapping.

## Observable Truths
- [x] **Truth 1:** Issued JWT tokens contain `authorities` claim with format `ORG_{id}_{ROLE}` and `ROLE_SUPER_ADMIN` for super admins.
- [x] **Truth 2:** Issued JWT tokens contain `emails` claim with only verified email addresses.
- [x] **Truth 3:** System automatically creates a Super Admin organization (using configured slug) and a Super Admin user on startup if they don't exist.
- [x] **Truth 4:** Authentication logic prevents using unverified emails for login (verified in CustomUserDetailsService).

## Required Artifacts
- **Database Schema:**
  - [x] `user_emails.verified` column (boolean)
  - [x] `companies.slug` column (varchar, unique)
- **Repositories:**
  - [x] `src/main/java/de/goaldone/authservice/repository/CompanyRepository.java`
  - [x] `src/main/java/de/goaldone/authservice/repository/MembershipRepository.java`
- **Logic:**
  - [x] `src/main/java/de/goaldone/authservice/startup/BootstrapRunner.java`
  - [x] `src/main/java/de/goaldone/authservice/config/TokenCustomizerConfig.java`
  - [x] `src/main/java/de/goaldone/authservice/security/CustomUserDetailsService.java` (updated)

## Key Links
- [x] **Link 1:** `TokenCustomizerConfig` -> `UserRepository` (Fetch user emails for claims)
- [x] **Link 2:** `BootstrapRunner` -> `CompanyRepository` / `UserRepository` (Data persistence)
- [x] **Link 3:** `CustomUserDetailsService` -> `User` / `Membership` (Authority mapping)
- [x] **Link 4:** `BootstrapRunner` -> `SuperAdminProperties` (Configuration injection)
