package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.VerificationToken;
import de.goaldone.authservice.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class VerificationTokenServiceTests {

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    private final String testEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
    }

    @Test
    void createToken_shouldGenerateSecureToken() {
        VerificationToken token = tokenService.createToken(testEmail, TokenType.INVITATION);

        assertThat(token).isNotNull();
        assertThat(token.getToken()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(token.getEmail()).isEqualTo(testEmail);
        assertThat(token.getType()).isEqualTo(TokenType.INVITATION);
        assertThat(token.getExpiryDate()).isAfter(LocalDateTime.now());
    }

    @Test
    void createToken_shouldReplaceExistingToken() {
        VerificationToken firstToken = tokenService.createToken(testEmail, TokenType.INVITATION);
        VerificationToken secondToken = tokenService.createToken(testEmail, TokenType.INVITATION);

        assertThat(tokenRepository.findByTokenAndType(firstToken.getToken(), TokenType.INVITATION)).isEmpty();
        assertThat(tokenRepository.findByTokenAndType(secondToken.getToken(), TokenType.INVITATION)).isPresent();
    }

    @Test
    void verifyToken_shouldReturnEmailAndDeleteMapping() {
        VerificationToken token = tokenService.createToken(testEmail, TokenType.INVITATION);

        Optional<String> verifiedEmail = tokenService.validateToken(token.getToken(), TokenType.INVITATION);

        assertThat(verifiedEmail).isPresent().contains(testEmail);
        assertThat(tokenRepository.findByTokenAndType(token.getToken(), TokenType.INVITATION)).isEmpty();
    }

    @Test
    void verifyToken_shouldReturnEmptyIfExpired() {
        VerificationToken token = tokenService.createToken(testEmail, TokenType.INVITATION);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(token);

        Optional<String> verifiedEmail = tokenService.validateToken(token.getToken(), TokenType.INVITATION);

        assertThat(verifiedEmail).isEmpty();
        // Expired token is NOT deleted automatically by verifyToken in the current implementation until checked, 
        // but we filter it out. Actually my implementation deletes it ONLY if valid.
    }

    @Test
    void verifyToken_shouldReturnEmptyIfWrongType() {
        VerificationToken token = tokenService.createToken(testEmail, TokenType.INVITATION);

        Optional<String> verifiedEmail = tokenService.validateToken(token.getToken(), TokenType.PASSWORD_RESET);

        assertThat(verifiedEmail).isEmpty();
    }

    @Test
    void purgeExpiredTokens_shouldRemoveExpiredTokens() {
        VerificationToken validToken = tokenService.createToken("valid@example.com", TokenType.INVITATION);
        VerificationToken expiredToken = tokenService.createToken("expired@example.com", TokenType.INVITATION);
        
        expiredToken.setExpiryDate(LocalDateTime.now().minusHours(1));
        tokenRepository.save(expiredToken);

        tokenService.purgeExpiredTokens();

        assertThat(tokenRepository.findById(validToken.getId())).isPresent();
        assertThat(tokenRepository.findById(expiredToken.getId())).isEmpty();
    }
}
