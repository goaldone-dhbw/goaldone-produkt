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
     *
     * @param jwt The current web-token
     * @return The identityId for the current account
     */
    public UUID findIdentityFromAccount(Jwt jwt) {
        UserAccountEntity currentAccount = getCurrentAccount(jwt);
        return currentAccount.getUserIdentityId();
    }


    public List<UserAccountEntity> findAccountsForIdentity(UUID identityId) {
        return userAccountRepository.findAllByUserIdentityId(identityId);
    }

    /**
     *
     * @param jwt The current web-token
     * @return The current account from the token
     */
    private UserAccountEntity getCurrentAccount(Jwt jwt) {
        return userAccountRepository
                .findByZitadelSub(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("Account not found after JIT provisioning"));
    }


    public AccountListResponse buildAccountListResponse(Jwt jwt) {
        UserAccountEntity currentAccount = getCurrentAccount(jwt);

        List<UserAccountEntity> accounts = findAccountsForIdentity(currentAccount.getUserIdentityId());
        boolean hasConflicts = workingTimeRepository.hasConflictsForIdentity(currentAccount.getUserIdentityId());

        List<AccountResponse> responses = accounts.stream()
            .map(account -> {
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

                zitadelManagementClient.getUser(account.getZitadelSub()).ifPresent(userNode -> {
                    if (userNode.has("human")) {
                        var human = userNode.get("human");
                        if (human.has("email") && human.get("email").has("email")) {
                            r.setEmail(human.get("email").get("email").asText());
                        }
                        if (human.has("profile")) {
                            var profile = human.get("profile");
                            if (profile.has("givenName")) {
                                r.setFirstName(profile.get("givenName").asText());
                            }
                            if (profile.has("familyName")) {
                                r.setLastName(profile.get("familyName").asText());
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

    public List<UUID> accountIdsForUser(Jwt jwt) {
        return buildAccountListResponse(jwt).getAccounts().stream()
                .map(AccountResponse::getAccountId)
                .toList();
    }

    public boolean hasUserAccessToAccount(Jwt jwt, UUID accountId) {
        AccountListResponse accounts = buildAccountListResponse(jwt);

        return accounts.getAccounts().stream()
                .anyMatch(account -> account.getAccountId().equals(accountId));
    }
}
