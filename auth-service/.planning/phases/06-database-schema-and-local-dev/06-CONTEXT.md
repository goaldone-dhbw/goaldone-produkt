# Phase 6: Database Schema & Local Development Setup — Context

**Created:** 2026-05-02  
**Phase Status:** Planning  
**Priority:** Critical  

---

## Overview

Phase 6 addresses critical blockers for local development and application startup:

1. **Database Schema Error** — Hibernate fails to start due to missing `acceptance_reason` column in the invitations table
2. **Development Experience** — Local testing currently requires external Redis instance, complicating developer setup

This phase enables the team to:
- Run the application locally without external dependencies
- Test all flows (login, invitation, password reset) in development
- Maintain clean production configuration
- Ease onboarding for new developers

---

## Current Situation

### Application Startup Failure

**Error Stack:**
```
Hibernate schema validation: missing column [acceptance_reason] in table [invitations]
Unable to build Hibernate SessionFactory
Application run failed
```

**Root Cause:** JPA entity defines field but database migration is missing.

**Impact:**
- ❌ Application cannot start
- ❌ No local testing possible
- ❌ Blocks all development workflows
- ❌ Cannot deploy without schema fix

### Redis Dependency for Local Dev

**Current Requirements:**
- Redis must be running for Spring Session JDBC
- Complex local setup for developers
- CI/CD test environments need Redis container

**Desired State:**
- Local dev works with embedded H2 database
- Redis optional for production-like testing
- Developers can start coding immediately

---

## Phase Goals

### 6.1: Fix Database Schema Validation Errors
- Identify all missing columns in invitations table
- Create Liquibase migration for missing columns
- Test on fresh and existing databases
- Verify application starts successfully
- **Estimated:** 8-12 hours

### 6.2: Enable Local Development Without Redis
- Add H2 database as fallback session storage
- Create dev profile configuration
- Document local setup process
- Test all flows with H2 sessions
- Provide optional Docker Compose for Redis
- **Estimated:** 12-16 hours

---

## Key Decisions

1. **Session Storage Strategy:**
   - Production: Redis (existing setup, keeps external state)
   - Development: H2 in-memory or file-based (zero setup)
   - Profiles: Conditional configuration based on `spring.profiles.active`

2. **H2 Database Choice:**
   - In-memory for quick testing (`jdbc:h2:mem:`)
   - File-based optional for persistence during session
   - Automatic schema initialization via JPA/Spring Session

3. **Configuration Approach:**
   - Multiple profiles: `dev`, `prod`, `test`
   - No code changes needed, only configuration
   - Clear environment variable handling for secrets

---

## Technical Scope

### In Scope ✅
- Liquibase migration for schema fixes
- H2 database configuration
- Profile-based Spring Session setup
- Configuration files for dev/prod separation
- Developer documentation
- Local testing verification
- Optional Docker Compose for Redis

### Out of Scope ❌
- Entity model refactoring
- Redis cluster configuration
- Session migration between backends
- Performance benchmarking
- Production deployment pipeline

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| Schema migration conflicts | Medium | Audit all previous migrations first |
| H2 sessions lost on restart | Low | Document as dev-only, not for persistence |
| Production accidentally uses H2 | Low | Clear profile defaults, explicit config |
| Session schema mismatches | Medium | Test with exact Spring Session schema |

---

## Dependencies

### Upstream (Completed)
- Phase 4.1: Spring Session JDBC schema (session tables)
- Phase 5.4: Invitation entity with acceptance_reason field

### Downstream (Future)
- Phase 7: Full integration test suite
- Phase 8: Production deployment & monitoring

---

## Success Metrics

After Phase 6 completion, the team should be able to:

✅ **Local Development:**
- Start application with `mvn spring-boot:run -Dspring.profiles.active=dev`
- Run without Redis installed
- Test login, invitation, password reset flows
- Complete onboarding in < 10 minutes

✅ **Database:**
- Application starts with schema validation enabled
- Zero Hibernate schema errors
- Migrations apply cleanly on fresh DB

✅ **Documentation:**
- Clear setup guide for new developers
- Profile usage documented
- Redis optional setup explained
- Troubleshooting guide provided

---

## Timeline

**Planning Phase:** 2026-05-02  
**Execution Phase:** 2026-05-02 (following approval)

**Estimated Duration:**
- 06-01: 8-12 hours (schema fixes)
- 06-02: 12-16 hours (H2 configuration)
- **Total:** 20-28 hours

---

## Open Questions

1. Should H2 be in-memory (memory-only) or file-based (persistent during session)?
   - **Recommendation:** In-memory for simplicity, file-based optional

2. Should we auto-initialize H2 session schema or require manual creation?
   - **Recommendation:** Auto-initialize via Spring Session and JPA

3. Is docker-compose-dev.yml necessary or is documentation sufficient?
   - **Recommendation:** Include for developers wanting Redis testing

4. Should test profile (`test`) use H2 or keep Redis?
   - **Recommendation:** H2 for test isolation, no external dependencies in CI

---

## Next Steps

1. **Approval:** Review and approve phase goals
2. **Planning:** Detailed planning meeting for 06-01 and 06-02
3. **Execution:** Implement fixes in order (6.1 first, then 6.2)
4. **Verification:** Test all scenarios on clean environment
5. **Integration:** Merge to main and update roadmap state

---

## References

- Spring Boot Profiles: https://spring.io/blog/2011/02/15/spring-3-1-m1-introducing-component-scanning-stereotype-annotations-and-java-5-5-annotation-overrides/
- Spring Session JDBC: https://spring.io/projects/spring-session
- H2 Database: https://h2database.com/
- Liquibase: https://www.liquibase.org/
