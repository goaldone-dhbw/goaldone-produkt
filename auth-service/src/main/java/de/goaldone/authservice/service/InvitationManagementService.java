package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.*;
import de.goaldone.authservice.dto.*;
import de.goaldone.authservice.exception.InvitationInvalidTokenException;
import de.goaldone.authservice.exception.InvitationTokenExpiredException;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.InvitationRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationManagementService {

    private final InvitationRepository invitationRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public InvitationResponse createInvitation(InvitationRequest request) {
        log.info("Creating invitation for email: {} in company: {}", request.getEmail(), request.getCompanyId());

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id: " + request.getCompanyId()));

        // Check if user with this email is already a member of the company
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (membershipRepository.existsByUserIdAndCompanyId(user.getId(), company.getId())) {
                throw new IllegalArgumentException("User with email " + request.getEmail() + " is already a member of organization " + company.getName());
            }
        });

        Invitation invitation = Invitation.builder()
                .email(request.getEmail())
                .company(company)
                .inviterId(request.getInviterId())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .role(request.getRole())
                .build();

        Invitation saved = invitationRepository.save(invitation);

        // Create Verification Token
        VerificationToken token = tokenService.createToken(request.getEmail(), TokenType.INVITATION);

        // Send Email
        String inviteUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/invitation")
                .queryParam("token", token.getToken())
                .toUriString();
        
        mailService.sendInvitation(request.getEmail(), inviteUrl, company.getName());

        return mapToResponse(saved);
    }

    @Transactional
    public void acceptInvitation(String tokenValue, User user, AccountLinkingContext linkingContext) {
        String email = tokenService.verifyToken(tokenValue, TokenType.INVITATION)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token"));

        // Find the invitation for this email
        Invitation invitation = invitationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new EntityNotFoundException("No invitation found for email: " + email));

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invitation has expired");
        }

        // Check if this is a linking scenario
        if (linkingContext != null && linkingContext.isValid()) {
            handleAccountLinking(invitation, user, linkingContext);
        } else {
            // Standard acceptance path - create membership without email linking
            Membership membership = Membership.builder()
                    .user(user)
                    .company(invitation.getCompany())
                    .role(Role.USER)
                    .build();

            membershipRepository.save(membership);
            invitationRepository.delete(invitation);

            log.info("User {} accepted invitation to company {}", user.getEmails(), invitation.getCompany().getName());
        }
    }

    /**
     * Legacy method signature for backward compatibility
     */
    @Transactional
    public void acceptInvitation(String tokenValue, User user) {
        acceptInvitation(tokenValue, user, null);
    }

    /**
     * Handle account linking acceptance.
     * Adds the invited email as secondary email, assigns role, and creates membership.
     *
     * @param invitation the invitation being accepted
     * @param authenticatedUser the user accepting (must match linking context)
     * @param linkingContext the account linking context
     */
    @Transactional
    public void handleAccountLinking(Invitation invitation, User authenticatedUser, AccountLinkingContext linkingContext) {
        log.info("Processing account linking for user {} with email {}",
                authenticatedUser.getId(), linkingContext.getInvitedEmail());

        // Verify authenticated user matches the context
        if (!authenticatedUser.getId().equals(linkingContext.getExistingUserId())) {
            log.warn("Account linking validation failed: authenticated user {} does not match context user {}",
                    authenticatedUser.getId(), linkingContext.getExistingUserId());
            throw new IllegalArgumentException("Authenticated user does not match linking context");
        }

        String invitedEmail = linkingContext.getInvitedEmail();

        // Check if email is already attached to another user
        Optional<User> existingEmailUser = userRepository.findByEmail(invitedEmail);
        if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(authenticatedUser.getId())) {
            log.warn("Email {} is already attached to another user", invitedEmail);
            throw new IllegalArgumentException("Email is already associated with another account");
        }

        // Add invited email as secondary email if not already present
        boolean emailExists = authenticatedUser.getEmails().stream()
                .anyMatch(e -> e.getEmail().equalsIgnoreCase(invitedEmail));

        if (!emailExists) {
            UserEmail secondaryEmail = UserEmail.builder()
                    .email(invitedEmail)
                    .isPrimary(false)
                    .verified(true)
                    .user(authenticatedUser)
                    .build();
            authenticatedUser.addEmail(secondaryEmail);
            log.debug("Added secondary email {} to user {}", invitedEmail, authenticatedUser.getId());
        } else {
            log.debug("Email {} already exists for user {}", invitedEmail, authenticatedUser.getId());
        }

        // Determine role from linking context or default to USER
        Role assignedRole = Role.USER;
        if (linkingContext.getTargetRole() != null && !linkingContext.getTargetRole().isEmpty()) {
            try {
                assignedRole = Role.valueOf(linkingContext.getTargetRole());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role in linking context: {}, using USER", linkingContext.getTargetRole());
            }
        }

        // Create membership with assigned role
        Membership membership = Membership.builder()
                .user(authenticatedUser)
                .company(invitation.getCompany())
                .role(assignedRole)
                .build();

        membershipRepository.save(membership);

        // Save user with new email
        userRepository.save(authenticatedUser);

        // Mark invitation as accepted
        invitationRepository.delete(invitation);

        // Send account linking confirmation email
        try {
            String userName = authenticatedUser.getEmails().stream()
                    .filter(UserEmail::isPrimary)
                    .findFirst()
                    .map(UserEmail::getEmail)
                    .orElse("User");

            mailService.sendAccountLinkingConfirmation(
                    invitedEmail,
                    userName,
                    invitedEmail,
                    invitation.getCompany().getName(),
                    assignedRole.name()
            );
            log.info("Account linking confirmation email sent to {}", invitedEmail);
        } catch (Exception e) {
            log.error("Failed to send account linking confirmation email to {}", invitedEmail, e);
            // Don't fail the linking operation if email send fails
        }

        log.info("Successfully linked email {} to user {} in organization {} with role {}",
                invitedEmail, authenticatedUser.getId(), invitation.getCompany().getId(), assignedRole);
    }

    @Transactional
    public User activateUser(String tokenValue, String password) {
        String email = tokenService.verifyToken(tokenValue, TokenType.INVITATION)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token"));

        Invitation invitation = invitationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new EntityNotFoundException("No invitation found for email: " + email));

        // Create or find user
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .password(passwordEncoder.encode(password))
                    .status(UserStatus.ACTIVE)
                    .superAdmin(false)
                    .build();
            
            UserEmail userEmail = UserEmail.builder()
                    .email(email)
                    .isPrimary(true)
                    .verified(true)
                    .user(newUser)
                    .build();
            
            newUser.addEmail(userEmail);
            return userRepository.save(newUser);
        });

        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
            user.setPassword(passwordEncoder.encode(password));
            // Mark email as verified if it wasn't
            user.getEmails().forEach(e -> {
                if (e.getEmail().equals(email)) e.setVerified(true);
            });
            userRepository.save(user);
        }

        // Create membership
        Membership membership = Membership.builder()
                .user(user)
                .company(invitation.getCompany())
                .role(Role.USER)
                .build();

        membershipRepository.save(membership);
        invitationRepository.delete(invitation);

        return user;
    }

    @Transactional(readOnly = true)
    public InvitationResponse getInvitationByToken(UUID token) {
        Invitation invitation = invitationRepository.findById(token)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found with token: " + token));
        return mapToResponse(invitation);
    }

    @Transactional
    public void cancelInvitation(UUID token) {
        if (!invitationRepository.existsById(token)) {
            throw new EntityNotFoundException("Invitation not found with token: " + token);
        }
        invitationRepository.deleteById(token);
    }

    /**
     * Route an invitation acceptance request to the appropriate flow.
     * Analyzes the email address to determine if it matches an existing user
     * and recommends the appropriate acceptance path.
     *
     * @param token the invitation token
     * @param invitedEmail the email address being invited
     * @return InvitationFlowRoute with flow recommendation and email match info
     * @throws InvitationInvalidTokenException if token is invalid
     * @throws InvitationTokenExpiredException if token is expired
     */
    @Transactional(readOnly = true)
    public InvitationFlowRoute routeAcceptanceFlow(String token, String invitedEmail) {
        log.info("Routing invitation acceptance flow for email: {}", invitedEmail);

        // Verify token is valid
        Optional<String> emailOpt = tokenService.checkToken(token, TokenType.INVITATION);
        if (emailOpt.isEmpty()) {
            log.warn("Invalid or expired token provided");
            throw new InvitationInvalidTokenException("Token is invalid or expired");
        }

        String tokenEmail = emailOpt.get();
        if (!tokenEmail.equalsIgnoreCase(invitedEmail)) {
            log.warn("Email mismatch: token for {} but provided {}", tokenEmail, invitedEmail);
            throw new IllegalArgumentException("Email address does not match invitation token");
        }

        // Find the invitation
        Invitation invitation = invitationRepository.findTopByEmailOrderByCreatedAtDesc(invitedEmail)
                .orElseThrow(() -> new EntityNotFoundException("No invitation found for email: " + invitedEmail));

        // Check expiration
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Invitation token expired for email: {}", invitedEmail);
            throw new InvitationTokenExpiredException("Invitation token has expired");
        }

        // Try to match email to existing user
        Optional<User> userOpt = matchInvitedEmailToExistingUser(invitedEmail);

        // Build email match info
        InvitationFlowRoute.EmailMatch emailMatch;
        String recommendedFlow;

        if (userOpt.isPresent()) {
            User existingUser = userOpt.get();
            List<String> orgNames = existingUser.getMemberships().stream()
                    .map(m -> m.getCompany().getName())
                    .collect(Collectors.toList());

            emailMatch = InvitationFlowRoute.EmailMatch.builder()
                    .found(true)
                    .userId(existingUser.getId())
                    .email(invitedEmail)
                    .userFullName(invitedEmail)
                    .existingOrganizations(orgNames)
                    .build();

            recommendedFlow = "ACCOUNT_LINKING";
            log.info("Email match found for user {} - recommending ACCOUNT_LINKING", existingUser.getId());
        } else {
            emailMatch = InvitationFlowRoute.EmailMatch.builder()
                    .found(false)
                    .email(invitedEmail)
                    .existingOrganizations(new ArrayList<>())
                    .build();

            recommendedFlow = "NEW_ACCOUNT";
            log.info("No email match found - recommending NEW_ACCOUNT for {}", invitedEmail);
        }

        // Build organization info
        InvitationFlowRoute.OrganizationInfo organizationInfo = InvitationFlowRoute.OrganizationInfo.builder()
                .id(invitation.getCompany().getId())
                .name(invitation.getCompany().getName())
                .role(Role.USER.name())
                .build();

        return InvitationFlowRoute.builder()
                .token(token)
                .emailMatch(emailMatch)
                .recommendedFlow(recommendedFlow)
                .organization(organizationInfo)
                .build();
    }

    /**
     * Match an invited email to an existing user by primary or secondary email.
     * Uses case-insensitive matching to find users with the invited email.
     *
     * @param invitedEmail the email being invited
     * @return Optional containing the User if a match is found
     */
    @Transactional(readOnly = true)
    public Optional<User> matchInvitedEmailToExistingUser(String invitedEmail) {
        if (invitedEmail == null || invitedEmail.isEmpty()) {
            return Optional.empty();
        }

        // Use UserRepository's findByEmail which queries both primary and secondary emails
        // with case-insensitive matching
        return userRepository.findByEmail(invitedEmail.toLowerCase());
    }

    /**
     * Check if an invitation can be accepted with account linking.
     * Returns linking eligibility status and email match info.
     *
     * @param tokenValue the invitation token
     * @return InvitationLinkingEligibility DTO with email match status and user details
     */
    @Transactional(readOnly = true)
    public InvitationLinkingEligibility canAcceptWithLinking(String tokenValue) {
        // Verify token is valid
        Optional<String> emailOpt = tokenService.checkToken(tokenValue, TokenType.INVITATION);

        if (emailOpt.isEmpty()) {
            return InvitationLinkingEligibility.builder()
                    .tokenValid(false)
                    .canLink(false)
                    .emailMatch(false)
                    .build();
        }

        String invitedEmail = emailOpt.get();

        // Find the invitation
        Optional<Invitation> invitationOpt = invitationRepository.findTopByEmailOrderByCreatedAtDesc(invitedEmail);

        if (invitationOpt.isEmpty()) {
            return InvitationLinkingEligibility.builder()
                    .tokenValid(true)
                    .canLink(false)
                    .emailMatch(false)
                    .build();
        }

        Invitation invitation = invitationOpt.get();

        // Check if invitation is expired
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return InvitationLinkingEligibility.builder()
                    .tokenValid(false)
                    .canLink(false)
                    .emailMatch(false)
                    .build();
        }

        // Try to match email to existing user
        Optional<User> userOpt = matchInvitedEmailToExistingUser(invitedEmail);

        if (userOpt.isEmpty()) {
            return InvitationLinkingEligibility.builder()
                    .tokenValid(true)
                    .canLink(false)
                    .emailMatch(false)
                    .invitedEmail(invitedEmail)
                    .organizationId(invitation.getCompany().getId())
                    .organizationName(invitation.getCompany().getName())
                    .build();
        }

        // Email match found - user can link
        User existingUser = userOpt.get();
        return InvitationLinkingEligibility.builder()
                .tokenValid(true)
                .canLink(true)
                .emailMatch(true)
                .invitedEmail(invitedEmail)
                .existingUserId(existingUser.getId())
                .organizationId(invitation.getCompany().getId())
                .organizationName(invitation.getCompany().getName())
                .build();
    }

    private InvitationResponse mapToResponse(Invitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .companyId(invitation.getCompany().getId())
                .inviterId(invitation.getInviterId())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
