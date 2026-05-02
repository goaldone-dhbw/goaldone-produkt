package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.model.AccountResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
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
                .findByAuthUserId(jwt.getSubject())
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
            .map(account -> {
                OrganizationEntity org = organizationRepository.findById(account.getOrganizationId())
                    .orElseThrow(() -> new IllegalStateException("Organization not found for account " + account.getId()));
                List<String> roles = zitadelManagementClient.getUserGrantRoles(
                        account.getAuthUserId(), goaldoneOrgId, goaldoneProjectId);
                AccountResponse r = new AccountResponse();
                r.setAccountId(account.getId());
                r.setOrganizationId(account.getOrganizationId());
                r.setOrganizationName(org.getName());
                r.setRoles(roles);
                r.setHasConflicts(hasConflicts);

                zitadelManagementClient.getUser(account.getAuthUserId()).ifPresent(user -> {
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
            })
            .toList();

        AccountListResponse response = new AccountListResponse();
        response.setAccounts(responses);
        return response;
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
}
