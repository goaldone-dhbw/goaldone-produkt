package de.goaldone.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.exception.ZitadelApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final RestClient restClient;
    private final String serviceAccountToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new ZitadelManagementClient.
     *
     * @param restClientBuilder the builder used to create the RestClient
     * @param managementApiUrl the base URL for the Zitadel Management API
     * @param serviceAccountToken the service account token used for authentication
     */
    public ZitadelManagementClient(
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String managementApiUrl,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        this.restClient = restClientBuilder.baseUrl(managementApiUrl).build();
        this.serviceAccountToken = serviceAccountToken;
    }

    /**
     * Checks if a user with the specified email address exists instance-wide.
     *
     * @param email the email address to search for
     * @return true if a user with the given email exists, false otherwise
     * @throws ZitadelApiException if the API call fails
     */
    public boolean emailExists(String email) {
        String normalizedEmail = normalizeEmail(email);

        try {
            Map<String, Object> emailQuery = Map.of(
                    "emailAddress", normalizedEmail,
                    "method", "TEXT_QUERY_METHOD_EQUALS"
            );
            Map<String, Object> body = Map.of(
                    "queries", List.of(Map.of("emailQuery", emailQuery))
            );

            log.debug("Checking email existence for: {}", normalizedEmail);
            String responseBody = restClient.post()
                    .uri("/v2/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("result")) {
                JsonNode resultArray = response.get("result");
                return resultArray != null && !resultArray.isEmpty();
            }
            return false;
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to check email existence: %s: %s", e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            log.error("Failed to check email existence for {}: {}", normalizedEmail, e.getMessage());
            throw new ZitadelApiException("Failed to check email existence: " + e.getMessage(), e);
        }
    }
    /**
     * Checks if an organization with the specified ID exists.
     *
     * @param orgId the organization ID to search for
     * @return true if the organization exists, false otherwise
     */
    public boolean organizationExists(String orgId) {
        try {
            Map<String, Object> body = Map.of(
                    "queries", List.of(Map.of("idQuery", Map.of("id", orgId)))
            );

            String responseBody = restClient.post()
                    .uri("/v2/organizations/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("result")) {
                JsonNode resultArray = response.get("result");
                return resultArray != null && !resultArray.isEmpty();
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check organization existence for {}: {}", orgId, e.getMessage());
            return false;
        }
    }

    /**
     * Lists user IDs that are assigned a specific role within a given organization and project.
     *
     * @param orgId the organization ID
     * @param projectId the project ID
     * @param roleKey the role key to search for
     * @return a list of user IDs assigned to the role
     * @throws ZitadelApiException if the API call fails
     */
    public List<String> listUserIdsByRole(String orgId, String projectId, String roleKey) {
        try {
            Map<String, Object> roleQuery = Map.of(
                    "roleKey", roleKey,
                    "method", "TEXT_QUERY_METHOD_EQUALS"
            );
            Map<String, Object> projectQuery = Map.of(
                    "projectId", projectId
            );
            Map<String, Object> body = Map.of(
                    "queries", List.of(
                            Map.of("roleKeyQuery", roleQuery),
                            Map.of("projectIdQuery", projectQuery)
                    )
            );

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
            log.error("Failed to list user grants for role {} in project {} (org {}): {}", roleKey, projectId, orgId, e.getMessage());
            throw new ZitadelApiException("Failed to list authorizations: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the roles assigned to a specific user within a project and organization.
     *
     * @param userId the user ID
     * @param orgId the organization ID
     * @param projectId the project ID
     * @return a list of role keys assigned to the user
     */
    public List<String> getUserGrantRoles(String userId, String orgId, String projectId) {
        try {
            Map<String, Object> body = Map.of(
                    "queries", List.of(
                            Map.of("userIdQuery", Map.of("userId", userId)),
                            Map.of("projectIdQuery", Map.of("projectId", projectId))
                    )
            );

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
            log.warn("Failed to fetch roles for user {} in project {}: {}", userId, projectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Retrieves details for a specific user by their ID.
     *
     * @param userId the ID of the user to retrieve
     * @return an Optional containing the user details as a JsonNode, or empty if not found
     * @throws ZitadelApiException if the API call fails
     */
    public Optional<JsonNode> getUser(String userId) {
        try {
            String responseBody = restClient.get()
                    .uri("/v2/users/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            return Optional.ofNullable(response.get("user"));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new ZitadelApiException("Failed to get user: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to get user: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new human user in a specific organization.
     *
     * @param zitadelOrgId the ID of the organization to create the user in
     * @param email the user's email address
     * @param firstName the user's given name
     * @param lastName the user's family name
     * @return the ID of the newly created user
     * @throws ZitadelApiException if the creation fails or no user ID is returned
     */
    public String addHumanUser(String zitadelOrgId, String email, String firstName, String lastName) {
        String normalizedEmail = normalizeEmail(email);

        try {
            Map<String, Object> emailObj = Map.of(
                    "email", normalizedEmail,
                    "isVerified", false
            );
            Map<String, Object> orgObj = Map.of("orgId", zitadelOrgId);
            Map<String, Object> profileObj = Map.of(
                    "givenName", firstName,
                    "familyName", lastName
            );

            Map<String, Object> body = new HashMap<>();
            body.put("organization", orgObj);
            body.put("profile", profileObj);
            body.put("email", emailObj);

            String responseBody = restClient.post()
                    .uri("/v2/users/human")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", zitadelOrgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("userId")) {
                return response.get("userId").asText();
            }
            throw new ZitadelApiException("No userId in Zitadel response");
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to create user in Zitadel: %s: %s", e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create user in Zitadel: " + e.getMessage(), e);
        }
    }
    /**
     * Assigns a specific project role to a user.
     *
     * @param userId the user ID
     * @param mainOrgId the organization ID
     * @param projectId the project ID
     * @param roleKey the role key to assign
     * @throws ZitadelApiException if the operation fails
     */
    public void addUserGrant(String userId, String mainOrgId, String projectId, String roleKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("projectId", projectId);
            body.put("roleKeys", List.of(roleKey));

            restClient.post()
                    .uri("/management/v1/users/{userId}/grants", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", mainOrgId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to add user grant in Zitadel (v1): %s: %s", e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (RestClientException e) {
            throw new ZitadelApiException("Failed to add user grant in Zitadel: " + e.getMessage(), e);
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
            Map<String, Object> body = Map.of("sendCode", Map.of());

            restClient.post()
                    .uri("/v2/users/{userId}/invite_code", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to create invite code: %s: %s", e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (RestClientException e) {
            throw new ZitadelApiException("Failed to create invite code: " + e.getMessage(), e);
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
            Map<String, String> body = Map.of("name", name);

            String responseBody = restClient.post()
                    .uri("/v2/organizations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("Zitadel response: {}", responseBody);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("organizationId")) {
                return response.get("organizationId").asText();
            }
            throw new ZitadelApiException("No organizationId in Zitadel response");
        } catch (ZitadelApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to create organization in Zitadel: %s: %s", e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create organization in Zitadel: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes an organization by its ID.
     *
     * @param zitadelOrgId the ID of the organization to delete
     */
    public void deleteOrganization(String zitadelOrgId) {
        try {
            restClient.delete()
                    .uri("/v2/organizations/{orgId}", zitadelOrgId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Failed to delete organization {} during compensation: {}: {}", zitadelOrgId, e.getMessage(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("Failed to delete organization {} during compensation: {}", zitadelOrgId, e.getMessage());
        }
    }

    /**
     * Deletes a user by their ID.
     *
     * @param userId the ID of the user to delete
     */
    public void deleteUser(String userId) {
        try {
            restClient.delete()
                    .uri("/v2/users/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Failed to delete user {}: {}: {}", userId, e.getMessage(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("Failed to delete user {}: {}", userId, e.getMessage());
        }
    }
    /**
     * Lists all user IDs that have any project grant within a specific organization.
     * Unlike {@link #listUserIdsByRole}, this method does not filter by role.
     *
     * @param zitadelOrgId the Zitadel organization ID
     * @param projectId    the project ID
     * @return list of user IDs with any grant in the org+project
     * @throws ZitadelApiException if the API call fails
     */
    public List<String> listAllUserIdsForOrgProject(String zitadelOrgId, String projectId) {
        try {
            Map<String, Object> body = Map.of(
                    "queries", List.of(Map.of("projectIdQuery", Map.of("projectId", projectId)))
            );

            String responseBody = restClient.post()
                    .uri("/management/v1/users/grants/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", zitadelOrgId)
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
            log.error("Failed to list user grants for project {} in org {}: {}", projectId, zitadelOrgId, e.getMessage());
            throw new ZitadelApiException("Failed to list user grants: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the state of multiple users by their IDs.
     *
     * @param userIds the list of user IDs to query; returns an empty map if the list is empty
     * @return a map of userId to state string (e.g. {@code "USER_STATE_ACTIVE"}, {@code "USER_STATE_INITIAL"})
     * @throws ZitadelApiException if the API call fails
     */
    public Map<String, String> getUserStates(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Object> body = Map.of(
                    "queries", List.of(Map.of("inUserIdsQuery", Map.of("userIds", userIds)))
            );

            String responseBody = restClient.post()
                    .uri("/v2/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            Map<String, String> result = new java.util.HashMap<>();
            if (response != null && response.has("result")) {
                for (JsonNode user : response.get("result")) {
                    String id = user.path("userId").asText(null);
                    String state = user.path("state").asText(null);
                    if (id != null && state != null) {
                        result.put(id, state);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to get user states: {}", e.getMessage());
            throw new ZitadelApiException("Failed to get user states: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all users that belong to a specific Zitadel organization, including their state.
     *
     * @param zitadelOrgId the Zitadel organization ID
     * @return list of {@link ZitadelUserInfo} records with user ID and state
     * @throws ZitadelApiException if the API call fails
     */
    public List<ZitadelUserInfo> listUsersInOrganization(String zitadelOrgId) {
        try {
            Map<String, Object> body = Map.of(
                    "queries", List.of(Map.of("organizationIdQuery", Map.of("organizationId", zitadelOrgId)))
            );

            String responseBody = restClient.post()
                    .uri("/v2/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            List<ZitadelUserInfo> users = new ArrayList<>();
            if (response != null && response.has("result")) {
                for (JsonNode user : response.get("result")) {
                    String id = user.path("userId").asText(null);
                    String state = user.path("state").asText(null);
                    if (id != null) {
                        users.add(new ZitadelUserInfo(id, state != null ? state : ""));
                    }
                }
            }
            return users;
        } catch (Exception e) {
            log.error("Failed to list users in organization {}: {}", zitadelOrgId, e.getMessage());
            throw new ZitadelApiException("Failed to list users in organization: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a user by their ID, throwing an exception if the deletion fails.
     * Use this method when the caller needs to detect and handle deletion failures.
     * For best-effort cleanup (compensation), use {@link #deleteUser(String)} instead.
     *
     * @param userId the ID of the user to delete
     * @throws ZitadelApiException if the deletion fails
     */
    public void deleteUserOrThrow(String userId) {
        try {
            restClient.delete()
                    .uri("/v2/users/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to delete user %s: %s: %s", userId, e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (RestClientException e) {
            String errorMsg = String.format("Failed to delete user %s: %s", userId, e.getMessage());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        }
    }

    /**
     * Deletes a Zitadel organization by its ID, throwing an exception if the deletion fails.
     * Use this method when the caller needs to detect and handle deletion failures.
     * For best-effort cleanup (compensation), use {@link #deleteOrganization(String)} instead.
     *
     * @param zitadelOrgId the ID of the organization to delete
     * @throws ZitadelApiException if the deletion fails
     */
    public void deleteOrganizationOrThrow(String zitadelOrgId) {
        try {
            restClient.delete()
                    .uri("/v2/organizations/{orgId}", zitadelOrgId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to delete organization %s: %s: %s", zitadelOrgId, e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (RestClientException e) {
            String errorMsg = String.format("Failed to delete organization %s: %s", zitadelOrgId, e.getMessage());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        }
    }

    /**
     * Lists all organizations in Zitadel without any filter.
     * Uses a high page size; for very large deployments a pagination loop should be added.
     *
     * @return list of {@link ZitadelOrgInfo} for every active organization
     * @throws ZitadelApiException if the API call fails
     */
    public List<ZitadelOrgInfo> listAllOrganizations() {
        try {
            Map<String, Object> body = Map.of("queries", List.of());

            String responseBody = restClient.post()
                    .uri("/v2/organizations/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            List<ZitadelOrgInfo> orgs = new ArrayList<>();
            log.info("Zitadel response: {}", responseBody);
            if (response != null && response.has("result")) {
                for (JsonNode org : response.get("result")) {
                    String id = org.path("id").asText(null);
                    String name = org.path("name").asText(null);
                    String creationDateStr = org.path("details").path("creationDate").asText(null);
                    OffsetDateTime creationDate = null;
                    if (creationDateStr != null && !creationDateStr.isEmpty()) {
                        try {
                            creationDate = OffsetDateTime.parse(creationDateStr);
                        } catch (Exception e) {
                            log.warn("Could not parse creationDate '{}' for org {}: {}", creationDateStr, id, e.getMessage());
                        }
                    }
                    if (id != null) {
                        orgs.add(new ZitadelOrgInfo(id, name != null ? name : "", creationDate));
                    }
                }
            }
            log.info("Found {} organizations", orgs.size());
            return orgs;
        } catch (Exception e) {
            log.error("Failed to list all organizations: {}", e.getMessage());
            throw new ZitadelApiException("Failed to list all organizations: " + e.getMessage(), e);
        }
    }

    /**
     * Get Org Info for one zitadelOrgId
     *
     * @param zitadelOrgId the zitadel org id
     * @return {@link ZitadelOrgInfo} for the provided zitadel org id
     */
    public ZitadelOrgInfo getOrgInfo(String zitadelOrgId) {
        try {
            Map<String, Object> body = Map.of("queries", List.of(
                    Map.of("idQuery", Map.of("id", zitadelOrgId))
            ));

            String responseBody = restClient.post()
                    .uri("/v2/organizations/_search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            log.info("Zitadel response: {}", responseBody);
            if (response != null && response.has("result")) {
                JsonNode resultArray = response.get("result");
                if (resultArray != null && !resultArray.isEmpty()) {
                    if (resultArray.size() > 1) {
                        log.warn("Expected exactly one organization for id {}, but got {}. Returning first result.", zitadelOrgId, resultArray.size());
                    }

                    JsonNode org = resultArray.get(0);
                    String id = org.path("id").asText(null);
                    String name = org.path("name").asText(null);
                    String creationDateStr = org.path("details").path("creationDate").asText(null);
                    OffsetDateTime creationDate = null;
                    if (creationDateStr != null && !creationDateStr.isEmpty()) {
                        try {
                            creationDate = OffsetDateTime.parse(creationDateStr);
                        } catch (Exception e) {
                            log.warn("Could not parse creationDate '{}' for org {}: {}", creationDateStr, id, e.getMessage());
                        }
                    }
                    if (id != null) {
                        return new ZitadelOrgInfo(id, name != null ? name : "", creationDate);
                    }
                }
            }
            throw new ZitadelApiException("No organization found for id: " + zitadelOrgId);
        } catch (Exception e) {
            log.error("Failed to get org info: {}", e.getMessage());
            throw new ZitadelApiException("Failed to get org info: " + e.getMessage(), e);
        }
    }


    /**
     * Immutable value type representing a Zitadel organization.
     *
     * @param id           the Zitadel organization ID
     * @param name         the organization name
     * @param creationDate the organization creation date, or {@code null} if unavailable
     */
    public record ZitadelOrgInfo(String id, String name, java.time.OffsetDateTime creationDate) {}

    /**
     * Immutable value type representing a Zitadel user with their current state.
     *
     * @param id    the Zitadel user ID
     * @param state the user state (e.g. {@code "USER_STATE_ACTIVE"}, {@code "USER_STATE_INITIAL"})
     */
    public record ZitadelUserInfo(String id, String state) {}

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }
}
