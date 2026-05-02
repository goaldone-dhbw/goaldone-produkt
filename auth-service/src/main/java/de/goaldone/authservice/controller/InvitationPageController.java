package de.goaldone.authservice.controller;

import de.goaldone.authservice.dto.InvitationFlowRoute;
import de.goaldone.authservice.exception.InvitationInvalidTokenException;
import de.goaldone.authservice.exception.InvitationTokenExpiredException;
import de.goaldone.authservice.service.InvitationManagementService;
import de.goaldone.authservice.service.VerificationTokenService;
import de.goaldone.authservice.domain.TokenType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

/**
 * Controller for invitation landing page.
 * Handles the initial page users see when clicking invitation links.
 */
@Controller
@RequestMapping("/invitations")
@RequiredArgsConstructor
@Slf4j
public class InvitationPageController {

    private final VerificationTokenService tokenService;
    private final InvitationManagementService invitationService;

    /**
     * Invitation landing page.
     * Validates token, detects email match, and recommends a flow path to frontend.
     *
     * @param token the invitation token from the email link
     * @param authentication the current authentication (maybe null)
     * @param model the view model
     * @return the invitation landing page template with flow recommendation
     */
    @GetMapping("/{token}")
    public String landingPage(
            @PathVariable String token,
            Authentication authentication,
            Model model) {

        log.info("User accessing invitation landing page with token");

        try {
            // Validate token format and existence
            Optional<String> emailOpt = tokenService.checkToken(token, TokenType.INVITATION);

            if (emailOpt.isEmpty()) {
                log.warn("Invalid or expired invitation token provided");
                model.addAttribute("errorCode", "EXPIRED");
                model.addAttribute("message", "Die Einladung ist abgelaufen. Bitte fordern Sie eine neue Einladung an.");
                model.addAttribute("supportContact", "support@goaldone.de");
                return "auth/invitation-error";
            }

            String invitedEmail = emailOpt.get();
            log.debug("Token validated for email: {}", invitedEmail);

            // Get flow recommendation
            InvitationFlowRoute flowRoute = invitationService.routeAcceptanceFlow(token, invitedEmail);

            // Add to model for frontend
            model.addAttribute("token", token);
            model.addAttribute("email", invitedEmail);
            model.addAttribute("flowRoute", flowRoute);
            model.addAttribute("isLoggedIn", authentication != null && authentication.isAuthenticated());

            // Set model attributes for template
            model.addAttribute("recommendedFlow", flowRoute.getRecommendedFlow());
            model.addAttribute("emailMatch", flowRoute.getEmailMatch().isFound());
            model.addAttribute("matchedUserId", flowRoute.getEmailMatch().getUserId());
            model.addAttribute("matchedUserName", flowRoute.getEmailMatch().getUserFullName());
            model.addAttribute("existingOrganizations", flowRoute.getEmailMatch().getExistingOrganizations());
            model.addAttribute("organization", flowRoute.getOrganization());

            log.info("Invitation landing page loaded successfully for flow: {}", flowRoute.getRecommendedFlow());
            return "auth/invitation-landing";

        } catch (InvitationTokenExpiredException e) {
            log.warn("Invitation token expired: {}", e.getMessage());
            model.addAttribute("errorCode", "EXPIRED");
            model.addAttribute("message", "Die Einladung ist abgelaufen. Bitte fordern Sie eine neue Einladung an.");
            model.addAttribute("supportContact", "support@goaldone.de");
            return "auth/invitation-error";

        } catch (InvitationInvalidTokenException e) {
            log.warn("Invalid invitation token: {}", e.getMessage());
            model.addAttribute("errorCode", "INVALID");
            model.addAttribute("message", "Die Einladung ist ungültig. Bitte überprüfen Sie den Link.");
            model.addAttribute("supportContact", "support@goaldone.de");
            return "auth/invitation-error";

        } catch (Exception e) {
            log.error("Unexpected error on invitation landing page", e);
            model.addAttribute("errorCode", "ERROR");
            model.addAttribute("message", "Ein unerwarteter Fehler ist aufgetreten. Bitte kontaktieren Sie den Support.");
            model.addAttribute("supportContact", "support@goaldone.de");
            return "auth/invitation-error";
        }
    }

    /**
     * Invitation error page.
     * Shows error state for invalid/expired/already-accepted invitations.
     * This is rendered by landingPage() when errors occur.
     *
     * @return the error template
     */
    @GetMapping("/error")
    public String errorPage() {
        return "auth/invitation-error";
    }
}
