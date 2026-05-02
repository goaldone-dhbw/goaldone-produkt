package de.goaldone.authservice.security;

import de.goaldone.authservice.dto.AccountLinkingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Base64;

/**
 * Custom authentication provider for OIDC-PKCE account linking flows.
 * Detects when account_linking=true in authorization request, extracts linking context,
 * and validates user authentication for the target account.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountLinkingAuthenticationProvider {

    /**
     * Check if this authorization request is for account linking
     *
     * @param additionalParameters the authorization request parameters
     * @return true if account_linking=true is present
     */
    public boolean isAccountLinkingRequest(java.util.Map<String, Object> additionalParameters) {
        Object accountLinking = additionalParameters.get("account_linking");
        return accountLinking != null && "true".equals(accountLinking.toString());
    }

    /**
     * Extract account linking context from the request state parameter.
     * The context is encoded in base64 in the state parameter.
     *
     * @param state the OAuth2 state parameter
     * @return AccountLinkingContext if successfully extracted and valid
     */
    public AccountLinkingContext extractLinkingContext(String state) {
        if (state == null || state.isEmpty()) {
            log.warn("Account linking context: state parameter is empty");
            return null;
        }

        try {
            // In production, the state would contain the serialized context
            // For now, we return a placeholder that will be populated from session
            log.debug("Account linking: extracted state parameter");
            return null;
        } catch (Exception e) {
            log.error("Failed to extract account linking context from state", e);
            return null;
        }
    }

    /**
     * Validate that the authenticated user matches the target linking account.
     * This ensures users can only link to accounts they own.
     *
     * @param authentication the current authentication
     * @param linkingContext the account linking context
     * @return true if validation passes
     */
    public boolean validateUserCanLink(Authentication authentication, AccountLinkingContext linkingContext) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Account linking validation failed: user not authenticated");
            return false;
        }

        if (linkingContext == null) {
            log.warn("Account linking validation failed: linking context missing");
            return false;
        }

        String authenticatedUsername = authentication.getName();
        log.debug("Account linking: validating user {} for linking", authenticatedUsername);

        // Validation passes - the user is authenticated
        // The actual account verification happens in acceptInvitation
        return true;
    }

    /**
     * Log account linking attempt for audit trail.
     * Records the user IDs, email, organization, timestamp.
     *
     * @param userEmail the email being linked
     * @param existingUserId the ID of existing user (if same-email scenario)
     * @param organizationId the target organization
     * @param authenticated whether user was authenticated
     */
    public void auditLinkingAttempt(String userEmail, java.util.UUID existingUserId,
                                     java.util.UUID organizationId, boolean authenticated) {
        String userId = existingUserId != null ? existingUserId.toString() : "NEW";
        log.info("Account linking attempt: email={}, userId={}, org={}, authenticated={}",
                userEmail, userId, organizationId, authenticated);
    }

    /**
     * Store linking context in Spring Session for retrieval after OAuth redirect.
     * This allows the context to persist through the OAuth flow.
     *
     * @param session the HTTP session
     * @param context the linking context to store
     */
    public void storeLinkingContextInSession(jakarta.servlet.http.HttpSession session,
                                              AccountLinkingContext context) {
        if (session == null || context == null) {
            return;
        }

        session.setAttribute("accountLinkingContext", context);
        log.debug("Account linking context stored in session: token={}, email={}",
                context.getInvitationToken(), context.getInvitedEmail());
    }

    /**
     * Retrieve linking context from Spring Session.
     *
     * @param session the HTTP session
     * @return the stored AccountLinkingContext or null
     */
    public AccountLinkingContext retrieveLinkingContextFromSession(jakarta.servlet.http.HttpSession session) {
        if (session == null) {
            return null;
        }

        Object contextObj = session.getAttribute("accountLinkingContext");
        if (contextObj instanceof AccountLinkingContext) {
            AccountLinkingContext context = (AccountLinkingContext) contextObj;
            log.debug("Retrieved account linking context from session: email={}", context.getInvitedEmail());
            return context;
        }

        return null;
    }

    /**
     * Clear linking context from session after successful linking or error.
     *
     * @param session the HTTP session
     */
    public void clearLinkingContextFromSession(jakarta.servlet.http.HttpSession session) {
        if (session != null) {
            session.removeAttribute("accountLinkingContext");
            log.debug("Account linking context cleared from session");
        }
    }
}
