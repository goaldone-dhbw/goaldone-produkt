# Invitation Flow UX Testing Checklist

**Version:** 1.0  
**Date:** 2026-05-01  
**Scope:** Manual end-to-end testing for Phase 5.4 Invitation Management

---

## Testing Scenarios

### Scenario 1: New Email Invitation

Testing a brand-new user receiving and accepting an invitation.

**Setup:**
- Invite a user with email not in the system (e.g., test-new@example.com)
- Extract token from invitation email

**Steps:**
- [ ] Click invitation link from email
- [ ] Landing page loads successfully
- [ ] Page displays "Create Account" form or flow prompt
- [ ] Email address is pre-filled and correct
- [ ] Password fields are visible with rules displayed
- [ ] Set password with valid criteria (8+ chars, mix of upper/lower/numbers)
  - [ ] Test password too short (should show error)
  - [ ] Test weak password (should show error)
  - [ ] Test strong password (should be accepted)
- [ ] Confirm password matches
- [ ] Submit form
- [ ] Redirected to dashboard or success page
- [ ] User created in database with:
  - [ ] Email as primary email address
  - [ ] Email marked as verified
  - [ ] Status is ACTIVE
  - [ ] No duplicate users created
- [ ] User automatically logged in (or redirected to login)
- [ ] Organization membership created with USER role
- [ ] Can access organization resources immediately

**Expected Outcome:**
- User successfully onboarded with new account
- Single user record created
- Organization membership established
- Session created with organization context

---

### Scenario 2: Existing User with Matching Primary Email

Testing an existing user with the same email as invitation.

**Setup:**
- Create user: existing@example.com with password
- Invite same email to same organization
- Extract token from invitation email

**Steps:**
- [ ] User logs in with existing credentials (existing@example.com / password)
- [ ] Click invitation link
- [ ] Landing page detects existing user
- [ ] Page shows "Link Account" option or similar
- [ ] User is already authenticated
- [ ] Click "Accept with existing credentials" or "Confirm"
- [ ] Redirected to acceptance/confirmation page
- [ ] Confirm linking action
- [ ] Redirected to dashboard or success page
- [ ] User record unchanged (no new user created)
- [ ] Organization membership added
  - [ ] User now member of organization with USER role
  - [ ] Email linked to organization (confirm in database)
- [ ] Session updated with new organization context
- [ ] User can access new organization resources
- [ ] Other org memberships still valid

**Expected Outcome:**
- Existing user added to new organization
- No new user created
- Email remains linked to original user
- Multi-organization context established

---

### Scenario 3: Existing User with Secondary Email

Testing user receiving invitation for a secondary email address.

**Setup:**
- Create user: primary@example.com
- Add secondary email: secondary@example.com to user
- Invite secondary@example.com to organization
- Extract token from invitation email

**Steps:**
- [ ] System detects email match (secondary email)
- [ ] Landing page shows "Link Account" option
- [ ] User logs in or is already authenticated
- [ ] Click "Accept"
- [ ] System recognizes this is account linking scenario
- [ ] Confirmation shows both email addresses
- [ ] Confirm linking
- [ ] Organization membership created
- [ ] Both email addresses verified in organization context
- [ ] User can access organization
- [ ] Secondary email remains secondary (not promoted to primary)

**Expected Outcome:**
- Secondary email successfully linked to organization
- Original user structure unchanged
- No new user created
- Organization membership established
- Email association preserved

---

### Scenario 4: Expired Token

Testing user with expired invitation link.

**Setup:**
- Create invitation
- Modify invitation expiration in database to yesterday
- Extract token

**Steps:**
- [ ] Click expired invitation link
- [ ] Error page loads with clear message
- [ ] Error message in German: "Die Einladung ist abgelaufen"
- [ ] Message explains invitation has expired
- [ ] "Request new invitation" CTA visible
- [ ] Contact information provided
- [ ] CTA links to organization admin or support
- [ ] No error stack traces visible

**Expected Outcome:**
- User presented with helpful error message
- Clear path to resolve (request new invitation)
- No information leakage
- Professional error UX

---

### Scenario 5: Already Accepted Invitation

Testing user clicking accepted invitation link again.

**Setup:**
- Create invitation
- Accept the invitation (create membership, mark as accepted)
- Retrieve token again

**Steps:**
- [ ] Click same invitation link again
- [ ] Status page/message loads
- [ ] Message shows "You already accepted this invitation" or similar
- [ ] No new membership created (confirm in DB)
- [ ] No new user created
- [ ] Option to go to organization dashboard
- [ ] User is logged in and can access organization

**Expected Outcome:**
- Idempotent operation
- No duplicate memberships
- User informed of already-accepted status
- Graceful handling of reuse

---

### Scenario 6: Invalid Token Format

Testing user with malformed token.

**Setup:**
- Manually modify URL with invalid token format
- Use gibberish or incomplete token

**Steps:**
- [ ] Click modified URL with invalid token
- [ ] Error page loads immediately
- [ ] Message indicates token is invalid
- [ ] Message in German
- [ ] User guided to request new invitation
- [ ] No server errors exposed

**Expected Outcome:**
- Graceful error handling
- User-friendly message
- No information disclosure

---

### Scenario 7: Mobile Responsiveness

Testing invitation flow on mobile devices.

**Setup:**
- Open invitation link on mobile device (iOS/Android)
- Test with various screen sizes

