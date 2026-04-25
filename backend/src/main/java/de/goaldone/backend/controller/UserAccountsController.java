package de.goaldone.backend.controller;

import de.goaldone.backend.api.UserAccountsApi;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.model.LinkConfirmRequest;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.service.AccountLinkingService;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserAccountsController implements UserAccountsApi {

    private final UserIdentityService userIdentityService;
    private final CurrentUserResolver currentUserResolver;
    private final AccountLinkingService accountLinkingService;

    @Override
    public ResponseEntity<AccountListResponse> getMyAccounts() {
        var jwt = currentUserResolver.extractJwt();
        AccountListResponse response = userIdentityService.buildAccountListResponse(jwt);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LinkTokenResponse> requestAccountLink() {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        LinkTokenResponse response = accountLinkingService.requestLink(currentAccount.getId());
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<Void> confirmAccountLink(LinkConfirmRequest request) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        accountLinkingService.confirmLink(request.getLinkToken(), currentAccount.getId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unlinkAccount(UUID accountId) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        accountLinkingService.unlink(currentAccount.getId(), accountId);
        return ResponseEntity.noContent().build();
    }
}
