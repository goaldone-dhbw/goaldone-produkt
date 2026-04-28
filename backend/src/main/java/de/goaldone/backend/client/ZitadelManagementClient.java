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
    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }
}
