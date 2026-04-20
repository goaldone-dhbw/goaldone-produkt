package de.goaldone.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.goaldone.backend.config.ZitadelManagementProperties;
import de.goaldone.backend.exception.ZitadelUpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ZitadelManagementClient {
    private final RestClient restClient;
    private final ZitadelManagementProperties properties;
    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt;

    public ZitadelManagementClient(RestClient.Builder restClientBuilder, ZitadelManagementProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    /**
     * Get a valid access token using OAuth2 client credentials flow.
     */
    private String getAccessToken() {
        if (cachedAccessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }

        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (properties.getClientId() + ":" + properties.getClientSecret()).getBytes()
            );

            TokenResponse response = restClient
                    .post()
                    .uri("/oauth/v2/token")
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken == null) {
                throw new ZitadelUpstreamException("Failed to obtain access token from Zitadel");
            }

            cachedAccessToken = response.accessToken;
            tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn - 30);
            return cachedAccessToken;
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Failed to obtain access token: " + e.getMessage());
        }
    }

    /**
     * Add a human user to the Goaldone organization.
     * Returns the user ID.
     */
    public String addHumanUser(String email) {
        try {
            AddHumanUserRequest request = new AddHumanUserRequest(
                    new AddHumanUserRequest.Profile(email, null, null),
                    new AddHumanUserRequest.Email(email, true)
            );

            AddHumanUserResponse response = restClient
                    .post()
                    .uri("/v2/users/human")
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("x-zitadel-orgid", properties.getGoaldoneOrgId())
                    .body(request)
                    .retrieve()
                    .body(AddHumanUserResponse.class);

            if (response == null || response.userId == null) {
                throw new ZitadelUpstreamException("Failed to create human user: empty response");
            }

            return response.userId;
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while creating user: " + e.getMessage());
        }
    }

    /**
     * Add a user grant (role assignment) to a user.
     */
    public void addUserGrant(String userId, String projectId, String roleKey) {
        try {
            AddUserGrantRequest request = new AddUserGrantRequest(projectId, List.of(roleKey));

            restClient
                    .post()
                    .uri("/v2/users/{userId}/grants", userId)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("x-zitadel-orgid", properties.getGoaldoneOrgId())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while adding user grant: " + e.getMessage());
        }
    }

    /**
     * Create an invite code and optionally send it via email.
     */
    public void createInviteCode(String userId) {
        try {
            CreateInviteCodeRequest request = new CreateInviteCodeRequest(true);

            restClient
                    .post()
                    .uri("/v2/users/{userId}/invite", userId)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("x-zitadel-orgid", properties.getGoaldoneOrgId())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while creating invite code: " + e.getMessage());
        }
    }

    /**
     * Delete a user from Zitadel.
     */
    public void deleteUser(String userId) {
        try {
            restClient
                    .delete()
                    .uri("/v2/users/{userId}", userId)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("x-zitadel-orgid", properties.getGoaldoneOrgId())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new de.goaldone.backend.exception.UserNotFoundException("User not found in Zitadel");
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while deleting user: " + e.getMessage());
        }
    }

    /**
     * Search for users by email across all organizations.
     * Returns true if a user with the given email exists.
     */
    public boolean userExistsByEmail(String email) {
        try {
            ListUsersResponse response = restClient
                    .post()
                    .uri("/v2/users/_search")
                    .header("Authorization", "Bearer " + getAccessToken())
                    .body(new ListUsersRequest(new ListUsersRequest.Query(email)))
                    .retrieve()
                    .body(ListUsersResponse.class);

            return response != null && response.result != null && !response.result.isEmpty();
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while searching users: " + e.getMessage());
        }
    }

    /**
     * Get a user by ID.
     */
    public UserDetail getUserById(String userId) {
        try {
            GetUserResponse response = restClient
                    .get()
                    .uri("/v2/users/{userId}", userId)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("x-zitadel-orgid", properties.getGoaldoneOrgId())
                    .retrieve()
                    .body(GetUserResponse.class);

            if (response == null || response.user == null) {
                throw new de.goaldone.backend.exception.UserNotFoundException("User not found");
            }

            return response.user;
        } catch (HttpClientErrorException.NotFound e) {
            throw new de.goaldone.backend.exception.UserNotFoundException("User not found");
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while fetching user: " + e.getMessage());
        }
    }

    /**
     * List all super-admin grants in the Goaldone organization.
     */
    public GrantsListResponse listSuperAdminGrants() {
        try {
            ListGrantsRequest request = new ListGrantsRequest(
                    new ListGrantsRequest.Query(properties.getGoaldoneProjectId(), "SUPER_ADMIN")
            );

            GrantsListResponse response = restClient
                    .post()
                    .uri("/v2/users/grants/_search")
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("x-zitadel-orgid", properties.getGoaldoneOrgId())
                    .body(request)
                    .retrieve()
                    .body(GrantsListResponse.class);

            return response != null ? response : new GrantsListResponse(List.of(), 0);
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while listing grants: " + e.getMessage());
        }
    }

    /**
     * List organizations by ID to verify Goaldone org exists.
     */
    public OrganizationsListResponse listOrganizationsById(String orgId) {
        try {
            ListOrgsRequest request = new ListOrgsRequest(new ListOrgsRequest.Query(orgId));

            OrganizationsListResponse response = restClient
                    .post()
                    .uri("/v2/organizations/_search")
                    .header("Authorization", "Bearer " + getAccessToken())
                    .body(request)
                    .retrieve()
                    .body(OrganizationsListResponse.class);

            return response != null ? response : new OrganizationsListResponse(List.of(), 0);
        } catch (HttpServerErrorException e) {
            throw new ZitadelUpstreamException("Zitadel error while listing organizations: " + e.getMessage());
        }
    }

    // ========== DTOs ==========

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {}

    public record AddHumanUserRequest(
            Profile profile,
            Email email
    ) {
        record Profile(String userName, String givenName, String familyName) {}
        record Email(String email, boolean primary) {}
    }

    public record AddHumanUserResponse(
            @JsonProperty("user_id") String userId
    ) {}

    public record AddUserGrantRequest(
            @JsonProperty("project_id") String projectId,
            @JsonProperty("role_keys") List<String> roleKeys
    ) {}

    public record CreateInviteCodeRequest(
            @JsonProperty("send_code") boolean sendCode
    ) {}

    public record ListUsersRequest(Query query) {
        record Query(String loginName) {}
    }

    public record ListUsersResponse(
            List<UserDetail> result
    ) {}

    public record GetUserResponse(
            UserDetail user
    ) {}

    public record UserDetail(
            String userId,
            String loginName,
            String email,
            String firstName,
            String lastName,
            String state,
            @JsonProperty("created_at") String createdAt
    ) {}

    public record ListGrantsRequest(Query query) {
        record Query(
                @JsonProperty("project_id") String projectId,
                @JsonProperty("role_key") String roleKey
        ) {}
    }

    public record GrantsListResponse(
            List<Grant> result,
            long totalResult
    ) {
        public record Grant(
                @JsonProperty("user_id") String userId
        ) {}
    }

    public record ListOrgsRequest(Query query) {
        record Query(String id) {}
    }

    public record OrganizationsListResponse(
            List<Organization> result,
            long totalResult
    ) {
        record Organization(String id, String name) {}
    }
}
