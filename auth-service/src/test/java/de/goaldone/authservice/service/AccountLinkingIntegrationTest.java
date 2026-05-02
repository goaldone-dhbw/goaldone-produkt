package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.*;
import de.goaldone.authservice.dto.AccountLinkingContext;
import de.goaldone.authservice.dto.InvitationLinkingEligibility;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.InvitationRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for account linking flow.
 * Tests the complete scenario from email matching through successful linking
 * and confirmation email sending.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
public class AccountLinkingIntegrationTest {

    @Autowired
    private InvitationManagementService invitationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private VerificationTokenService tokenService;

    @MockitoBean
    private MailService mailService;

    private Company testCompany;
    private User existingUser;
    private String invitationToken;
    private String invitedEmail;

    @BeforeEach
    void setUp() {
        // Clean up previous test data
        invitationRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        // Create test company
        testCompany = Company.builder()
                .name("Test Organization")
                .slug("test-organization")
                .build();
        testCompany = companyRepository.save(testCompany);

        // Create existing user with primary email
        existingUser = User.builder()
                .password("encoded-password")
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        UserEmail primaryEmail = UserEmail.builder()
                .email("primary@example.com")
                .isPrimary(true)
                .verified(true)
                .user(existingUser)
                .build();

        existingUser.addEmail(primaryEmail);
        existingUser = userRepository.save(existingUser);

        // Setup for tests requiring invitation token
        invitedEmail = "secondary@example.com";
        VerificationToken token = tokenService.createToken(invitedEmail, TokenType.INVITATION);
        invitationToken = token.getToken();
    }

    /**
     * Test Case 1: Same-email matching with primary email
     * Existing user with primary email invited → correctly identified
     */
    @Test
    void testSameEmailMatching_PrimaryEmail() {
        String primaryEmail = "primary@example.com";

        // Try to match the primary email
        Optional<User> matchedUser = invitationService.matchInvitedEmailToExistingUser(primaryEmail);

        assertTrue(matchedUser.isPresent(), "Should match existing user by primary email");
        assertEquals(existingUser.getId(), matchedUser.get().getId());
    }

    /**
     * Test Case 2: Secondary-email matching
     * Existing user with secondary email invited → correctly identified
     */
    @Test
    void testSecondaryEmailMatching() {
        // Add secondary email to existing user
        UserEmail secondaryEmail = UserEmail.builder()
                .email("secondary@example.com")
                .isPrimary(false)
                .verified(true)
                .user(existingUser)
                .build();
        existingUser.addEmail(secondaryEmail);
        userRepository.save(existingUser);

        // Try to match the secondary email
        Optional<User> matchedUser = invitationService.matchInvitedEmailToExistingUser("secondary@example.com");

        assertTrue(matchedUser.isPresent(), "Should match existing user by secondary email");
        assertEquals(existingUser.getId(), matchedUser.get().getId());
    }

    /**
     * Test Case 3: New email - no match found
     * Non-existent email invited → no match found, new account path
     */
    @Test
    void testNewEmailNoMatch() {
        Optional<User> matchedUser = invitationService.matchInvitedEmailToExistingUser("newuser@example.com");

        assertFalse(matchedUser.isPresent(), "Should not match any user for new email");
    }

