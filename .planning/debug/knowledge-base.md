# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## jit_provisioning_org_memberships — JWT orgs claim empty after auth-service login
- **Date:** 2026-05-03
- **Error patterns:** 403 Forbidden, User is not a member of the requested organization, X-Org-ID not in user's memberships
- **Root cause:** UserRepository.findByEmail() and findByPrimaryEmail() queries did not eagerly fetch the memberships collection, causing CustomUserDetails to have empty memberships, resulting in JWT orgs claim being empty
- **Fix:** Added LEFT JOIN FETCH u.memberships m LEFT JOIN FETCH m.company to both UserRepository query methods to eagerly load memberships and their associated companies
- **Files changed:** auth-service/src/main/java/de/goaldone/authservice/repository/UserRepository.java
---
