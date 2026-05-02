package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.repository.UserRepository;
import de.goaldone.authservice.security.CustomUserDetails;
import de.goaldone.authservice.service.InvitationManagementService;
import de.goaldone.authservice.service.VerificationTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/invitation")
@RequiredArgsConstructor
@Slf4j
public class InvitationController {

    private final VerificationTokenService tokenService;
    private final UserRepository userRepository;
    private final InvitationManagementService invitationService;

    @GetMapping
    public String landingPage(@RequestParam("token") String tokenValue, Authentication authentication, Model model) {
        log.info("Accessing invitation landing page with token: {}", tokenValue);

        // Use checkToken (non-consuming) to check validity
        Optional<String> emailOpt = tokenService.checkToken(tokenValue, TokenType.INVITATION);
        if (emailOpt.isEmpty()) {
            model.addAttribute("message", "Invalid or expired invitation token.");
            return "error";
        }

        String email = emailOpt.get();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            // New user, redirect to set-password
            return "redirect:/invitation/set-password?token=" + tokenValue;
        }

        model.addAttribute("email", email);
        model.addAttribute("token", tokenValue);
        model.addAttribute("isExistingUser", true);
        model.addAttribute("isLoggedIn", authentication != null && authentication.isAuthenticated());
        model.addAttribute("emailMatch", true);

        return "auth/invitation-landing";
    }

    @PostMapping("/accept")
    public String acceptInvitation(@RequestParam("token") String tokenValue, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = null;
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            user = userDetails.getUser();
        } else {
            // Fallback for other principal types (e.g., basic Spring Security User)
            String email = authentication.getName();
            user = userRepository.findByEmail(email).orElse(null);
        }

        if (user == null) {
            log.warn("Could not find user for authenticated principal: {}", authentication.getName());
            return "redirect:/login";
        }

        invitationService.acceptInvitation(tokenValue, user);
        return "redirect:/?invitation_accepted";
    }

    @GetMapping("/set-password")
    public String setPasswordForm(@RequestParam("token") String tokenValue, Model model) {
        // Use checkToken (non-consuming) to check validity
        Optional<String> emailOpt = tokenService.checkToken(tokenValue, TokenType.INVITATION);
        if (emailOpt.isEmpty()) {
            model.addAttribute("message", "Invalid or expired invitation token.");
            return "error";
        }

        model.addAttribute("token", tokenValue);
        model.addAttribute("email", emailOpt.get());
        return "auth/invitation-set-password";
    }

    @PostMapping("/set-password")
    public String setPassword(@RequestParam("token") String tokenValue,
                              @RequestParam("password") String password,
                              @RequestParam("confirmPassword") String confirmPassword,
                              Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", tokenValue);
            model.addAttribute("error", "Passwords do not match.");
            return "auth/invitation-set-password";
        }

        try {
            invitationService.activateUser(tokenValue, password);
            return "redirect:/login?activation_success";
        } catch (Exception e) {
            model.addAttribute("message", e.getMessage());
            return "error";
        }
    }
}