**Steps:**
- [ ] Landing page displays correctly on mobile
- [ ] Forms are touch-friendly (no horizontal scrolling)
- [ ] Password input shows eye icon toggle for visibility
- [ ] Buttons are large enough for finger tapping
- [ ] Text is readable without zooming
- [ ] Form submission works on mobile
- [ ] Success page renders properly
- [ ] Navigation back works as expected

**Expected Outcome:**
- Fully responsive invitation flow
- Good mobile UX
- No broken layouts
- Touch-friendly interactions

---

### Scenario 8: Email Validation

Testing email field validation in new account scenario.

**Setup:**
- New invitation for test@example.com

**Steps:**
- [ ] Landing page shows pre-filled email (correct)
- [ ] Email field is read-only or disabled (cannot change)
- [ ] Email format is clearly displayed
- [ ] No ability to change email during signup
- [ ] Confirmation page shows correct email

**Expected Outcome:**
- Email from invitation cannot be modified
- Prevents phishing/typo issues
- Clear email confirmation

---

### Scenario 9: Password Validation Rules

Testing password strength requirements.

**Setup:**
- New account invitation

**Steps:**
- [ ] Password requirements displayed (in German)
- [ ] Minimum length requirement shown (e.g., 8 chars)
- [ ] Special character requirements (if any) shown
- [ ] Real-time validation as user types
  - [ ] Too short -> red error
  - [ ] Missing caps -> red error
  - [ ] Missing numbers -> red error
  - [ ] All criteria met -> green checkmark
- [ ] Submit button disabled until valid password
- [ ] Passwords must match (confirm password)
- [ ] Mismatch -> clear error message

**Expected Outcome:**
- Clear, real-time feedback
- User guided to strong password
- Submit blocked for invalid input
- Good UX for password creation

---

### Scenario 10: Concurrent Sessions

Testing user already logged in to another org.

**Setup:**
- User is logged in to Organization A
- Send invitation to Organization B

**Steps:**
- [ ] User clicks invitation link while logged in
- [ ] System detects existing session
- [ ] Landing page shows "Link Account" option
- [ ] User can accept linking without logging out
- [ ] After acceptance, user has access to both orgs
- [ ] Session maintains both org contexts
- [ ] User can switch between orgs

**Expected Outcome:**
- Multi-org sessions work correctly
- User not forced to logout
- Context switching works
- No session conflicts

---

### Scenario 11: Error Recovery

Testing user's ability to recover from errors.

**Setup:**
- New account invitation

**Steps:**
- [ ] Enter mismatched passwords
- [ ] Click submit
- [ ] Error message displayed clearly
- [ ] Form not cleared (user can fix)
- [ ] Error message helpful (not generic)
- [ ] Go back in browser
- [ ] Invitation link still works
- [ ] Try again with correct password
- [ ] Success

**Expected Outcome:**
- Non-destructive error handling
- Clear error messages
- User can retry easily

---

### Scenario 12: Network Error Handling

Testing behavior when submission fails.

**Setup:**
- New account invitation
- Simulate network error (network tab in browser dev tools)

**Steps:**
- [ ] Submit form while offline/with network error
- [ ] Error message shown to user
- [ ] Form state preserved (data not lost)
- [ ] Retry button available
- [ ] Once network restored, retry works
- [ ] Form clears on final success

**Expected Outcome:**
- Resilient error handling
- User doesn't lose form data
- Clear path to retry

---

## Test Data

### Test Users
- **New User:** test-new@example.com (no prior account)
- **Existing User:** existing@example.com (primary email)
- **Secondary Email User:** primary@secondary-test.com (with secondary@secondary-test.com)

### Test Organizations
- **Organization A:** test-org-a
- **Organization B:** test-org-b

### Test Invitations
- Generate fresh tokens for each scenario
- Verify token expiration dates
- Document token values for manual testing

---

## Localization Checklist (German)

Verify all pages are in German:

- [ ] Landing page title and headings in German
- [ ] Form labels in German
- [ ] Error messages in German
- [ ] Success messages in German
- [ ] Button text in German
- [ ] Help text in German
- [ ] No English text visible
- [ ] Special characters (ä, ö, ü, ß) render correctly

---

## Performance Checklist

- [ ] Landing page loads in under 2 seconds
- [ ] Form submission completes in under 3 seconds
- [ ] No console errors or warnings
- [ ] No memory leaks in browser dev tools
- [ ] Mobile page loads in under 4 seconds

---

## Accessibility Checklist

- [ ] Form labels properly associated with inputs
- [ ] Error messages announced to screen readers
- [ ] Focus management works (keyboard navigation)
- [ ] Color contrast sufficient for readability
- [ ] No reliance on color alone for errors
- [ ] Tab order logical and intuitive

---

## Sign-Off

| Role | Name | Date | Status |
|------|------|------|--------|
| QA | _____ | _____ | _____ |
| Product | _____ | _____ | _____ |
| Dev | _____ | _____ | _____ |

---

## Notes

- Test in Chrome, Firefox, Safari (desktop and mobile)
- Test with and without JavaScript enabled
- Test with browser autofill enabled/disabled
- Clear browser cache between scenarios
- Check database state after each scenario
- Review audit logs for all scenarios

---

*Testing Document: 05-invitation-ux-testing.md*  
*Phase: 05-advanced-features-refinement*  
*Plan: 05-04-PLAN.md*
