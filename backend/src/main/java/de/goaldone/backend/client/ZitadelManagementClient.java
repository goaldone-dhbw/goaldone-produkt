package de.goaldone.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zitadel.ApiException;
import com.zitadel.Zitadel;
import com.zitadel.model.AuthorizationServiceAuthorizationsSearchFilter;
import com.zitadel.model.AuthorizationServiceIDFilter;
import com.zitadel.model.AuthorizationServiceListAuthorizationsRequest;
import com.zitadel.model.AuthorizationServiceListAuthorizationsResponse;
import com.zitadel.model.AuthorizationServicePaginationRequest;
import com.zitadel.model.OrganizationServiceAddOrganizationRequest;
import com.zitadel.model.OrganizationServiceAddOrganizationResponse;
import com.zitadel.model.OrganizationServiceDeleteOrganizationRequest;
import com.zitadel.model.OrganizationServiceListOrganizationsRequest;
import com.zitadel.model.OrganizationServiceOrganizationIDQuery;
import com.zitadel.model.OrganizationServiceSearchQuery;
import com.zitadel.model.UserServiceAddHumanUserRequest;
import com.zitadel.model.UserServiceAddHumanUserResponse;
import com.zitadel.model.UserServiceCreateInviteCodeRequest;
import com.zitadel.model.UserServiceDeleteUserRequest;
import com.zitadel.model.UserServiceEmailQuery;
import com.zitadel.model.UserServiceGetUserByIDRequest;
import com.zitadel.model.UserServiceGetUserByIDResponse;
import com.zitadel.model.UserServiceSetHumanEmail;
import com.zitadel.model.UserServiceSetHumanProfile;
import com.zitadel.model.UserServiceTextQueryMethod;
import com.zitadel.model.UserServiceInUserIDQuery;
import com.zitadel.model.UserServiceListUsersRequest;
import com.zitadel.model.UserServiceListUsersResponse;
import com.zitadel.model.UserServiceOrganization;
import com.zitadel.model.UserServiceSearchQuery;
import com.zitadel.model.UserServiceSendInviteCode;
import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.exception.ZitadelApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for interacting with the Zitadel Management API.
 * Provides methods for user management, organization management, and handling user grants
 * within the Zitadel identity management system.
 *
 * Methods that interact with v2 APIs use the official Zitadel Java SDK.
 * Methods that interact with Management v1 APIs (which lack SDK typed models) use RestClient with JSON parsing.
 */
@Slf4j
@Component
public class ZitadelManagementClient {

    private final Zitadel zitadel;
    private final RestClient restClient;
    private final String serviceAccountToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new ZitadelManagementClient.
     *
     * @param zitadel the Zitadel SDK client
     * @param restClientBuilder the builder used to create the RestClient for Management v1 APIs
     * @param managementApiUrl the base URL for the Zitadel Management API
     * @param serviceAccountToken the service account token used for authentication
     */
    public ZitadelManagementClient(
            Zitadel zitadel,
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String managementApiUrl,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        this.zitadel = zitadel;
        this.restClient = restClientBuilder.baseUrl(managementApiUrl).build();
        this.serviceAccountToken = serviceAccountToken;
    }

    public boolean emailExists(String email) {
        String normalizedEmail = normalizeEmail(email);
        try {
            log.debug("Checking email existence for: {}", normalizedEmail);
            UserServiceEmailQuery emailQuery = new UserServiceEmailQuery()
                    .emailAddress(normalizedEmail)
                    .method(UserServiceTextQueryMethod.TEXT_QUERY_METHOD_EQUALS);
            UserServiceListUsersRequest request = new UserServiceListUsersRequest()
                    .addQueriesItem(new UserServiceSearchQuery().emailQuery(emailQuery));
            UserServiceListUsersResponse response = zitadel.getUsers().listUsers(request);
            return response.getResult() != null && !response.getResult().isEmpty();
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to check email existence: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to check email existence for {}: {}", normalizedEmail, e.getMessage());
            throw new ZitadelApiException("Failed to check email existence: " + e.getMessage(), e);
        }
    }

    public boolean organizationExists(String orgId) {
        try {
            OrganizationServiceListOrganizationsRequest request = new OrganizationServiceListOrganizationsRequest()
                    .addQueriesItem(new OrganizationServiceSearchQuery()
                            .idQuery(new OrganizationServiceOrganizationIDQuery().id(orgId)));
            var response = zitadel.getOrganizations().listOrganizations(request);
            return response.getResult() != null && !response.getResult().isEmpty();
        } catch (Exception e) {
            log.error("Failed to check organization existence for {}: {}", orgId, e.getMessage());
            return false;
        }
    }

