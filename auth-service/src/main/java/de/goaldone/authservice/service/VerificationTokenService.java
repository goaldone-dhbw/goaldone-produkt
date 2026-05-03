package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.VerificationToken;
import de.goaldone.authservice.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for managing the lifecycle of verification tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.token.expiry-hours:24}")
    private int defaultExpiryHours;

    @Value("${app.token.password-reset-expiry-hours:1}")
    private int passwordResetExpiryHours;

    private int getExpiryHoursForType(TokenType type) {
        return type == TokenType.PASSWORD_RESET ? passwordResetExpiryHours : defaultExpiryHours;
    }

    /**
     * Creates a new verification token for the given email and type.
     * If an active token already exists for the same email and type, it will be replaced.
     *
     * @param email the email address
     * @param type  the token type
     * @return the created VerificationToken
     */
    @Transactional
    public VerificationToken createToken(String email, TokenType type) {
        log.debug("Creating verification token of type {} for email {}", type, email);
        
        // Remove existing tokens of the same type for this email
        tokenRepository.deleteByEmailAndType(email, type);

        String tokenValue = generateSecureToken();
        VerificationToken token = VerificationToken.builder()
                .token(tokenValue)
                .type(type)
                .email(email)
                .expiryDate(LocalDateTime.now().plusHours(getExpiryHoursForType(type)))
                .build();

        return tokenRepository.save(token);
    }

    /**
     * Validates a token string and type.
     * If the token is valid and not expired, it is deleted and the email is returned.
     * This implements single-use tokens.
     *
     * @param tokenValue the token string
     * @param type       the token type
     * @return an Optional containing the email address if the token is valid and not expired, or empty otherwise
     */
    @Transactional
    public Optional<String> validateToken(String tokenValue, TokenType type) {
        log.debug("Validating token of type {}", type);

        return tokenRepository.findByTokenAndType(tokenValue, type)
                .filter(token -> !token.isExpired())
                .map(token -> {
                    String email = token.getEmail();
                    // Tokens are single-use, so we delete it after successful validation
                    tokenRepository.delete(token);
                    return email;
                });
    }

    /**
     * Checks if a token is valid without consuming it.
     * Use this for read-only token validation (e.g., landing page checks).
     *
     * @param tokenValue the token string
     * @param type       the token type
     * @return an Optional containing the email address if the token is valid and not expired, or empty otherwise
     */
    public Optional<String> checkToken(String tokenValue, TokenType type) {
        log.debug("Checking token of type {} (non-consuming)", type);
        return tokenRepository.findByTokenAndType(tokenValue, type)
                .filter(token -> !token.isExpired())
                .map(VerificationToken::getEmail);
    }

    /**
     * Verifies a token string and type.
     * Alias for {@link #validateToken(String, TokenType)} for backward compatibility.
     *
     * @param tokenValue the token string
     * @param type       the token type
     * @return an Optional containing the email address if the token is valid and not expired, or empty otherwise
     */
    public Optional<String> verifyToken(String tokenValue, TokenType type) {
        return validateToken(tokenValue, type);
    }

    /**
     * Generates a cryptographically secure random string of at least 32 characters.
     *
     * @return a secure random string
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Periodically clean up expired tokens.
     * This could be triggered by a scheduled task.
     */
    @Transactional
    public void purgeExpiredTokens() {
        log.info("Purging expired verification tokens");
        var expiredTokens = tokenRepository.findExpiredTokens(LocalDateTime.now());
        if (!expiredTokens.isEmpty()) {
            tokenRepository.deleteAll(expiredTokens);
        }
    }
}
