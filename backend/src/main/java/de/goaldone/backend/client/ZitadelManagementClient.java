package de.goaldone.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zitadel.ApiException;
import com.zitadel.Zitadel;
import com.zitadel.model.*;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.AccountUpdateRequest;
import de.goaldone.backend.model.MemberRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for interacting with the Zitadel Management API.
 * Provides methods for user management, organization management, and handling user grants
 * within the Zitadel identity management system.
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
     * @param zitadel             the Zitadel SDK client
     * @param restClientBuilder   the builder used to create the RestClient for Management v1 APIs
     * @param managementApiUrl    the base URL for the Zitadel Management API
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

    /**
     * Check if a user with the given email exists in the Zitadel system.
     *
     * @param email the email address of the user to check
     * @return true if the user exists, false otherwise
     */
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

    /**
     * Check if a org with the given ID exists in the Zitadel system.
     *
     * @param orgId the ID of the organization to check
     * @return true if the organization exists, false otherwise
     */
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

    /**
     * Check if a project with the given ID exists in the Zitadel system.
     * @param zitadelOrgId the ID of the organization containing the project (root org for our case)
     * @return A list with users that are members of the organization
     */
    public UserServiceListUsersResponse listUsersOfOrg(String zitadelOrgId) {
        try {
            UserServiceListUsersRequest request = new UserServiceListUsersRequest()
                    .addQueriesItem(new UserServiceSearchQuery()
                            .organizationIdQuery(new UserServiceOrganizationIdQuery().organizationId(zitadelOrgId)));
            return zitadel.getUsers().listUsers(request);
        } catch (Exception e) {
            log.error("Failed to list users of org {}: {}", zitadelOrgId, e.getMessage());
            throw new ZitadelApiException("Failed to list users of org: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user is last admin in org.
     */
    public Map<String, List<MemberRole>> listUsersWithTheirRoles(String zitadelOrgId, String projectId) {
        try {
            AuthorizationServiceListAuthorizationsRequest request = new AuthorizationServiceListAuthorizationsRequest()
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .projectId(new AuthorizationServiceIDFilter().id(projectId)))
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .userOrganizationId(new AuthorizationServiceIDFilter().id(zitadelOrgId)))
                    .pagination(new AuthorizationServicePaginationRequest().limit(1000));

            AuthorizationServiceListAuthorizationsResponse response = zitadel.getAuthorizations().listAuthorizations(request);
            Map<String, List<MemberRole>> userRolesMap = new java.util.HashMap<>();
            if (response.getAuthorizations() != null) {
                for (AuthorizationServiceAuthorization auth : response.getAuthorizations()) {
                    AuthorizationServiceUser user = auth.getUser();

                    if (user == null) continue;

                    String userId = user.getId();

                    if(auth.getRoles() == null || auth.getRoles().isEmpty()) continue;

                    List<MemberRole> roles = auth.getRoles()
                            .stream()
                            .map(role -> MemberRole.fromValue(role.getKey()))
                            .toList();
                    userRolesMap.put(userId, roles);
                }
            }
            return userRolesMap;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list authorizations in project %s: HTTP %d", projectId, e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to list all authorizations: {}", e.getMessage());
            throw new ZitadelApiException("Failed to list all grants: " + e.getMessage(), e);
        }
    }

    /**
     * ListUserIdsByRole returns a list of user IDs that have a specific role in a project.
     *
     * @param orgId     the ID of the organization containing the project (root org for our case)
     * @param projectId the ID of the project to search for role grants (root Goaldone Project for our case)
     * @param roleKey   the key of the role to search for (SUPER_ADMIN, COMPANY_ADMIN, USER)
     * @return List of user IDs that have the specified role in the project
     */
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

    /**
     * Check if a user has a specific role in a project.
     */
    public AuthorizationServiceListAuthorizationsResponse listGrantsForSpecificUser(String projectId, String userId) {
        try {
            AuthorizationServiceListAuthorizationsRequest request = new AuthorizationServiceListAuthorizationsRequest()
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .projectId(new AuthorizationServiceIDFilter().id(projectId)))
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .inUserIds(new AuthorizationServiceInIDsFilter().ids(List.of(userId))))
                    .pagination(new AuthorizationServicePaginationRequest().limit(1000));

            AuthorizationServiceListAuthorizationsResponse response = zitadel.getAuthorizations().listAuthorizations(request);
            log.debug("List grants for user {} in project {}: {} authorizations", userId, projectId, response.getAuthorizations() != null ? response.getAuthorizations().size() : 0);
            return response;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list authorizations: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to list user grants for user {}: {}", userId, e.getMessage());
            throw new ZitadelApiException("Failed to list user grants: " + e.getMessage(), e);
        }
    }

    /**
     * List all grants for a user in a project
     *
     * @param projectId the ID of the project to search for grants (Goaldone Project)
     * @param userOrgId the ID of the user organization (Goaldone User)
     * @return List of all grants for the user in the project
     */
    public AuthorizationServiceListAuthorizationsResponse listAllGrants(String projectId, String userOrgId) {
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

    /**
     * List all grants for a specific user in a project
     *
     * @param zitadelProjectId the ID of the project to search for grants (Goaldone Project)
     * @param zitadelUserOrgId the ID of the user organization (Goaldone User)
     * @param zitadelUserId    the ID of the user to search for grants
     * @return List of all grants for the user in the project
     */
    public AuthorizationServiceListAuthorizationsResponse listAllGrants(String zitadelProjectId, String zitadelUserOrgId, String zitadelUserId) {
        try {
            AuthorizationServiceListAuthorizationsRequest request = new AuthorizationServiceListAuthorizationsRequest()
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .projectId(new AuthorizationServiceIDFilter().id(zitadelProjectId)))
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .userOrganizationId(new AuthorizationServiceIDFilter().id(zitadelUserOrgId)))
                    .addFiltersItem(new AuthorizationServiceAuthorizationsSearchFilter()
                            .userOrganizationId(new AuthorizationServiceIDFilter().id(zitadelUserId)))
                    .pagination(new AuthorizationServicePaginationRequest().limit(1000));
            log.debug("List all grants request for project {} in user org {} for user {}", zitadelProjectId, zitadelUserOrgId, zitadelUserId);
            AuthorizationServiceListAuthorizationsResponse response = zitadel.getAuthorizations().listAuthorizations(request);
            log.debug("List all grants for user {} in response: {} authorizations", zitadelUserId, response.getAuthorizations().size());
            return response;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list authorizations in project %s: HTTP %d", zitadelProjectId, e.getCode());
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

    /**
     * Update the role of a project authorization.
     *
     * @param zitadelAuthorizationId the ID of the project authorization to update
     * @param roleKey                the new role key to assign to the project authorization
     */
    public void updateProjectAuthorization(String zitadelAuthorizationId, String roleKey) {
        try {
            AuthorizationServiceUpdateAuthorizationRequest request = new AuthorizationServiceUpdateAuthorizationRequest()
                    .id(zitadelAuthorizationId)
                    .roleKeys(List.of(roleKey));

            AuthorizationServiceUpdateAuthorizationResponse response = zitadel.getAuthorizations().updateAuthorization(request);
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to update project authorization: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to update project authorization: " + e.getMessage(), e);
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

    /**
     * Get user information about a list of users
     * @param userIds a list of user IDs to get information about
     * @return List of user information objects, one for each user ID provided
     */
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

    /**
     * Update a user's profile.
     * @param userId the ID of the user to update
     * @param updateRequest the update request containing the new profile information
     * @return the updated user information
     */
    public void updateUser(String userId, AccountUpdateRequest updateRequest) {
        try {
            UserServiceUpdateUserRequest request = new UserServiceUpdateUserRequest();
            request.userId(userId);

            UserServiceHuman humanUser = new UserServiceHuman();

            // Handle Email
            if (updateRequest.getEmail() != null) {
                humanUser.setEmail(new UserServiceSetHumanEmail()
                        .email(updateRequest.getEmail())
                        .sendCode(new UserServiceSendEmailVerificationCode()));
            }

            // Handle Profile (First Name / Last Name)
            if (updateRequest.getFirstName() != null || updateRequest.getLastName() != null) {
                UserServiceProfile profile = new UserServiceProfile();

                if (updateRequest.getFirstName() != null) {
                    profile.setGivenName(updateRequest.getFirstName());
                }
                if (updateRequest.getLastName() != null) {
                    profile.setFamilyName(updateRequest.getLastName());
                }

                humanUser.setProfile(profile);
            }

            request.human(humanUser);

            UserServiceUpdateUserResponse response = zitadel.getUsers().updateUser(request);
            log.info("Updated user {} successfully at {}", userId, response.getChangeDate());

        } catch (ZitadelApiException e) {
            throw new ZitadelApiException("Failed to update user: HTTP " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Update a user's password.
     * @param userId the ID of the user to update
     * @param currentPassword the current password of the user
     * @param newPassword the new password to set for the user
     */
    public void updateUserPassword(String userId, String currentPassword, String newPassword) {
        try {
            UserServiceUpdateUserRequest request = new UserServiceUpdateUserRequest()
                    .userId(userId)
                    .human(new UserServiceHuman()
                            .password(new UserServiceSetPassword()
                                    .currentPassword(currentPassword)
                                    .password(new UserServicePassword()
                                            .password(newPassword)
                                            .changeRequired(false)
                                    ))
                            );
            zitadel.getUsers().updateUser(request);
        } catch (ApiException e) {
            log.error("Failed to update user password: HTTP {}", e.getCode());
            throw new ZitadelApiException("Failed to update user password: HTTP " + e.getCode(), e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to update user password: " + e.getMessage(), e);
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

    /**
     * Generates an invitation code for a user and triggers an invitation email.
     *
     * @param userId the ID of the user to invite
     * @throws ZitadelApiException if the operation fails
     */
    public void createInviteCode(String userId) {
        try {
            UserServiceCreateInviteCodeRequest request = new UserServiceCreateInviteCodeRequest()
                    .userId(userId)
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

    /**
     * Lists all organizations in Zitadel
     * @return a list of all organizations in Zitadel
     */
    public OrganizationServiceListOrganizationsResponse listOrganizations() {
        try {
            OrganizationServiceListOrganizationsRequest request = new OrganizationServiceListOrganizationsRequest();
            OrganizationServiceListOrganizationsResponse response = zitadel.getOrganizations().listOrganizations(request);
            log.debug("List all organizations response: {} organizations", response.getResult() != null ? response.getResult().size() : 0);
            return response;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list organizations: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to list organizations: {}", e.getMessage());
            throw new ZitadelApiException("Failed to list organizations: " + e.getMessage(), e);
        }
    }

    /**
     * Get information about an organization by its ID.
     * @param zitadelOrgId the ID of the organization to get information about
     * @return the organization information if found, otherwise an empty response
     */
    public OrganizationServiceListOrganizationsResponse getOrganizationInfoById(String zitadelOrgId) {
        try {
            OrganizationServiceListOrganizationsRequest request = new OrganizationServiceListOrganizationsRequest().addQueriesItem(
                    new OrganizationServiceSearchQuery().idQuery(new OrganizationServiceOrganizationIDQuery().id(zitadelOrgId))
            );
            OrganizationServiceListOrganizationsResponse response = zitadel.getOrganizations().listOrganizations(request);
            log.debug("List organization response for {}, response: {}", zitadelOrgId ,response.getResult() != null ? response.getResult().size() : 0);
            return response;
        } catch (ApiException e) {
            String errorMsg = String.format("Failed to list organizations: HTTP %d", e.getCode());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to list information about organization: {}, error: {}", zitadelOrgId, e.getMessage());
            throw new ZitadelApiException("Failed to list organizations: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new organization in Zitadel.
     *
     * @param name the name of the organization to create
     * @return the ID of the newly created organization
     * @throws ZitadelApiException if the creation fails or no organization ID is returned
     */
    public String addOrganization(String name) {
        try {
            OrganizationServiceAddOrganizationRequest request = new OrganizationServiceAddOrganizationRequest()
                    .name(name);
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

    /**
     * Deletes an organization by its ID.
     *
     * @param zitadelOrgId the ID of the organization to delete
     */
    public void deleteOrganization(String zitadelOrgId) {
        try {
            OrganizationServiceDeleteOrganizationRequest request = new OrganizationServiceDeleteOrganizationRequest()
                    .organizationId(zitadelOrgId);
            zitadel.getOrganizations().deleteOrganization(request);
        } catch (ApiException e) {
            log.error("Failed to delete organization {}: HTTP {}", zitadelOrgId, e.getCode());
        } catch (Exception e) {
            log.error("Failed to delete organization {}: {}", zitadelOrgId, e.getMessage());
        }
    }

    /**
     * Deletes a user by their ID.
     *
     * @param userId the ID of the user to delete
     */
    public void deleteUser(String userId) {
        try {
            UserServiceDeleteUserRequest request = new UserServiceDeleteUserRequest()
                    .userId(userId);
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
