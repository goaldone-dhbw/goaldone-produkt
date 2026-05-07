package de.goaldone.backend.service;

import com.zitadel.model.OrganizationServiceListOrganizationsResponse;
import com.zitadel.model.UserServiceListUsersResponse;
import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.PartialDeletionException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletionService {

    private final ZitadelManagementClient zitadelManagementClient;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountDeletionService userAccountDeletionService;
    private final UserIdentityRepository userIdentityRepository;

    @Value("${zitadel.goaldone.project-id}")
    private String projectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    /**
     * Deletes an organization and all associated users from Zitadel and local database.
     * The Transactional annotation ensures that the database changes are rolled back in case of an error.
     * @param zitadelOrgId
     * @return
     */
    @Transactional
    public boolean deleteOrg(String zitadelOrgId) {
        if (mainOrgId.equals(zitadelOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HOME_ORGANIZATION_CANNOT_BE_DELETED");
        }

        try {
            OrganizationServiceListOrganizationsResponse orgInfo = zitadelManagementClient.getOrganizationInfoById(zitadelOrgId);
            Optional<OrganizationEntity> localOrg = organizationRepository.findByZitadelOrgId(zitadelOrgId);
            UserServiceListUsersResponse orgUsers = zitadelManagementClient.listUsersOfOrg(zitadelOrgId);

            List<String> failedUserIds = new ArrayList<>();

            if (orgUsers.getResult() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_USERS_NOT_FOUND");
            }

            if( localOrg.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND");
            }

            List<UserAccountEntity> userAccounts = userAccountRepository.findAllByOrganizationId(localOrg.get().getId());
            Map<String, UserAccountEntity> zitadelSubToAccountMap = new HashMap<>();
            for (UserAccountEntity account : userAccounts) {
                zitadelSubToAccountMap.put(account.getZitadelSub(), account);
            }

            for (UserServiceUser user : orgUsers.getResult()) {
                if (user.getState().toString().equals("USER_STATE_ACTIVE")) {
                    // If there is a local shadow record for the user, delete it
                    if (zitadelSubToAccountMap.containsKey(user.getUserId())) {
                        try {
                            UUID localUserId = zitadelSubToAccountMap.get(user.getUserId()).getId();
                            deleteLocalUserAccount(zitadelSubToAccountMap.get(localUserId);
                            deleteZitadelUserAccount();
                            //TODO: continue here






                        } catch (Exception e) {
                            log.error("Failed to delete active user account {} (zitadelSub={}): {}",
                                    zitadelSubToAccountMap.get(user.getUserId()).getId(), user.id(), e.getMessage());
                            failedUserIds.add(user.id());
                        }
                    } else {
                        // Active in Zitadel but no local shadow record — delete directly
                        try {
                            zitadelManagementClient.deleteUserOrThrow(user.id());
                        } catch (Exception e) {
                            log.error("Failed to delete Zitadel user {} (no local account): {}", user.id(), e.getMessage());
                            failedUserIds.add(user.id());
                        }
                    }
                } else {
                    // Invited (USER_STATE_INITIAL) or any other state — no local record, delete directly in Zitadel
                    try {
                        zitadelManagementClient.deleteUserOrThrow(user.id());
                    } catch (Exception e) {
                        log.error("Failed to delete invited user {} in Zitadel: {}", user.id(), e.getMessage());
                        failedUserIds.add(user.id());
                    }
                }
            }

            if (!failedUserIds.isEmpty()) {
                throw new PartialDeletionException(failedUserIds);
            }

            zitadelManagementClient.deleteOrganizationOrThrow(zitadelOrg.id());

            orgOptional.ifPresent(org -> {
                try {
                    organizationRepository.deleteById(org.getId());
                    log.info("Deleted organization {} (zitadelOrgId={})", org.getId(), org.getZitadelOrgId());
                } catch (Exception e) {
                    log.error("Failed to delete local organization record {}: {}", org.getId(), e.getMessage());
                    throw new ZitadelApiException("Failed to delete local organization record: " + e.getMessage(), e);
                }
            });


        } catch (ZitadelApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("No organization found for id")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND");
            }
            throw e;
        }
    }

    private void deleteZitadelUserAccount(String zitadelSub) {
        //TODO: implement
    }

    private void deleteLocalUserAccount(UUID accountId) {
        Optional<UserAccountEntity> accountResponse = userAccountRepository.findById(accountId);
        if(accountResponse.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        }
        UserAccountEntity account = accountResponse.get();

        int accountsInIdentity = userAccountRepository.findAllByUserIdentityId(account.getUserIdentityId()).size();

        userAccountRepository.delete(account);

        if (accountsInIdentity == 1) {
            // Last account in identity — clean up identity too
            userIdentityRepository.deleteById(account.getUserIdentityId());
            log.info("Deleted account {} and its identity {}", accountId, account.getUserIdentityId());
        } else {
            log.info("Deleted account {}, identity {} remains with {} accounts",
                    accountId, account.getUserIdentityId(), accountsInIdentity - 1);
        }
    }
}
