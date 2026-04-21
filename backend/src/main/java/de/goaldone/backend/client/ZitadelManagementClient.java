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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ZitadelManagementClient {

    private final RestClient restClient;
    private final String serviceAccountToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZitadelManagementClient(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String managementApiUrl,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        this.restClient = RestClient.builder().baseUrl(managementApiUrl).build();
        this.serviceAccountToken = serviceAccountToken;
    }

    /**
     * POST /v2/users — search by email to check existence.
     */
    public boolean emailExists(String email) {
        try {
            Map<String, Object> emailQuery = Map.of(
                    "emailAddress", email,
                    "method", "TEXT_QUERY_METHOD_EQUALS"
            );
            Map<String, Object> body = Map.of(
                    "queries", List.of(Map.of("emailQuery", emailQuery))
            );

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
            throw new ZitadelApiException("Failed to check email existence: " + e.getMessage(), e);
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

    /**
     * POST /v2/users/human — create a human user in the given organization.
     * Organization is passed both in the request body and via header.
     */
    public String addHumanUser(String zitadelOrgId, String email, String firstName, String lastName) {
        try {
            Map<String, Object> emailObj = Map.of(
                    "email", email,
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
            log.info("Zitadel user creation response: {}", responseBody);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("userId")) {
                return response.get("userId").asText();
            }
            throw new ZitadelApiException("No userId in Zitadel response");
        } catch (ZitadelApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String errorMsg = String.format("Failed to create user in Zitadel: %s: %s", e.getMessage(), e.getResponseBodyAsString());
            log.error(errorMsg);
            throw new ZitadelApiException(errorMsg, e);
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create user in Zitadel: " + e.getMessage(), e);
        }
    }

    /**
     * POST /management/v1/users/{userId}/grants — assign a project role to a user.
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
     * POST /v2/users/{userId}/invite_code — send an invitation email to the user.
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
            log.error("Failed to delete user {} during compensation: {}: {}", userId, e.getMessage(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("Failed to delete user {} during compensation: {}", userId, e.getMessage());
        }
    }
}
