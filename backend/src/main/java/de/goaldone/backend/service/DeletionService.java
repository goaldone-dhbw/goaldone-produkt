package de.goaldone.backend.service;

import com.zitadel.model.OrganizationServiceOrganization;
import com.zitadel.model.UserServiceListUsersResponse;
import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.MemberRole;
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
    private final UserIdentityRepository userIdentityRepository;

    @Value("${zitadel.goaldone.project-id}")
    private String projectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    /**
     * Deletes an organization and all associated users from Zitadel and local database.
     * The Transactional annotation ensures that the database changes are rolled back in case of an error.
     * @param zitadelOrgId the ID of the organization to delete in Zitadel
     */
    @Transactional
    public void deleteOrg(String zitadelOrgId) {
        if (mainOrgId.equals(zitadelOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HOME_ORGANIZATION_CANNOT_BE_DELETED");
        }

        try {
            List<OrganizationServiceOrganization> orgInfoList = zitadelManagementClient.getOrganizationInfoById(zitadelOrgId).getResult();
            if (orgInfoList == null || orgInfoList.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND");
            } else if (orgInfoList.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ORGANIZATION_ID_NOT_UNIQUE");
            }

            Optional<OrganizationEntity> localOrg = organizationRepository.findByZitadelOrgId(zitadelOrgId);
            UserServiceListUsersResponse orgUsers = zitadelManagementClient.listUsersOfOrg(zitadelOrgId);

            List<String> failedUserIds = new ArrayList<>();

            if (orgUsers.getResult() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_USERS_NOT_FOUND");
            }

            if(localOrg.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LOCAL_ORGANIZATION_NOT_FOUND");
                //TODO: can be empty
            }

            List<UserAccountEntity> userAccounts = userAccountRepository.findAllByOrganizationId(localOrg.get().getId());
            Map<String, UserAccountEntity> zitadelSubToAccountMap = new HashMap<>();
            for (UserAccountEntity account : userAccounts) {
                zitadelSubToAccountMap.put(account.getZitadelSub(), account);
            }

            for (UserServiceUser user : orgUsers.getResult()) {

                // If the user is active in zitadel, there can be a local record of the user.
                // In that case, delete the local record.
                if (user.getState().toString().equals("USER_STATE_ACTIVE")) {
                    if (zitadelSubToAccountMap.containsKey(user.getUserId())) {
                        try {
                            UUID localUserId = zitadelSubToAccountMap.get(user.getUserId()).getId();
                            deleteLocalUserAccount(localUserId);
                        } catch (Exception e) {
                            log.error("Failed to delete active user account {} (zitadelSub={}): {}",
                                    zitadelSubToAccountMap.get(user.getUserId()).getId(), user.getUserId(), e.getMessage());
                        }
                    }
                }
                // Delete user in zitadel
                deleteZitadelUserAccount(user.getUserId());
            }

            localOrg.ifPresent(org -> {
                deleteLocalOrg(org.getId(), zitadelOrgId);
            });
            deleteZitadelOrg(zitadelOrgId);
        } catch (ZitadelApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("No organization found for id")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND");
            }
            throw e;
        }
    }

    /**
     * This method includes a safety check to ensure that the user is not the last admin in the organization.
     * If the user is the last admin, the deletion is not allowed.
     * @param accountId the ID of the user to be deleted
     */
    @Transactional
    public void deleteUser(UUID accountId) {
        UserAccountEntity account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        OrganizationEntity organization = organizationRepository.findById(account.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND"));

        try {
            Map<String, List<MemberRole>> rolesMap = zitadelManagementClient.listUsersWithTheirRoles(organization.getZitadelOrgId(), projectId);
            int adminCount = 0;
            for (List<MemberRole> role : rolesMap.values()) {
                for (MemberRole r : role) {
                    if(MemberRole.COMPANY_ADMIN.equals(r)) {
                        adminCount++;
                    }
                }
            }
            if(adminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_REMOVED");
            }

            deleteUserAccount(accountId);

        } catch (ZitadelApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("No organization found for id")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND");
            }
            throw e;
        }
    }

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

        zitadelManagementClient.deleteUser(account.getZitadelSub());

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


    /**
     * Deletes a user account from Zitadel. If the deletion fails, a ZitadelApiException is thrown,
     * therefore, the Transactional annotation will roll back the database changes.
     * @param zitadelUserId the ID of the user to delete in Zitadel
     */
    private void deleteZitadelUserAccount(String zitadelUserId) {
        try {
            zitadelManagementClient.deleteUser(zitadelUserId);
            log.info("Deleted Zitadel user {}", zitadelUserId);
        } catch (Exception e) {
            log.error("Failed to delete Zitadel user {}: {}", zitadelUserId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "FAILED_TO_DELETE_ZITADEL_USER: " + zitadelUserId);
        }
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

    private void deleteZitadelOrg(String zitadelOrgId) {
        try {
            zitadelManagementClient.deleteOrganization(zitadelOrgId);
        } catch (Exception e) {
            log.error("Failed to delete zitadel org: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "FAILED_TO_DELETE_ZITADEL_ORGANIZATION");
        }
    }

    private void deleteLocalOrg(UUID orgId, String zitadelOrgId) {
        try {
            organizationRepository.deleteById(orgId);
            log.info("Deleted organization {} (zitadelOrgId={})", orgId, zitadelOrgId);
        } catch (Exception e) {
            log.error("Failed to delete local organization record {}: {}", orgId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "FAILED_TO_DELETE_LOCAL_ORGANIZATION");
        }
    }
}