    /**
     * Test Case 4: Linking eligibility check for same-email scenario
     * Email match found → canLink = true
     */
    @Test
    void testLinkingEligibility_SameEmail() {
        // Create invitation for primary email
        String primaryEmail = "primary@example.com";
        VerificationToken token = tokenService.createToken(primaryEmail, TokenType.INVITATION);

        Invitation invitation = Invitation.builder()
                .email(primaryEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        // Check eligibility
        InvitationLinkingEligibility eligibility = invitationService.canAcceptWithLinking(token.getToken());

        assertTrue(eligibility.isTokenValid(), "Token should be valid");
        assertTrue(eligibility.isCanLink(), "Should be eligible for linking");
        assertTrue(eligibility.isEmailMatch(), "Should have email match");
        assertEquals(existingUser.getId(), eligibility.getExistingUserId());
    }

    /**
     * Test Case 5: Linking eligibility check for new-email scenario
     * No email match found → canLink = false but token valid
     */
    @Test
    void testLinkingEligibility_NewEmail() {
        Invitation invitation = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        InvitationLinkingEligibility eligibility = invitationService.canAcceptWithLinking(invitationToken);

        assertTrue(eligibility.isTokenValid(), "Token should be valid");
        assertFalse(eligibility.isCanLink(), "Should not be eligible for linking");
        assertFalse(eligibility.isEmailMatch(), "Should not have email match");
        assertNull(eligibility.getExistingUserId());
    }

    /**
     * Test Case 6: Successful account linking with email addition and role assignment
     * Authenticated user completes acceptance → email added, role granted, no new user
     */
    @Test
    void testSuccessfulAccountLinking() {
        // Create invitation for secondary email
        Invitation invitation = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        // Create linking context
        AccountLinkingContext context = AccountLinkingContext.builder()
                .invitationToken(invitationToken)
                .invitedEmail(invitedEmail)
                .targetOrganizationId(testCompany.getId())
                .targetRole(Role.USER.name())
                .existingUserId(existingUser.getId())
                .linkingTimestamp(LocalDateTime.now())
                .build();

        // Accept invitation with linking
        invitationService.handleAccountLinking(invitation, existingUser, context);

        // Verify email was added as secondary
        User updatedUser = userRepository.findById(existingUser.getId()).orElseThrow();
        assertTrue(updatedUser.getEmails().stream()
                .anyMatch(e -> e.getEmail().equals(invitedEmail)),
                "Should have secondary email added");

        UserEmail newEmail = updatedUser.getEmails().stream()
                .filter(e -> e.getEmail().equals(invitedEmail))
                .findFirst()
                .orElseThrow();
        assertFalse(newEmail.isPrimary(), "Should be secondary email");
        assertTrue(newEmail.isVerified(), "Should be verified");

        // Verify membership created
        assertTrue(membershipRepository.existsByUserIdAndCompanyId(existingUser.getId(), testCompany.getId()),
                "Should have membership in organization");

        // Verify invitation deleted
        assertFalse(invitationRepository.existsById(invitation.getId()),
                "Invitation should be deleted after acceptance");

        // Verify confirmation email sent
        verify(mailService, times(1)).sendAccountLinkingConfirmation(
                eq(invitedEmail),
                anyString(),
                eq(invitedEmail),
                eq(testCompany.getName()),
                eq(Role.USER.name())
        );
    }

    /**
     * Test Case 7: Email already linked to another user → error
     * Prevents linking same email to multiple accounts
     */
    @Test
    void testLinkingPreventsDuplicateSecondaryEmails() {
        // Create another user with the same email already linked
        User otherUser = User.builder()
                .password("encoded-password")
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        UserEmail otherEmail = UserEmail.builder()
                .email(invitedEmail)
                .isPrimary(true)
                .verified(true)
                .user(otherUser)
                .build();

        otherUser.addEmail(otherEmail);
        userRepository.save(otherUser);

        // Create invitation
        Invitation invitation = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        // Create linking context for different user
        AccountLinkingContext context = AccountLinkingContext.builder()
                .invitationToken(invitationToken)
                .invitedEmail(invitedEmail)
                .targetOrganizationId(testCompany.getId())
                .targetRole(Role.USER.name())
                .existingUserId(existingUser.getId())
                .linkingTimestamp(LocalDateTime.now())
                .build();

        // Try to link - should fail
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.handleAccountLinking(invitation, existingUser, context),
                "Should prevent linking email already associated with another account");
    }

