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

@Slf4j
@Component
public class ZitadelManagementClient {

    private final RestClient restClient;
    private final String serviceAccountToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZitadelManagementClient(
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String managementApiUrl,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        this.restClient = restClientBuilder.baseUrl(managementApiUrl).build();
        this.serviceAccountToken = serviceAccountToken;
    }

    /**
     * POST /v2/users — search by email to check existence (instance-wide).
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
     * POST /v2/organizations/_search — search organizations by ID.
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
     * POST /management/v1/users/grants/_search — search user grants.
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
     * POST /management/v1/users/grants/_search — get role keys for a specific user in a project.
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
     * GET /v2/users/{userId} — get user details.
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
     * POST /v2/users/human — create a human user.
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
     * POST /management/v1/users/{userId}/grants — assign a project role.
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
     * POST /v2/users/{userId}/invite_code — send invitation email.
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
     * POST /v2/organizations — create a new organization.
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
