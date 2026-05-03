package de.goaldone.backend.controller;

import de.goaldone.backend.api.UserAccountsApi;
import de.goaldone.backend.model.LinkConfirmRequest;
import de.goaldone.backend.model.LinkConfirmResponse;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.model.UserOrganizationsResponse;
import de.goaldone.backend.service.AccountLinkingService;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing user accounts and account linking.
 * Provides endpoints for retrieving organizations, requesting and confirming account links, and unlinking accounts.
 */
@RestController
@RequiredArgsConstructor
public class UserAccountsController implements UserAccountsApi {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;
    private final AccountLinkingService accountLinkingService;

    /**
     * Retrieves all organization memberships for the currently authenticated user.
     * Returns ALL organizations (not scoped to a single X-Org-ID).
     * Implements D-09/D-10: replaces the old getMyAccounts endpoint.
     *
     * @return a {@link ResponseEntity} containing a {@link UserOrganizationsResponse}
     */
    @Override
    public ResponseEntity<UserOrganizationsResponse> getMyOrganizations() {
        var jwt = currentUserResolver.extractJwt();
        UserOrganizationsResponse response = userService.buildOrganizationsResponse(jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * Requests a link token for the current account to initiate an account linking process.
     *
     * @param xOrgID the organization ID context for the request
     * @return a {@link ResponseEntity} containing a {@link LinkTokenResponse} with HTTP status 201 (Created)
     */
    @Override
    public ResponseEntity<LinkTokenResponse> requestAccountLink(@RequestHeader("X-Org-ID") UUID xOrgID) {
        var currentMembership = currentUserResolver.resolveCurrentMembership();
        LinkTokenResponse response = accountLinkingService.requestLink(currentMembership.getId());
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Confirms the linking of an account using a previously requested link token.
     *
     * @param xOrgID the organization ID context for the request
     * @param request the request object containing the link token
     * @return a {@link ResponseEntity} containing a {@link LinkConfirmResponse} indicating if conflicts were found
     */
    @Override
    public ResponseEntity<LinkConfirmResponse> confirmAccountLink(@RequestHeader("X-Org-ID") UUID xOrgID, LinkConfirmRequest request) {
        var currentMembership = currentUserResolver.resolveCurrentMembership();
        boolean hasConflicts = accountLinkingService.confirmLink(request.getLinkToken(), currentMembership.getId());
        LinkConfirmResponse response = new LinkConfirmResponse();
        response.setHasConflicts(hasConflicts);
        return ResponseEntity.ok(response);
    }

    /**
     * Unlinks a specific account from the current user identity.
     *
     * @param xOrgID the organization ID context for the request
     * @param accountId the unique identifier (UUID) of the account to unlink
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     */
    @Override
    public ResponseEntity<Void> unlinkAccount(@RequestHeader("X-Org-ID") UUID xOrgID, UUID accountId) {
        var currentMembership = currentUserResolver.resolveCurrentMembership();
        accountLinkingService.unlink(currentMembership.getId(), accountId);
        return ResponseEntity.noContent().build();
    }
}
