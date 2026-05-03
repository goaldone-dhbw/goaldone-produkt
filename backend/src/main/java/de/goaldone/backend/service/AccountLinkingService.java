package de.goaldone.backend.service;

import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.exception.AlreadyLinkedException;
import de.goaldone.backend.exception.LinkTokenExpiredException;
import de.goaldone.backend.exception.NotLinkedException;
import de.goaldone.backend.exception.SameOrganizationLinkNotAllowedException;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Service for managing the linking and unlinking of user accounts across different organizations.
 * It handles the creation and confirmation of link tokens, merging user identities, and cleaning up expired tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLinkingService {

    private static final Duration LINK_TOKEN_TTL = Duration.ofMinutes(10);

    private final LinkTokenRepository linkTokenRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final WorkingTimeRepository workingTimeRepository;

    /**
     * Requests a new link token for the specified initiator account.
     *
     * @param initiatorAccountId The UUID of the account initiating the link request.
     * @return A {@link LinkTokenResponse} containing the generated token and its expiration time.
     */
    public LinkTokenResponse requestLink(UUID initiatorAccountId) {
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(UUID.randomUUID());
        token.setInitiatorAccountId(initiatorAccountId);
        token.setExpiresAt(Instant.now().plus(LINK_TOKEN_TTL));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        LinkTokenResponse response = new LinkTokenResponse();
        response.setLinkToken(token.getToken());
        response.setExpiresAt(OffsetDateTime.ofInstant(token.getExpiresAt(), ZoneOffset.UTC));
        return response;
    }

    /**
     * Confirms a link request using a provided token and a confirming account.
     * Merges the identities of the two accounts if they are not already linked.
     *
     * @param linkToken          The UUID of the link token to confirm.
     * @param confirmingAccountId The UUID of the account confirming the link.
     * @return {@code true} if there are working time conflicts after merging identities, {@code false} otherwise.
     * @throws LinkTokenExpiredException          if the token is not found or has expired.
     * @throws AlreadyLinkedException             if the accounts are already linked to the same identity.
     * @throws SameOrganizationLinkNotAllowedException if the accounts belong to the same organization.
     * @throws IllegalStateException              if the initiator or confirming account cannot be found.
     */
    @Transactional
    public boolean confirmLink(UUID linkToken, UUID confirmingAccountId) {
        LinkTokenEntity tokenEntity = linkTokenRepository.findById(linkToken)
            .orElseThrow(() -> new LinkTokenExpiredException(linkToken));

        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            linkTokenRepository.delete(tokenEntity);
            throw new LinkTokenExpiredException(linkToken);
        }

        MembershipEntity accountA = membershipRepository.findById(tokenEntity.getInitiatorAccountId())
            .orElseThrow(() -> new IllegalStateException("Initiator account not found"));
        MembershipEntity accountB = membershipRepository.findById(confirmingAccountId)
            .orElseThrow(() -> new IllegalStateException("Confirming account not found"));

        if (accountA.getUser().getId().equals(accountB.getUser().getId())) {
            throw new AlreadyLinkedException();
        }

        // Check for same-organization link
        var orgsWithMultipleUsers = membershipRepository.findOrgIdsWithMultipleUsers(
            accountA.getUser().getId(), accountB.getUser().getId());
        if (!orgsWithMultipleUsers.isEmpty()) {
            throw new SameOrganizationLinkNotAllowedException();
        }

        // Merge: reassign all memberships from user B to user A, then delete user B
        UserEntity userA = accountA.getUser();
        UserEntity userB = accountB.getUser();
        membershipRepository.findAllByUserId(userB.getId())
            .forEach(m -> {
                m.setUser(userA);
                membershipRepository.save(m);
            });
        userRepository.delete(userB);
        linkTokenRepository.delete(tokenEntity);

        // Check if there are conflicts after merging
        boolean hasConflicts = workingTimeRepository.hasConflictsForUser(userA.getId());

        log.info("Linked accounts {} and {} under user {}, hasConflicts: {}",
            accountA.getId(), accountB.getId(), userA.getId(), hasConflicts);

        return hasConflicts;
    }

    /**
     * Unlinks a target account from its current identity and creates a new identity for it.
     *
     * @param currentAccountId The UUID of the currently logged-in account.
     * @param targetAccountId  The UUID of the account to be unlinked.
     * @throws ResponseStatusException if the current account is not authorized to unlink the target account.
     * @throws NotLinkedException      if the account is not linked to any other accounts.
     * @throws IllegalStateException    if the current or target account cannot be found.
     */
    @Transactional
    public void unlink(UUID currentAccountId, UUID targetAccountId) {
        MembershipEntity currentAccount = membershipRepository.findById(currentAccountId)
            .orElseThrow(() -> new IllegalStateException("Current account not found"));
        MembershipEntity targetAccount = membershipRepository.findById(targetAccountId)
            .orElseThrow(() -> new IllegalStateException("Target account not found"));

        if (!currentAccount.getUser().getId().equals(targetAccount.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to unlink this account");
        }

        long count = membershipRepository.countByUserId(currentAccount.getUser().getId());
        if (count <= 1) {
            throw new NotLinkedException();
        }

        // Create a new user identity for the target account
        UserEntity newIdentity = new UserEntity();
        newIdentity.setId(UUID.randomUUID());
        // Note: we might need a dummy or placeholder authUserId here if the model requires it
        newIdentity.setAuthUserId("unlinked-" + UUID.randomUUID());
        newIdentity.setCreatedAt(Instant.now());
        userRepository.save(newIdentity);

        targetAccount.setUser(newIdentity);
        membershipRepository.save(targetAccount);

        log.info("Unlinked account {} from user {}, now has user {}",
            targetAccountId, currentAccount.getUser().getId(), newIdentity.getId());
    }

    /**
     * Periodically cleans up expired link tokens from the database.
     * Runs every hour at the top of the hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        linkTokenRepository.deleteByExpiresAtBefore(Instant.now());
        log.debug("Cleaned up expired link tokens");
    }
}
