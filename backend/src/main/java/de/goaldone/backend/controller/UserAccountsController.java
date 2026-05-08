package de.goaldone.backend.controller;

import de.goaldone.backend.api.UserAccountsApi;
import de.goaldone.backend.model.*;
import de.goaldone.backend.service.AccountLinkingService;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.DeletionService;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final DeletionService deletionService;

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

    /**
     * Deletes an account by its ID.
     * @param accountId  (required) The unique identifier (UUID) of the account to delete
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     * @throws Exception if the account deletion fails or if the user does not have access to the account
     */
    @Override
    public ResponseEntity<Void> deleteAccount(UUID accountId) throws Exception {
        hasAccess(accountId);
        deletionService.deleteUser(accountId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates an account's details.
     * @param accountId  (required) The unique identifier (UUID) of the account to update
     * @param accountUpdateRequest  (required) The request body containing the updated account details
     * @return a {@link ResponseEntity} containing the updated {@link AccountResponse}
     * @throws Exception if the account update fails or if the user does not have access to the account
     */
    @Override
    public ResponseEntity<AccountResponse> updateAccount(UUID accountId, AccountUpdateRequest accountUpdateRequest) throws Exception {
        hasAccess(accountId);
        AccountResponse response = userIdentityService.updateAccount(accountId, accountUpdateRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Update the password of the current user account.
     * @param passwordUpdateRequest  (required) The request body containing the current password and the new password
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     * @throws Exception if the password update fails
     */
    @Override
    public ResponseEntity<Void> updateAccountPassword(PasswordUpdateRequest passwordUpdateRequest) throws Exception {
        UUID accountId = currentUserResolver.resolveCurrentAccount().getId();
        userIdentityService.updateAccountPassword(accountId, passwordUpdateRequest);
        return ResponseEntity.noContent().build();
    }

    /**
     * Checks if the current user has access to the specified account.
     * @param accountId local AccountId of the account to check access for
     */
    private void hasAccess(UUID accountId) {
        if(!userIdentityService.hasUserAccessToAccount(currentUserResolver.extractJwt(), accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this account");
        }
    }
}
