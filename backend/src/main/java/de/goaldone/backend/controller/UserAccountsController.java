package de.goaldone.backend.controller;

import de.goaldone.backend.api.UserAccountsApi;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserAccountsController implements UserAccountsApi {

    private final UserIdentityService userIdentityService;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public ResponseEntity<AccountListResponse> getMyAccounts() {
        var jwt = currentUserResolver.extractJwt();
        AccountListResponse response = userIdentityService.buildAccountListResponse(jwt);
        return ResponseEntity.ok(response);
    }
}
