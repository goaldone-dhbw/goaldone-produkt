package de.goaldone.backend.controller;

import de.goaldone.backend.api.UserAccountsApi;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.model.LinkConfirmRequest;
import de.goaldone.backend.model.LinkConfirmResponse;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.service.AccountLinkingService;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing user accounts and account linking.
 * Provides endpoints for retrieving accounts, requesting and confirming account links, and unlinking accounts.
 */
@RestController
@RequiredArgsConstructor
public class UserAccountsController implements UserAccountsApi {

    private final UserIdentityService userIdentityService;
    private final CurrentUserResolver currentUserResolver;
    private final AccountLinkingService accountLinkingService;

    /**
     * Retrieves a list of all accounts associated with the currently authenticated user identity.
     *
     * @return a {@link ResponseEntity} containing an {@link AccountListResponse}
     */
    @Override
    public ResponseEntity<AccountListResponse> getMyAccounts() {
        var jwt = currentUserResolver.extractJwt();
        AccountListResponse response = userIdentityService.buildAccountListResponse(jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * Requests a link token for the current account to initiate an account linking process.
     *
     * @return a {@link ResponseEntity} containing a {@link LinkTokenResponse} with HTTP status 201 (Created)
     */
    @Override
    public ResponseEntity<LinkTokenResponse> requestAccountLink() {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        LinkTokenResponse response = accountLinkingService.requestLink(currentAccount.getId());
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Confirms the linking of an account using a previously requested link token.
     *
     * @param request the request object containing the link token
     * @return a {@link ResponseEntity} containing a {@link LinkConfirmResponse} indicating if conflicts were found
     */
    @Override
    public ResponseEntity<LinkConfirmResponse> confirmAccountLink(LinkConfirmRequest request) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        boolean hasConflicts = accountLinkingService.confirmLink(request.getLinkToken(), currentAccount.getId());
        LinkConfirmResponse response = new LinkConfirmResponse();
        response.setHasConflicts(hasConflicts);
        return ResponseEntity.ok(response);
    }

    /**
     * Unlinks a specific account from the current user identity.
     *
     * @param accountId the unique identifier (UUID) of the account to unlink
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     */
    @Override
    public ResponseEntity<Void> unlinkAccount(UUID accountId) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        accountLinkingService.unlink(currentAccount.getId(), accountId);
        return ResponseEntity.noContent().build();
    }
}
