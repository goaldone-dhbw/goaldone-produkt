package de.goaldone.backend.service;

import com.zitadel.model.AuthorizationServiceAuthorization;
import com.zitadel.model.AuthorizationServiceRole;
import com.zitadel.model.UserServiceUpdateUserResponse;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user identities and their associated accounts.
 * Provides methods to retrieve identities, list accounts for an identity, and build account list responses.
 */
@Service
@RequiredArgsConstructor
public class UserIdentityService {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final ZitadelManagementClient zitadelManagementClient;
    private final WorkingTimeRepository workingTimeRepository;

    @Value("${zitadel.goaldone.org-id}")
    private String goaldoneOrgId;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;


    /**
     * Finds the identity ID for the user account associated with the provided JWT.
     *
     * @param jwt The current JWT representing the logged-in user.
     * @return The UUID of the user's identity.
     */
    public UUID findIdentityFromAccount(Jwt jwt) {
        UserAccountEntity currentAccount = getCurrentAccount(jwt);
        return currentAccount.getUserIdentityId();
    }


    /**
     * Retrieves all user accounts associated with a specific identity.
     *
     * @param identityId The UUID of the identity to find accounts for.
     * @return A list of {@link UserAccountEntity} objects.
     */
    public List<UserAccountEntity> findAccountsForIdentity(UUID identityId) {
        return userAccountRepository.findAllByUserIdentityId(identityId);
    }

    /**
     * Retrieves the current user account based on the JWT's subject claim.
     *
     * @param jwt The current JWT representing the logged-in user.
     * @return The {@link UserAccountEntity} associated with the token.
     * @throws IllegalStateException if the account cannot be found.
     */
    private UserAccountEntity getCurrentAccount(Jwt jwt) {
        return userAccountRepository
                .findByZitadelSub(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("Account not found after JIT provisioning"));
    }


    /**
     * Builds an {@link AccountListResponse} containing details of all accounts linked to the user's identity.
     *
     * @param jwt The current JWT representing the logged-in user.
     * @return An {@link AccountListResponse} summarizing the linked accounts and potential conflicts.
     * @throws IllegalStateException if organization details for any account cannot be found.
     */
    public AccountListResponse buildAccountListResponse(Jwt jwt) {
        UserAccountEntity currentAccount = getCurrentAccount(jwt);

        List<UserAccountEntity> accounts = findAccountsForIdentity(currentAccount.getUserIdentityId());
        boolean hasConflicts = workingTimeRepository.hasConflictsForIdentity(currentAccount.getUserIdentityId());

        List<AccountResponse> responses = accounts.stream()
                .map(account -> mapUserInfoToResponse(account, hasConflicts))
                .toList();

        AccountListResponse response = new AccountListResponse();
        response.setAccounts(responses);
        return response;
    }

    private AccountResponse mapUserInfoToResponse(UserAccountEntity account, boolean hasConflicts) {
        OrganizationEntity org = organizationRepository.findById(account.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException("Organization not found for account " + account.getId()));
        List<String> roles = zitadelManagementClient.getUserGrantRoles(
                account.getZitadelSub(), goaldoneOrgId, goaldoneProjectId);
        AccountResponse r = new AccountResponse();
        r.setAccountId(account.getId());
        r.setOrganizationId(account.getOrganizationId());
        r.setOrganizationName(org.getName());
        r.setRoles(roles);
        r.setHasConflicts(hasConflicts);

        zitadelManagementClient.getUser(account.getZitadelSub()).ifPresent(user -> {
            if (user.getHuman() != null) {
                var human = user.getHuman();
                if (human.getEmail() != null) {
                    r.setEmail(human.getEmail().getEmail());
                }
                if (human.getProfile() != null) {
                    var profile = human.getProfile();
                    if (profile.getGivenName() != null) {
                        r.setFirstName(profile.getGivenName());
                    }
                    if (profile.getFamilyName() != null) {
                        r.setLastName(profile.getFamilyName());
                    }
                }
            }
        });

        return r;
    }

