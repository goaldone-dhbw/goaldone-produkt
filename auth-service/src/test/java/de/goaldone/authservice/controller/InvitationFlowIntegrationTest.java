package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.*;
import de.goaldone.authservice.dto.InvitationFlowRoute;
import de.goaldone.authservice.repository.*;
import de.goaldone.authservice.service.InvitationManagementService;
import de.goaldone.authservice.service.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for invitation flow paths (new account and linking).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class InvitationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private InvitationManagementService invitationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Company testCompany;
    private User existingUser;
    private String existingUserEmail = "existing@example.com";
    private String newUserEmail = "newuser@example.com";

    @BeforeEach
    public void setup() {
        // Clean up
        invitationRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        // Create test company
        testCompany = Company.builder()
                .name("Test Organization")
                .slug("test-organization-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                .build();
        testCompany = companyRepository.save(testCompany);

        // Create existing user
        existingUser = User.builder()
                .password(passwordEncoder.encode("SecurePassword123!"))
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        UserEmail primaryEmail = UserEmail.builder()
                .email(existingUserEmail)
                .isPrimary(true)
                .verified(true)
                .user(existingUser)
                .build();

        existingUser.addEmail(primaryEmail);
        existingUser = userRepository.save(existingUser);
    }

    @Test
    public void testNewAccountFlowTokenValidation() throws Exception {
        // Test Case 1: New email invitation -> landing page shows "create account" form
        Invitation invitation = Invitation.builder()
                .email(newUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitation = invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(newUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/api/v1/invitations/{token}/status", token.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("PENDING")))
                .andExpect(jsonPath("$.invitedEmail", equalTo(newUserEmail)))
                .andExpect(jsonPath("$.emailMatch.found", is(false)));
    }

    @Test
    public void testLinkingFlowEmailDetection() throws Exception {
        // Test Case 2: Token for existing email -> landing page shows "link account" option
        Invitation invitation = Invitation.builder()
                .email(existingUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitation = invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(existingUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/api/v1/invitations/{token}/status", token.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("PENDING")))
                .andExpect(jsonPath("$.emailMatch.found", is(true)))
                .andExpect(jsonPath("$.emailMatch.userId", notNullValue()));
    }

    @Test
    public void testInvalidTokenValidation() throws Exception {
        // Test Case 3: Invalid token -> 400 Bad Request
        mockMvc.perform(get("/api/v1/invitations/invalid-token-12345/status"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status", equalTo("EXPIRED")));
    }

    @Test
    public void testExpiredTokenHandling() throws Exception {
        // Test Case 4: Token with past expiration -> 410 Gone
        Invitation invitation = Invitation.builder()
                .email(newUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(newUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/api/v1/invitations/{token}/status", token.getToken()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status", equalTo("EXPIRED")));
    }

    @Test
    public void testAlreadyAcceptedInvitation() throws Exception {
        // Test Case 5: Reuse accepted token -> 409 Conflict
        Invitation invitation = Invitation.builder()
                .email(newUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .acceptanceReason("NEW_ACCOUNT")
                .linkedUserId(existingUser.getId())
                .linkingTimestamp(LocalDateTime.now())
                .build();
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(newUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/api/v1/invitations/{token}/status", token.getToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")));
    }

    @Test
    public void testFlowRoutingNewAccount() throws Exception {
        // Test Case 6: Flow routing for new account
        Invitation invitation = Invitation.builder()
                .email(newUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(newUserEmail, TokenType.INVITATION);

        InvitationFlowRoute route = invitationService.routeAcceptanceFlow(token.getToken(), newUserEmail);

        assertThat(route.getRecommendedFlow()).isEqualTo("NEW_ACCOUNT");
        assertThat(route.getEmailMatch().isFound()).isFalse();
        assertThat(route.getOrganization().getId()).isEqualTo(testCompany.getId());
    }

    @Test
    public void testFlowRoutingLinking() throws Exception {
        // Test Case 7: Flow routing for account linking
        Invitation invitation = Invitation.builder()
                .email(existingUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(existingUserEmail, TokenType.INVITATION);

        InvitationFlowRoute route = invitationService.routeAcceptanceFlow(token.getToken(), existingUserEmail);

        assertThat(route.getRecommendedFlow()).isEqualTo("ACCOUNT_LINKING");
        assertThat(route.getEmailMatch().isFound()).isTrue();
        assertThat(route.getEmailMatch().getUserId()).isEqualTo(existingUser.getId());
    }

    @Test
    public void testStatusEndpointResponse() throws Exception {
        // Test Case 8: Status endpoint returns correct structure
        Invitation invitation = Invitation.builder()
                .email(newUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(newUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/api/v1/invitations/{token}/status", token.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.invitedEmail", notNullValue()))
                .andExpect(jsonPath("$.organizationId", notNullValue()))
                .andExpect(jsonPath("$.organizationName", notNullValue()))
                .andExpect(jsonPath("$.expirationDate", notNullValue()))
                .andExpect(jsonPath("$.emailMatch", notNullValue()));
    }

    @Test
    public void testInvitationStatusUpdate() throws Exception {
        // Test Case 9: Invitation status updates correctly after acceptance
        Invitation invitation = Invitation.builder()
                .email(existingUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitation = invitationRepository.save(invitation);

        // Mark as accepted
        invitation.markAsAcceptedWithLinking(existingUser);
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(existingUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/api/v1/invitations/{token}/status", token.getToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.emailMatch.userId", equalTo(existingUser.getId().toString())));
    }

    @Test
    @WithMockUser
    public void testInvitationLandingPageNewAccount() throws Exception {
        // Test Case 10: Landing page for new account flow
        Invitation invitation = Invitation.builder()
                .email(newUserEmail)
                .company(testCompany)
                .inviterId(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        VerificationToken token = tokenService.createToken(newUserEmail, TokenType.INVITATION);

        mockMvc.perform(get("/invitations/{token}", token.getToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/invitation-landing"))
                .andExpect(model().attributeExists("token", "email", "flowRoute", "recommendedFlow"));
    }

    @Test
    public void testErrorPageDisplay() throws Exception {
        // Test Case: Error page shows for invalid tokens
        mockMvc.perform(get("/invitations/invalid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/invitation-error"))
                .andExpect(model().attributeExists("errorCode", "message"));
    }
}