    /**
     * Test Case 8: Unauthenticated linking attempt → error
     * User ID mismatch validation
     */
    @Test
    void testLinkingRequiresMatchingUser() {
        // Create another user
        User anotherUser = User.builder()
                .password("encoded-password")
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        UserEmail email = UserEmail.builder()
                .email("another@example.com")
                .isPrimary(true)
                .verified(true)
                .user(anotherUser)
                .build();

        anotherUser.addEmail(email);
        anotherUser = userRepository.save(anotherUser);

        // Create invitation
        Invitation invitation = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        // Create linking context for different user
        AccountLinkingContext context = AccountLinkingContext.builder()
                .invitationToken(invitationToken)
                .invitedEmail(invitedEmail)
                .targetOrganizationId(testCompany.getId())
                .targetRole(Role.USER.name())
                .existingUserId(anotherUser.getId()) // Different user ID
                .linkingTimestamp(LocalDateTime.now())
                .build();

        // Try to link as existingUser but context is for anotherUser - should fail
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.handleAccountLinking(invitation, existingUser, context),
                "Should prevent user from linking to wrong account");
    }

    /**
     * Test Case 9: Role assignment from linking context
     * Confirms that role from context is assigned correctly
     */
    @Test
    void testRoleAssignmentFromContext() {
        Invitation invitation = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        // Create context with COMPANY_ADMIN role
        AccountLinkingContext context = AccountLinkingContext.builder()
                .invitationToken(invitationToken)
                .invitedEmail(invitedEmail)
                .targetOrganizationId(testCompany.getId())
                .targetRole(Role.COMPANY_ADMIN.name())
                .existingUserId(existingUser.getId())
                .linkingTimestamp(LocalDateTime.now())
                .build();

        invitationService.handleAccountLinking(invitation, existingUser, context);

        // Verify membership has correct role
        Membership membership = membershipRepository.findAll().stream()
                .filter(m -> m.getUser().getId().equals(existingUser.getId())
                        && m.getCompany().getId().equals(testCompany.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(Role.COMPANY_ADMIN, membership.getRole(),
                "Membership should have role from context");
    }

    /**
     * Test Case 10: Idempotency - linking same email twice
     * Second linking should be idempotent or error gracefully
     */
    @Test
    void testIdempotentLinkingOfSameEmail() {
        Invitation invitation1 = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation1);

        AccountLinkingContext context = AccountLinkingContext.builder()
                .invitationToken(invitationToken)
                .invitedEmail(invitedEmail)
                .targetOrganizationId(testCompany.getId())
                .targetRole(Role.USER.name())
                .existingUserId(existingUser.getId())
                .linkingTimestamp(LocalDateTime.now())
                .build();

        // First linking
        invitationService.handleAccountLinking(invitation1, existingUser, context);

        // Verify email was added
        User updatedUser = userRepository.findById(existingUser.getId()).orElseThrow();
        int emailCountAfterFirst = (int) updatedUser.getEmails().stream()
                .filter(e -> e.getEmail().equals(invitedEmail))
                .count();
        assertEquals(1, emailCountAfterFirst, "Should have one instance of the email");

        // Try to link same email again (with different invitation)
        Invitation invitation2 = Invitation.builder()
                .email(invitedEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation2);

        VerificationToken token2 = tokenService.createToken(invitedEmail, TokenType.INVITATION);
        AccountLinkingContext context2 = AccountLinkingContext.builder()
                .invitationToken(token2.getToken())
                .invitedEmail(invitedEmail)
                .targetOrganizationId(testCompany.getId())
                .targetRole(Role.USER.name())
                .existingUserId(existingUser.getId())
                .linkingTimestamp(LocalDateTime.now())
                .build();

        // Second linking should be idempotent
        invitationService.handleAccountLinking(invitation2, existingUser, context2);

        // Verify email still appears only once
        User updatedUser2 = userRepository.findById(existingUser.getId()).orElseThrow();
        int emailCountAfterSecond = (int) updatedUser2.getEmails().stream()
                .filter(e -> e.getEmail().equals(invitedEmail))
                .count();
        assertEquals(1, emailCountAfterSecond, "Should still have only one instance of the email (idempotent)");
    }
}
