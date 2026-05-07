package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for deleting user accounts.
 * Handles the removal of user account records and their associated identities if they are no longer needed.
 * The Zitadel user is deleted first; if that fails, a {@link de.goaldone.backend.exception.ZitadelApiException}
 * is thrown and the local record is left intact.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final ZitadelManagementClient zitadelManagementClient;

    /**
     * Deletes a user account by its ID.
     * First removes the user from Zitadel (throwing if that fails), then deletes the local account record.
     * If the account is the last one associated with a user identity, the identity is also deleted.
     *
     * @param accountId The UUID of the account to be deleted.
     * @throws IllegalStateException                             if the account cannot be found.
     * @throws de.goaldone.backend.exception.ZitadelApiException if the Zitadel deletion fails.
     */
    @Transactional
    public void deleteUserAccount(UUID accountId) {
        UserAccountEntity account = userAccountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalStateException("Account not found"));

        UUID identityId = account.getUserIdentityId();
        long count = userAccountRepository.countByUserIdentityId(identityId);

        zitadelManagementClient.deleteUserOrThrow(account.getZitadelSub());

        userAccountRepository.delete(account);

        if (count == 1) {
            // Last account in identity — clean up identity too
            userIdentityRepository.deleteById(identityId);
            log.info("Deleted account {} and its identity {}", accountId, identityId);
        } else {
            log.info("Deleted account {}, identity {} remains with {} accounts",
                accountId, identityId, count - 1);
        }
    }
}
