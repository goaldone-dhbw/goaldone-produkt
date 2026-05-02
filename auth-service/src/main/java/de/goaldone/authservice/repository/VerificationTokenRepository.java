package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link VerificationToken} entities.
 */
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    /**
     * Finds a token by its string value and type.
     *
     * @param token the token string
     * @param type  the token type
     * @return an Optional containing the found token, or empty if not found
     */
    Optional<VerificationToken> findByTokenAndType(String token, TokenType type);

    /**
     * Finds tokens by email and type.
     *
     * @param email the email address
     * @param type  the token type
     * @return an Optional containing the found token, or empty if not found
     */
    Optional<VerificationToken> findByEmailAndType(String email, TokenType type);

    /**
     * Finds all tokens that have expired before the given time.
     *
     * @param now the current time
     * @return a list of expired tokens
     */
    @Query("SELECT v FROM VerificationToken v WHERE v.expiryDate < :now")
    java.util.List<VerificationToken> findExpiredTokens(LocalDateTime now);

    /**
     * Deletes all tokens associated with a specific email and type.
     *
     * @param email the email address
     * @param type  the token type
     */
    void deleteByEmailAndType(String email, TokenType type);

    /**
     * Deletes all expired tokens (tokens with expiry date before the given time).
     *
     * @param now the current time
     */
    @Modifying
    @Query("DELETE FROM VerificationToken v WHERE v.expiryDate < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
