package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.VerificationToken;
import de.goaldone.authservice.repository.UserRepository;
import de.goaldone.authservice.service.MailService;
import de.goaldone.authservice.service.VerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final UserRepository userRepository;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final SessionRegistry sessionRegistry;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            VerificationToken token = tokenService.createToken(email, TokenType.PASSWORD_RESET);
            String resetUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/reset-password")
                    .queryParam("token", token.getToken())
                    .toUriString();
            mailService.sendPasswordReset(email, resetUrl);
        }

        // Enumeration protection: always show same message
        model.addAttribute("message", "If an account exists for " + email + ", you will receive a password reset link shortly.");
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        Optional<String> emailOpt = tokenService.validateToken(token, TokenType.PASSWORD_RESET);
        if (emailOpt.isEmpty()) {
            return "redirect:/forgot-password?error=invalid_token";
        }
        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match.");
            return "auth/reset-password";
        }

        Optional<String> emailOpt = tokenService.verifyToken(token, TokenType.PASSWORD_RESET);
        if (emailOpt.isEmpty()) {
            return "redirect:/forgot-password?error=invalid_token";
        }

        String email = emailOpt.get();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found for verified token email: " + email));

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        invalidateUserSessions(email);

        return "redirect:/login?reset_success";
    }

    private void invalidateUserSessions(String email) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (isSameUser(principal, email)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }

    private boolean isSameUser(Object principal, String email) {
        if (principal instanceof String principalEmail) {
            return principalEmail.equals(email);
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername().equals(email);
        }
        return false;
    }
}
