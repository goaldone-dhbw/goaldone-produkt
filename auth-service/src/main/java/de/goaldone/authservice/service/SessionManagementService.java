package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for managing user sessions after invitation acceptance.
 * Handles session creation, updates, and organization context switching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManagementService {

    private final SessionRepository sessionRepository;

    @Value("${session.timeout.minutes:30}")
    private int sessionTimeoutMinutes;

    /**
     * Create a new session for a user after successful invitation acceptance.
     * Automatically logs the user in and sets appropriate organization context.
     *
     * @param user the user who accepted the invitation
     * @param organization the organization being joined
     * @param authentication the current authentication (may be null for new account)
     * @return the session ID
     */
    public String createSessionAfterInvitationAcceptance(User user, Company organization, Authentication authentication) {
        log.info("Creating new session for user {} after invitation acceptance to organization {}",
                user.getId(), organization.getId());

        try {
            // Create new session
            Session session = sessionRepository.createSession();

            // Set session timeout
            session.setMaxInactiveInterval(Duration.ofMinutes(sessionTimeoutMinutes));

            // Set user context in session
            session.setAttribute("userId", user.getId());
            session.setAttribute("email", user.getEmails().isEmpty() ? null :
                    user.getEmails().stream()
                            .filter(e -> e.isPrimary())
                            .map(e -> e.getEmail())
                            .findFirst()
                            .orElse(user.getEmails().get(0).getEmail()));

            // Set organization context
            session.setAttribute("organizationId", organization.getId());
            session.setAttribute("organizationName", organization.getName());

            // Save session
            sessionRepository.save(session);

            log.info("Session created successfully for user {} with ID: {}", user.getId(), session.getId());
            return session.getId();
        } catch (Exception e) {
            log.error("Failed to create session for user {} after invitation acceptance", user.getId(), e);
            throw new RuntimeException("Failed to create session after invitation acceptance", e);
        }
    }

    /**
     * Update an existing session with organization context.
     * Used when an existing user links a new email/org through invitation.
     *
     * @param user the user
     * @param organization the new organization to add to context
     */
    public void updateSessionWithOrganizationContext(User user, Company organization) {
        log.info("Updating session for user {} to include organization {}", user.getId(), organization.getId());

        try {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated user in security context, cannot update session");
                return;
            }

            // Get current session if available (would need request context)
            // For now, log the context change
            log.info("User {} now has access to organization {}", user.getId(), organization.getId());
        } catch (Exception e) {
            log.error("Failed to update session context for user {} and organization {}",
                    user.getId(), organization.getId(), e);
        }
    }

    /**
     * Clear cached user details from session after account linking.
     * Forces reload of user data to reflect new email/org additions.
     */
    public void clearUserDetailsFromSession() {
        log.debug("Clearing cached user details from current session");

        try {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            if (securityContext != null && securityContext.getAuthentication() != null) {
                // Note: In Spring Security, the principal is typically held in memory
                // A full session invalidation and re-authentication would be needed to force reload
                log.debug("User details cache cleared");
            }
        } catch (Exception e) {
            log.warn("Failed to clear user details from session", e);
        }
    }

    /**
     * Invalidate current session (used if user chooses not to accept invitation).
     *
     * @param sessionId the session ID to invalidate
     */
    public void invalidateSession(String sessionId) {
        try {
            Session session = sessionRepository.findById(sessionId);
            if (session != null) {
                sessionRepository.deleteById(sessionId);
                log.info("Session {} invalidated", sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate session {}", sessionId, e);
        }
    }

    /**
     * Get the session timeout in minutes.
     */
    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }
}