    public List<String> listUserIdsByRole(String orgId, String projectId, String roleKey) {
        try {
            Map<String, Object> roleQuery = Map.of(
                    "roleKey", roleKey,
                    "method", "TEXT_QUERY_METHOD_EQUALS"
            );
            Map<String, Object> projectQuery = Map.of("projectId", projectId);
            Map<String, Object> body = Map.of("queries", List.of(
                    Map.of("roleKeyQuery", roleQuery),
                    Map.of("projectIdQuery", projectQuery)
            ));
            String responseBody = restClient.post()
                    .uri("/management/v1/users/grants/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", orgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("result")) {
                return response.findValuesAsText("userId");
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to list user grants for role {} in project {}: {}", roleKey, projectId, e.getMessage());
            throw new ZitadelApiException("Failed to list authorizations: " + e.getMessage(), e);
        }
    }

    public AuthorizationServiceListAuthorizationsResponse listAllGrants(String rootOrgId, String projectId, String userOrgId) {
        try {
            AuthorizationServiceListAuthorizationsRequest request = new AuthorizationServiceListAuthorizationsRequest()
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .projectId(new AuthorizationServiceIDFilter().id(projectId)))
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .userOrganizationId(new AuthorizationServiceIDFilter().id(userOrgId)))
                    .pagination(new AuthorizationServicePaginationRequest().limit(1000));
            log.debug("List all grants request for project {} in user org {}", projectId, userOrgId);
            AuthorizationServiceListAuthorizationsResponse response = zitadel.getAuthorizations().listAuthorizations(request);
            log.debug("List all grants response: {} authorizations", response.getAuthorizations().size());
            return response;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list authorizations in project %s: HTTP %d", projectId, e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to list all authorizations: {}", e.getMessage());
            throw new ZitadelApiException("Failed to list all grants: " + e.getMessage(), e);
        }
    }

    public Optional<UserGrantDto> searchUserGrants(String rootOrgId, String projectId, String userId) {
        try {
            Map<String, Object> body = Map.of("queries", List.of(
                    Map.of("userIdQuery", Map.of("userId", userId)),
                    Map.of("projectIdQuery", Map.of("projectId", projectId))
            ));
            String responseBody = restClient.post()
                    .uri("/management/v1/users/grants/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", rootOrgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("result") && !response.get("result").isEmpty()) {
                JsonNode grantNode = response.get("result").get(0);
                String grantId = grantNode.has("id") ? grantNode.get("id").asText() : null;
                List<String> roleKeys = new ArrayList<>();
                if (grantNode.has("roleKeys")) {
                    grantNode.get("roleKeys").forEach(role -> roleKeys.add(role.asText()));
                }
                return Optional.of(new UserGrantDto(grantId, roleKeys));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to search user grant for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public void updateUserGrant(String grantId, String orgId, List<String> roleKeys) {
        try {
            Map<String, Object> body = Map.of("roleKeys", roleKeys);
            restClient.put()
                    .uri("/management/v1/users/grants/{grantId}", grantId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", orgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to update user grant: %s", e.getMessage());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to update user grant: " + e.getMessage(), e);
        }
    }

    public List<UserServiceUser> listUsersByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try {
            UserServiceListUsersRequest request = new UserServiceListUsersRequest()
                    .addQueriesItem(new UserServiceSearchQuery()
                            .inUserIdsQuery(new UserServiceInUserIDQuery().userIds(userIds)));
            log.debug("List users by IDs request for {} user IDs", userIds.size());
            UserServiceListUsersResponse response = zitadel.getUsers().listUsers(request);
            log.debug("List users by IDs response: {} users", response.getResult().size());
            return response.getResult();
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list users by IDs: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to list users by IDs: {}", e.getMessage());
            throw new ZitadelApiException("Failed to list users: " + e.getMessage(), e);
        }
    }

    public List<String> getUserGrantRoles(String userId, String orgId, String projectId) {
        try {
            Map<String, Object> body = Map.of("queries", List.of(
                    Map.of("userIdQuery", Map.of("userId", userId)),
                    Map.of("projectIdQuery", Map.of("projectId", projectId))
            ));
            String responseBody = restClient.post()
                    .uri("/management/v1/users/grants/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", orgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("result")) {
                List<String> roles = new ArrayList<>();
                response.get("result").forEach(grant -> {
                    if (grant.has("roleKeys")) {
                        grant.get("roleKeys").forEach(role -> roles.add(role.asText()));
                    }
                });
                return roles;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch roles for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public Optional<UserServiceUser> getUser(String userId) {
        try {
            UserServiceGetUserByIDRequest request = new UserServiceGetUserByIDRequest().userId(userId);
            UserServiceGetUserByIDResponse response = zitadel.getUsers().getUserByID(request);
            return Optional.ofNullable(response.getUser());
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return Optional.empty();
            }
            throw new ZitadelApiException("Failed to get user: HTTP " + e.getCode(), e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to get user: " + e.getMessage(), e);
        }
    }

    public String addHumanUser(String zitadelOrgId, String email, String firstName, String lastName) {
        String normalizedEmail = normalizeEmail(email);
        try {
            UserServiceAddHumanUserRequest request = new UserServiceAddHumanUserRequest()
                    .organization(new UserServiceOrganization().orgId(zitadelOrgId))
                    .profile(new UserServiceSetHumanProfile().givenName(firstName).familyName(lastName))
                    .email(new UserServiceSetHumanEmail().email(normalizedEmail).isVerified(false));
            UserServiceAddHumanUserResponse response = zitadel.getUsers().addHumanUser(request);
            return response.getUserId();
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to create user: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create user: " + e.getMessage(), e);
        }
    }

    public void addUserGrant(String userId, String mainOrgId, String projectId, String roleKey) {
        try {
            Map<String, Object> body = Map.of("projectId", projectId, "roleKeys", List.of(roleKey));
            restClient.post()
                    .uri("/management/v1/users/{userId}/grants", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", mainOrgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to add user grant: %s", e.getMessage());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (RestClientException e) {
            throw new ZitadelApiException("Failed to add user grant: " + e.getMessage(), e);
        }
    }

    public void createInviteCode(String userId) {
        try {
            UserServiceCreateInviteCodeRequest request = new UserServiceCreateInviteCodeRequest()
                    .sendCode(new UserServiceSendInviteCode());
            zitadel.getUsers().createInviteCode(request);
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to create invite code: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create invite code: " + e.getMessage(), e);
        }
    }

    public String addOrganization(String name) {
        try {
            OrganizationServiceAddOrganizationRequest request = new OrganizationServiceAddOrganizationRequest().name(name);
            OrganizationServiceAddOrganizationResponse response = zitadel.getOrganizations().addOrganization(request);
            log.debug("Created organization: {}", response.getOrganizationId());
            return response.getOrganizationId();
        } catch (ZitadelApiException e) {
            throw e;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to create organization: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create organization: " + e.getMessage(), e);
        }
    }

    public void deleteOrganization(String zitadelOrgId) {
        try {
            OrganizationServiceDeleteOrganizationRequest request = new OrganizationServiceDeleteOrganizationRequest();
            zitadel.getOrganizations().deleteOrganization(request);
        } catch (ApiException e) {
            log.error("Failed to delete organization {}: HTTP {}", zitadelOrgId, e.getCode());
        } catch (Exception e) {
            log.error("Failed to delete organization {}: {}", zitadelOrgId, e.getMessage());
        }
    }

    public void deleteUser(String userId) {
        try {
            UserServiceDeleteUserRequest request = new UserServiceDeleteUserRequest().userId(userId);
            zitadel.getUsers().deleteUser(request);
        } catch (ApiException e) {
            log.error("Failed to delete user {}: HTTP {}", userId, e.getCode());
        } catch (Exception e) {
            log.error("Failed to delete user {}: {}", userId, e.getMessage());
        }
    }

    public int countGrantsByRole(String orgId, String projectId, String roleKey) {
        try {
            Map<String, Object> body = Map.of("queries", List.of(
                    Map.of("projectIdQuery", Map.of("projectId", projectId)),
                    Map.of("roleKeyQuery", Map.of("roleKey", roleKey, "method", "TEXT_QUERY_METHOD_EQUALS"))
            ));
            String responseBody = restClient.post()
                    .uri("/management/v1/users/grants/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", orgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("details") && response.get("details").has("totalResult")) {
                return response.get("details").get("totalResult").asInt();
            }
            return 0;
        } catch (Exception e) {
            log.error("Failed to count grants by role {}: {}", roleKey, e.getMessage());
            return 0;
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }
}
