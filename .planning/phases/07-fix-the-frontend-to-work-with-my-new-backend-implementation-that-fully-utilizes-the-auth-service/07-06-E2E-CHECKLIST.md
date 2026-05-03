# Phase 07-06 Manual E2E Testing Checklist

**Date:** 2026-05-04
**Tester:** [Tester Name]
**Status:** PARTIAL — Automated checks PASS, browser testing PENDING

## Automated Verification Results

| Check | Status | Result |
|-------|--------|--------|
| `npm run test:ci` | ✅ VERIFIED | 100/100 tests passing (0 failures) |
| `npm run build` | ✅ VERIFIED | 0 TypeScript errors (1 pre-existing bundle size warning) |

---

## Task 1: Prepare Test Environment

| Item | Status | Notes |
|------|--------|-------|
| Backend running (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`) | ⏳ PENDING LIVE TESTING | Start backend on port 8080 |
| Auth-service running and accessible | ⏳ PENDING LIVE TESTING | Verify at `http://localhost:9000` |
| Auth-service OIDC discovery endpoint responds | ⏳ PENDING LIVE TESTING | `http://localhost:9000/.well-known/openid-configuration` |
| Frontend dev server running (`npm start`) | ⏳ PENDING LIVE TESTING | Port 4200 |
| Frontend accessible at `http://localhost:4200` | ⏳ PENDING LIVE TESTING | Login page or redirect |
| Backend API calls succeed (DevTools → Network) | ⏳ PENDING LIVE TESTING | 200 OK on protected endpoints |

---

## Task 2: Single-Org Login Flow

| Item | Status | Notes |
|------|--------|-------|
| Log out or clear storage | ⏳ PENDING LIVE TESTING | |
| Navigate to `http://localhost:4200` → redirects to login | ⏳ PENDING LIVE TESTING | |
| Click login → redirects to auth-service issuer | ⏳ PENDING LIVE TESTING | e.g., `http://localhost:9000` |
| Login with single-org test user | ⏳ PENDING LIVE TESTING | Email: test-single-org@example.com |
| Redirect back to `http://localhost:4200` (no redirect loop) | ⏳ PENDING LIVE TESTING | |
| User can access protected pages | ⏳ PENDING LIVE TESTING | |
| No 401/403 errors in browser console | ⏳ PENDING LIVE TESTING | DevTools → Console |
| No token decoding errors in console | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** Login completes without redirect loop, no auth-related console errors.

---

## Task 3: Single-Org User UI (No Org Dropdown)

| Item | Status | Notes |
|------|--------|-------|
| Open Add Task dialog | ⏳ PENDING LIVE TESTING | |
| Org dropdown NOT visible (user is in 1 org) | ⏳ PENDING LIVE TESTING | |
| Open Add Worktime dialog | ⏳ PENDING LIVE TESTING | |
| Org dropdown NOT visible in Worktime dialog | ⏳ PENDING LIVE TESTING | |
| Navigate to Company Settings | ⏳ PENDING LIVE TESTING | |
| Org dropdown NOT visible in Settings (not multi-org admin) | ⏳ PENDING LIVE TESTING | |
| All dialogs open/close without errors | ⏳ PENDING LIVE TESTING | |
| Can create task in default org | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** Org dropdown hidden for single-org user, default org used transparently.

---

## Task 4: Multi-Org Login and Org Context

| Item | Status | Notes |
|------|--------|-------|
| Log out current user | ⏳ PENDING LIVE TESTING | |
| Log in with multi-org test user (2+ orgs) | ⏳ PENDING LIVE TESTING | Email: test-multi-org@example.com |
| `authService.getOrganizations()` returns 2+ orgs (console) | ⏳ PENDING LIVE TESTING | |
| `orgContextService.getActiveOrganization()` returns first org | ⏳ PENDING LIVE TESTING | |
| `orgContextService.getDialogOrg()` returns null (no dialog open) | ⏳ PENDING LIVE TESTING | |
| `orgContextService.getSettingsOrg()` returns null (not in settings) | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** Multi-org user logs in, all orgs from JWT loaded, first org selected as default.

---

## Task 5: Multi-Org Add Task Dialog

| Item | Status | Notes |
|------|--------|-------|
| Click Add Task → dialog opens | ⏳ PENDING LIVE TESTING | |
| Org dropdown IS visible (user has 2+ orgs) | ⏳ PENDING LIVE TESTING | |
| Dropdown shows all user's organizations (slug/name) | ⏳ PENDING LIVE TESTING | |
| Selecting different org updates `orgContextService.getDialogOrg()` | ⏳ PENDING LIVE TESTING | |
| Fill in task title and required fields | ⏳ PENDING LIVE TESTING | |
| Click save → task created (no 403 error) | ⏳ PENDING LIVE TESTING | |
| Close dialog → `orgContextService.getDialogOrg()` returns null | ⏳ PENDING LIVE TESTING | Org context cleared |

**Success Criteria:** Org dropdown visible, selection changes context, task created in selected org, context cleared on close.

---

## Task 6: Multi-Org Add Worktime Dialog

| Item | Status | Notes |
|------|--------|-------|
| Click Add Working Time → dialog opens | ⏳ PENDING LIVE TESTING | |
| Org dropdown IS visible (user has 2+ orgs) | ⏳ PENDING LIVE TESTING | |
| Org selection works correctly | ⏳ PENDING LIVE TESTING | |
| Worktime created in selected org (no 403 error) | ⏳ PENDING LIVE TESTING | |
| Close dialog → org context cleared | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** Org dropdown visible, worktime created in selected org, org context properly managed.

---

## Task 7: Multi-Org Settings Page

| Item | Status | Notes |
|------|--------|-------|
| Click Company Settings → navigate to settings page | ⏳ PENDING LIVE TESTING | |
| Org dropdown IS visible (user is admin in 2+ orgs) | ⏳ PENDING LIVE TESTING | Only COMPANY_ADMIN orgs |
| Selecting org updates `orgContextService.getSettingsOrg()` | ⏳ PENDING LIVE TESTING | |
| Member list loads for selected org | ⏳ PENDING LIVE TESTING | |
| X-Org-ID header present in API requests (DevTools → Network) | ⏳ PENDING LIVE TESTING | |
| Selecting different org → previous list replaced | ⏳ PENDING LIVE TESTING | |
| Navigate away from settings → `orgContextService.getSettingsOrg()` returns null | ⏳ PENDING LIVE TESTING | Context cleared |
| No org context leaks to other pages | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** Org dropdown visible for admin, settings page maintains context, cleared on navigation.

---

## Task 8: Member Management — Invite

| Item | Status | Notes |
|------|--------|-------|
| Open Invite Member dialog in Settings | ⏳ PENDING LIVE TESTING | |
| Fill invitee email, role (USER or COMPANY_ADMIN) | ⏳ PENDING LIVE TESTING | |
| Click Send → success toast appears | ⏳ PENDING LIVE TESTING | |
| Member appears in member list with status "INVITED" | ⏳ PENDING LIVE TESTING | |
| New member shows correct role | ⏳ PENDING LIVE TESTING | |
| Try to invite already-member → 409 CONFLICT error appears | ⏳ PENDING LIVE TESTING | "Already a member" message |

**Success Criteria:** Invite sent, member shows INVITED status, conflict error handled correctly.

---

## Task 9: Member Management — Role Change

| Item | Status | Notes |
|------|--------|-------|
| Find active member in member list | ⏳ PENDING LIVE TESTING | |
| Click role change button/action | ⏳ PENDING LIVE TESTING | |
| Select different role (USER ↔ COMPANY_ADMIN) | ⏳ PENDING LIVE TESTING | |
| Confirm change → success message appears | ⏳ PENDING LIVE TESTING | |
| Member's role updates in list | ⏳ PENDING LIVE TESTING | |
| No permission errors (user is admin) | ⏳ PENDING LIVE TESTING | |
| Try to demote last admin → 409 CONFLICT error | ⏳ PENDING LIVE TESTING | "Cannot demote last admin" |

**Success Criteria:** Role change succeeds, list updates, last-admin constraint enforced.

---

## Task 10: Member Management — Removal

| Item | Status | Notes |
|------|--------|-------|
| Find member to remove in member list | ⏳ PENDING LIVE TESTING | |
| Click remove/delete action | ⏳ PENDING LIVE TESTING | |
| Confirm removal | ⏳ PENDING LIVE TESTING | |
| Success message appears | ⏳ PENDING LIVE TESTING | |
| Member removed from list | ⏳ PENDING LIVE TESTING | |
| Try to remove self → error or button disabled | ⏳ PENDING LIVE TESTING | Cannot remove self |
| Try to remove last admin → 409 CONFLICT error | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** Removal succeeds, list updates, last-admin constraint enforced.

---

## Task 11: Error Scenario Testing

| Item | Status | Notes |
|------|--------|-------|
| Log in as non-admin user | ⏳ PENDING LIVE TESTING | |
| Try to invite member (403 expected) | ⏳ PENDING LIVE TESTING | |
| 403 error shows: "You don't have permission..." | ⏳ PENDING LIVE TESTING | Toast notification |
| Try to invite already-member → 409 CONFLICT | ⏳ PENDING LIVE TESTING | |
| 409 error shows appropriate message | ⏳ PENDING LIVE TESTING | Toast notification |
| Use expired invitation link → 410 GONE (if applicable) | ⏳ PENDING LIVE TESTING | "This link has expired..." |
| Turn on offline mode (DevTools → Network → offline) | ⏳ PENDING LIVE TESTING | |
| Try to perform operation → network error shown | ⏳ PENDING LIVE TESTING | "Unable to connect..." |
| Restore network | ⏳ PENDING LIVE TESTING | |
| All errors appear as toast notifications | ⏳ PENDING LIVE TESTING | |
| No technical error details exposed to user | ⏳ PENDING LIVE TESTING | |

**Success Criteria:** All error types show appropriate user-friendly messages in toasts.

---

## Task 12: Technical Verification (Automated) ✅

| Item | Status | Result |
|------|--------|--------|
| `npm run test:ci` — all tests pass | ✅ VERIFIED | 100/100 tests (0 failures) |
| `npm run build` — 0 TypeScript errors | ✅ VERIFIED | Clean build (1 bundle size warning, pre-existing) |

**Full test output:**
```
testsuites name="vitest tests" tests="100" failures="0" errors="0" time="4.6477906"
```

**Build output:** Application bundle generation complete — no errors.

---

## Summary

### ✅ Automated Items Verified

- **Tests:** 100/100 passing
- **Build:** 0 TypeScript errors

### ⏳ Pending Live Browser Testing

The following workflows require running services (auth-service, backend, frontend dev server) and a real browser to verify:

1. Complete OIDC login flow (auth-service → JWT → frontend)
2. Single-org user UI (no org dropdown visible)
3. Multi-org user UI (org dropdown in dialogs, context management)
4. Member management operations (invite, role change, remove)
5. Error handling toast notifications (403, 409, 410, network errors)
6. Token refresh behavior (5-minute buffer)
7. Console clean (no auth-related errors or warnings)
8. X-Org-ID header presence in network requests

### Issues Found During Automated Testing

None — all 100 tests pass, build is clean.

### Overall Status

⏳ **PARTIALLY COMPLETE** — Automated verification PASSED. Browser testing PENDING LIVE TESTING.

Once live testing is complete, update status to:
- **PASS** — Ready for deployment
- **FAIL** — Needs fixes (list specific issues above)
