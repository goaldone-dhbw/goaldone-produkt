package de.goaldone.backend.controller;

import de.goaldone.backend.api.AccountLinkingApi;
import de.goaldone.backend.model.LinkConfirmRequest;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.service.AccountLinkingService;
import de.goaldone.backend.service.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccountLinkingController implements AccountLinkingApi {

    private final AccountLinkingService accountLinkingService;
    private final CurrentUserResolver currentUserResolver;

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
