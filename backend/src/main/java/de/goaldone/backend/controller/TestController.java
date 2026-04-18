package de.goaldone.backend.controller;

import de.goaldone.backend.api.TestApi;
import de.goaldone.backend.client.ZitadelUserInfo;
import de.goaldone.backend.client.ZitadelUserInfoClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.UserInfoResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.service.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TestController implements TestApi {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final ZitadelUserInfoClient zitadelUserInfoClient;

    @Autowired
    private CurrentUserResolver currentUserResolver;

    @Override
    public ResponseEntity<UserInfoResponse> getCurrentUserInfo() throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken)) {
            throw new RuntimeException("JWT authentication required");
        }

        Jwt jwt = (Jwt) auth.getPrincipal();

        UserAccountEntity user = currentUserResolver.resolveCurrentAccount();

        OrganizationEntity org = currentUserResolver.resolveCurrentOrganization();


        List<String> roles = List.of();
        ZitadelUserInfo userInfo;
        UserInfoResponse response = new UserInfoResponse();
        if(jwt != null) {
            userInfo = zitadelUserInfoClient.getUserInfo(jwt.getTokenValue());
            roles = extractRoles(jwt);
            response.setZitadelSub(jwt.getSubject());
            response.setEmail(userInfo.email());
            response.setFirstName(JsonNullable.of(userInfo.givenName()));
            response.setLastName(JsonNullable.of(userInfo.familyName()));
        }
        response.setUserId(user.getId());
        response.setOrganizationId(user.getOrganizationId());
        response.setZitadelOrganizationId(org.getZitadelOrgId());
        response.setRoles(roles);

        return ResponseEntity.ok(response);
    }

    private List<String> extractRoles(Jwt jwt) {
        Map<String, Object> rolesClaim = jwt.getClaimAsMap("urn:zitadel:iam:org:project:roles");
        if (rolesClaim == null) {
            return List.of();
        }
        return rolesClaim.keySet().stream().toList();
    }
}
