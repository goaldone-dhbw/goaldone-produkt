package de.goaldone.backend.controller;

import de.goaldone.backend.api.TestApi;
import de.goaldone.backend.client.ZitadelUserInfo;
import de.goaldone.backend.client.ZitadelUserInfoClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.model.UserInfoResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
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

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ZitadelUserInfoClient zitadelUserInfoClient;

    @Override
    public ResponseEntity<UserInfoResponse> getCurrentUserInfo() throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken)) {
            throw new RuntimeException("JWT authentication required");
        }

        Jwt jwt = (Jwt) auth.getPrincipal();

        UserEntity user = userRepository.findByZitadelSub(jwt.getSubject())
            .orElseThrow(() -> new RuntimeException("User not found after JIT provisioning"));

        OrganizationEntity org = organizationRepository.findById(user.getOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        ZitadelUserInfo userInfo = zitadelUserInfoClient.getUserInfo(jwt.getTokenValue());
        List<String> roles = extractRoles(jwt);

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getId());
        response.setZitadelSub(jwt.getSubject());
        response.setEmail(userInfo.email());
        response.setFirstName(JsonNullable.of(userInfo.givenName()));
        response.setLastName(JsonNullable.of(userInfo.familyName()));
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
