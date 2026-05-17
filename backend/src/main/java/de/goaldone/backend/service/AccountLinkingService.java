package de.goaldone.backend.service;

import com.zitadel.model.UserServiceHumanUser;
import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.exception.AlreadyLinkedException;
import de.goaldone.backend.exception.LinkTokenExpiredException;
import de.goaldone.backend.exception.NotLinkedException;
import de.goaldone.backend.exception.SameOrganizationLinkNotAllowedException;
import de.goaldone.backend.model.LinkInfoResponse;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final WorkingTimeConflictService workingTimeConflictService;
    private final ZitadelManagementClient zitadelManagementClient;
    private final CurrentUserResolver currentUserResolver;
    private final OrganizationRepository organizationRepository;

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
     * Retrieves information about a link token, including the account ID of the initiator.
     * @param linkToken the link token to check
     * @return the account ID of the initiator
     */
    public LinkInfoResponse getLinkInfo(UUID linkToken, Jwt jwt) {

        LinkTokenEntity tokenEntity = linkTokenRepository.findById(linkToken)
                .orElseThrow(() -> new LinkTokenExpiredException(linkToken));

        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            linkTokenRepository.delete(tokenEntity);
            throw new LinkTokenExpiredException(linkToken);
        } else if (tokenEntity.getInitiatorAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Initiator account ID is missing in token");
        }

        // Get current User info from token
        UserAccountEntity currentAccount = currentUserResolver.resolveCurrentAccount(jwt);
        UserAccountEntity initiatorAccount = userAccountRepository.findById(tokenEntity.getInitiatorAccountId())
                .orElseThrow(() -> new IllegalStateException("Initiator account not found"));
        List<UserAccountEntity> intiatorAccounts = userAccountRepository.findAllByUserIdentityId(initiatorAccount.getUserIdentityId());

        List<UserAccountEntity> accounts = new ArrayList<>();
        accounts.add(currentAccount);
        accounts.addAll(intiatorAccounts);

        Map<UUID, String> emails = new HashMap<>();
        List<UUID> orgs = new ArrayList<>();
        AtomicBoolean hasConflicts = new AtomicBoolean(false);
        accounts.forEach(account -> {
            Optional<OrganizationEntity> org = organizationRepository.findById(account.getOrganizationId());
            if (org.isPresent()) {
                if(orgs.contains(account.getOrganizationId())){
                   hasConflicts.set(true);
                } else {
                    orgs.add(account.getOrganizationId());
                }
            }
            Optional<UserServiceUser> user = zitadelManagementClient.getUser(account.getZitadelSub());
            if (user.isEmpty() || user.get().getHuman() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch user from Zitadel");
            }
            UserServiceHumanUser human = user.get().getHuman();
            if (human.getEmail() == null || human.getEmail().getEmail() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User email not found");
            }
            emails.put(account.getId(), Objects.requireNonNull(user.get().getHuman().getEmail()).getEmail());
        });

        ArrayList<String> initiatorEmails = new ArrayList<>();
        intiatorAccounts.forEach(account -> initiatorEmails.add(emails.get(account.getId())));

        return new LinkInfoResponse(initiatorAccount.getId(), initiatorEmails, emails.get(currentAccount.getId()), hasConflicts.get());
    }
    /**
     * Confirms a link request using a provided token and a confirming account.
     * Merges the identities of the two accounts if they are not already linked and do not belong to the same organization.
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

        UserAccountEntity accountA = userAccountRepository.findById(tokenEntity.getInitiatorAccountId())
            .orElseThrow(() -> new IllegalStateException("Initiator account not found"));
        UserAccountEntity accountB = userAccountRepository.findById(confirmingAccountId)
            .orElseThrow(() -> new IllegalStateException("Confirming account not found"));

        if (accountA.getUserIdentityId().equals(accountB.getUserIdentityId())) {
            throw new AlreadyLinkedException();
        }

        // Check for same-organization link (any org in A's identity matches any org in B's identity)
        var orgsWithMultipleIdentities = userAccountRepository.findOrgIdsWithMultipleIdentities(
            accountA.getUserIdentityId(), accountB.getUserIdentityId());
        if (!orgsWithMultipleIdentities.isEmpty()) {
            throw new SameOrganizationLinkNotAllowedException();
        }

        // Merge: reassign all accounts from identity B to identity A, then delete identity B
        UUID identityBId = accountB.getUserIdentityId();
        userAccountRepository.findAllByUserIdentityId(identityBId)
            .forEach(acc -> {
                acc.setUserIdentityId(accountA.getUserIdentityId());
                userAccountRepository.save(acc);
            });
        userIdentityRepository.deleteById(identityBId);
        linkTokenRepository.delete(tokenEntity);

        // Check if there are conflicts after merging
        boolean hasConflicts = workingTimeConflictService.hasConflictsForIdentity(accountA.getUserIdentityId());

        log.info("Linked accounts {} and {} under identity {}, hasConflicts: {}",
            accountA.getId(), accountB.getId(), accountA.getUserIdentityId(), hasConflicts);

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
        UserAccountEntity currentAccount = userAccountRepository.findById(currentAccountId)
            .orElseThrow(() -> new IllegalStateException("Current account not found"));
        UserAccountEntity targetAccount = userAccountRepository.findById(targetAccountId)
            .orElseThrow(() -> new IllegalStateException("Target account not found"));

        if (!currentAccount.getUserIdentityId().equals(targetAccount.getUserIdentityId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to unlink this account");
        }

        long count = userAccountRepository.countByUserIdentityId(currentAccount.getUserIdentityId());
        if (count <= 1) {
            throw new NotLinkedException();
        }

        // Create a new identity for the target account
        UserIdentityEntity newIdentity = new UserIdentityEntity();
        newIdentity.setId(UUID.randomUUID());
        newIdentity.setCreatedAt(Instant.now());
        userIdentityRepository.save(newIdentity);

        targetAccount.setUserIdentityId(newIdentity.getId());
        userAccountRepository.save(targetAccount);

        log.info("Unlinked account {} from identity {}, now has identity {}",
            targetAccountId, currentAccount.getUserIdentityId(), newIdentity.getId());
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
