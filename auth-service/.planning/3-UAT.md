# Phase 3 UAT: Management API

**Status:** ✅ COMPLETED
**User:** @johannes
**Date:** 2026-05-01

## 📋 Test Scenarios

| ID | Feature | Scenario | Status | Notes |
|:---|:---|:---|:---|:---|
| 3.1 | M2M Security | Access protected API without token | ✅ PASSED | Returns 401 Unauthorized |
| 3.2 | M2M Security | Access protected API with valid M2M token | ✅ PASSED | Fixed PasswordEncoder issue |
| 3.3 | User Management | Create, Read, Update, Delete a User | ✅ PASSED | All CRUD operations verified |
| 3.4 | Org Management | Create, Read, Update an Organization | ✅ PASSED | Unique slug constraint verified |
| 3.5 | Invitation | Create invitation and verify business rules | ✅ PASSED | Duplicate membership block verified |

---

## 🧪 Test Execution Details

### [3.1] M2M Security: Unauthorized Access
- **Action:** Call `GET /api/v1/users/search?email=admin@goaldone.de` without Authorization header.
- **Expected:** `401 Unauthorized`.
- **Actual:** `HTTP/1.1 401 Unauthorized` with `WWW-Authenticate: Bearer` header.
- **Result:** ✅ PASSED

### [3.2] M2M Security: Authorized Access
- **Action:** 
  1. Obtain M2M token using Client Credentials grant.
  2. Call `GET /api/v1/users/search?email=admin@goaldone.de` with valid token.
- **Expected:** `200 OK` and user details.
- **Actual:** Success after updating `PasswordEncoder` to `DelegatingPasswordEncoder` in `DefaultSecurityConfig`.
- **Result:** ✅ PASSED

### [3.3] User Management: CRUD Lifecycle
- **Action:** 
  1. `POST /api/v1/users` to create `uat-user@example.com`.
  2. `GET /api/v1/users/{id}` to verify creation.
  3. `PUT /api/v1/users/{id}` to update status.
  4. `DELETE /api/v1/users/{id}` to remove user.
- **Expected:** Success at each step.
- **Actual:** Verified creation, lookup, and deletion (404 after delete). Update worked despite a minor log warning about request reading.
- **Result:** ✅ PASSED

### [3.4] Org Management: CRUD Lifecycle
- **Action:**
  1. `POST /api/v1/organizations` to create `UAT Corp` (slug: `uat-corp`).
  2. `GET /api/v1/organizations/{id}` to verify.
  3. `PUT /api/v1/organizations/{id}` to update name.
  4. Attempt duplicate slug creation.
- **Expected:** Success for CRUD, 400 for duplicate slug.
- **Actual:** CRUD succeeded. Duplicate slug returned RFC 7807 error with detail: "Organization with slug 'uat-corp' already exists".
- **Result:** ✅ PASSED

### [3.5] Invitation: Flow and Rules
- **Action:**
  1. `POST /api/v1/invitations` for a new email.
  2. Verify 7-day expiration in response.
  3. Attempt invitation for existing org member.
- **Expected:** First succeeds, second returns `400 Bad Request`.
- **Actual:** First succeeded with correct expiry. Second returned 400 with detail: "User with email admin@goaldone.de is already a member of organization System Admin".
- **Result:** ✅ PASSED
