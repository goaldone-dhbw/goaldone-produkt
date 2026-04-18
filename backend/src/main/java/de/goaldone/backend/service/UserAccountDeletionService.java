package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    // TODO: inject ZitadelManagementClient once implemented for deleteUser calls

    @Transactional
    public void deleteUserAccount(UUID accountId) {
        UserAccountEntity account = userAccountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalStateException("Account not found"));

        UUID identityId = account.getUserIdentityId();
        long count = userAccountRepository.countByUserIdentityId(identityId);

        // TODO: zitadelManagementClient.deleteUser(account.getZitadelSub());

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
