package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.model.AccountResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserIdentityService {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;


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

        List<AccountResponse> responses = accounts.stream()
            .map(account -> {
                OrganizationEntity org = organizationRepository.findById(account.getOrganizationId())
                    .orElseThrow(() -> new IllegalStateException("Organization not found for account " + account.getId()));
                AccountResponse r = new AccountResponse();
                r.setAccountId(account.getId());
                r.setOrganizationId(account.getOrganizationId());
                r.setOrganizationName(org.getName());
                return r;
            })
            .toList();

        AccountListResponse response = new AccountListResponse();
        response.setAccounts(responses);
        return response;
    }
}