    /**
     * Updates the account information for a specific account.
     * @param accountId the account to update
     * @param updateRequest update request body which includes information like first name, last name and email
     * @return the updated account information
     */
    public AccountResponse updateAccount(UUID accountId, AccountUpdateRequest updateRequest) {
        Optional<UserAccountEntity> localAccount = userAccountRepository.findById(accountId);
        if(localAccount.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        zitadelManagementClient.updateUser(localAccount.get().getZitadelSub(), updateRequest);

        boolean hasConflicts = workingTimeRepository.hasConflictsForIdentity(localAccount.get().getUserIdentityId());
        return mapUserInfoToResponse(localAccount.get(), hasConflicts);
    }

    /**
     * Updates the password for a specific account.
     * @param accountId the account to update
     * @param passwordUpdateRequest update request body which includes the current password and the new password
     */
    public void updateAccountPassword(UUID accountId, PasswordUpdateRequest passwordUpdateRequest) {
        Optional<UserAccountEntity> localAccount = userAccountRepository.findById(accountId);
        if(localAccount.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        zitadelManagementClient.updateUserPassword(localAccount.get().getZitadelSub(), passwordUpdateRequest.getCurrentPassword(), passwordUpdateRequest.getNewPassword());
    }


    /**
     * Retrieves the list of UUIDs for all accounts associated with the user's identity.
     *
     * @param jwt The current JWT representing the logged-in user.
     * @return A list of account UUIDs.
     */
    public List<UUID> accountIdsForUser(Jwt jwt) {
        return buildAccountListResponse(jwt).getAccounts().stream()
                .map(AccountResponse::getAccountId)
                .toList();
    }

    /**
     * Checks if the user associated with the provided JWT has access to the specified account ID.
     *
     * @param jwt       The current JWT representing the logged-in user.
     * @param accountId The UUID of the account to check access for.
     * @return {@code true} if the user has access to the account, {@code false} otherwise.
     */
    public boolean hasUserAccessToAccount(Jwt jwt, UUID accountId) {
        AccountListResponse accounts = buildAccountListResponse(jwt);

        return accounts.getAccounts().stream()
                .anyMatch(account -> account.getAccountId().equals(accountId));
    }

    /**
     * Checks if the user associated with the provided JWT has access to the specified organization and account IDs with any role
     *
     * @param jwt            JWT Token of the logged in user
     * @param organizationId UUID of the organization
     * @return true if the user has access to the organization, false otherwise
     */
    public boolean hasUserAccessToOrganization(Jwt jwt, UUID organizationId) {
        List<UserAccountEntity> accounts = getAccountInformationOfCurrentLoggedInUser(jwt);

        return accounts.stream()
                .anyMatch(account -> account.getOrganizationId().equals(organizationId));
    }

    /**
     * Checks if the user associated with the provided JWT has access to the specified organization and account IDs with the specified role
     *
     * @param jwt            JWT Token of the logged in user
     * @param organizationId UUID of the organization (local DB Id)
     * @param role           Role of the user in the organization to check against
     * @return true if the user has access to the organization, false otherwise
     */
    public boolean hasUserAccessToOrganizationWithRole(Jwt jwt, UUID organizationId, MemberRole role) {
        List<UserAccountEntity> accounts = getAccountInformationOfCurrentLoggedInUser(jwt);

        Optional<UserAccountEntity> account = accounts.stream()
                .filter(a -> a.getOrganizationId().equals(organizationId))
                .findFirst();

        if (account.isEmpty()) {
            return false;
        }

        List<MemberRole> roles = zitadelManagementClient.listGrantsForSpecificUser(goaldoneProjectId, account.get().getZitadelSub())
                .getAuthorizations()
                .stream()
                .map(auth -> {
                    if (auth.getRoles().isEmpty()) {
                        return null;
                    }
                    return MemberRole.fromValue(auth.getRoles().getFirst().getKey());
                }).toList();

        return !roles.isEmpty() && roles.contains(role);
    }

    /**
     * Checks if the user associated with the provided JWT is a super admin
     * @param jwt JWT Token of the logged in user
     * @return true if the user is a super admin, false otherwise
     */
    public boolean isUserSuperAdmin(Jwt jwt) {
        // Find all local accounts that the user has access to
        List<UserAccountEntity> accounts = getAccountInformationOfCurrentLoggedInUser(jwt);
        List<UUID> orgIds = accounts.stream()
                .map(UserAccountEntity::getOrganizationId)
                .toList();

        // Find all local orgs that the user has access to and check if any of them is the goaldone org
        // If the user is not a member of the goaldone org, then they cannot be a super admin
        List<OrganizationEntity> localOrgs = organizationRepository.findAllById(orgIds);
        List<String> zitadelOrgIds = localOrgs.stream()
                .map(OrganizationEntity::getZitadelOrgId)
                .toList();
        if (zitadelOrgIds.isEmpty() || !zitadelOrgIds.contains(goaldoneOrgId)) {
            return false;
        }

        // Find the accountId that is linked to the goaldone org
        OrganizationEntity localOrgOfGoaldoneRoot = organizationRepository
                .findByZitadelOrgId(goaldoneOrgId).orElse(null);
        if (localOrgOfGoaldoneRoot == null) {
            return false;
        }
        UserAccountEntity localSuperAdminAccount = accounts
                .stream()
                .filter(a -> a.getOrganizationId().equals(localOrgOfGoaldoneRoot.getId()))
                .findFirst()
                .orElse(null);
        if (localSuperAdminAccount == null) {
            return false;
        }

        // Check zitadel for the SUPER_ADMIN role
        List<AuthorizationServiceAuthorization> zitadelRecord = zitadelManagementClient.listGrantsForSpecificUser(
                goaldoneProjectId, localSuperAdminAccount.getZitadelSub()
        ).getAuthorizations();

        if(zitadelRecord.isEmpty()) {
            return false;
        }

        return zitadelRecord.stream().anyMatch(auth -> {
            if(auth.getRoles().isEmpty()) {
                return false;
            }
            List<String> roles = auth.getRoles().stream().map(AuthorizationServiceRole::getKey).toList();
            return roles.contains("SUPER_ADMIN");
        });
    }

    /**
     * Retrieves the list of user accounts associated with the currently logged-in user based on the provided JWT.
     * @param jwt JWT Token of the logged in user
     * @return List of user accounts associated with the current user
     */
    private List<UserAccountEntity> getAccountInformationOfCurrentLoggedInUser(Jwt jwt) {
        return userAccountRepository.findAllByUserIdentityId(findIdentityFromAccount(jwt));
    }
}
