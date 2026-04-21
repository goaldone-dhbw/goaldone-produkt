package de.goaldone.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.exception.ZitadelApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ZitadelManagementClient {

    private final RestClient restClient;
    private final String serviceAccountToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZitadelManagementClient(
            @Value("${zitadel.management-api-url}") String managementApiUrl,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        this.restClient = RestClient.builder().baseUrl(managementApiUrl).build();
        this.serviceAccountToken = serviceAccountToken;
    }

    public boolean emailExists(String email) {
        try {
            String query = "queries[0][emailQuery][emailAddress]=" + encodeEmail(email) +
                    "&queries[0][emailQuery][method]=TEXT_QUERY_METHOD_EQUALS";
            String responseBody = restClient.get()
                    .uri("/v2/users?" + query)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("result")) {
                JsonNode resultArray = response.get("result");
                return resultArray != null && resultArray.size() > 0;
            }
            return false;
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to check email existence: " + e.getMessage(), e);
        }
    }

    public String addOrganization(String name) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("name", name);

            String responseBody = restClient.post()
                    .uri("/v2/organizations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("organizationId")) {
                return response.get("organizationId").asText();
            }
            throw new ZitadelApiException("No organizationId in Zitadel response");
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create organization in Zitadel: " + e.getMessage(), e);
        }
    }

    public String addHumanUser(String zitadelOrgId, String email) {
        try {
            Map<String, Object> emailObj = new HashMap<>();
            emailObj.put("email", email);
            emailObj.put("isVerified", false);

            Map<String, Object> body = new HashMap<>();
            body.put("email", emailObj);

            String responseBody = restClient.post()
                    .uri("/v2/users/human")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .header("x-zitadel-orgid", zitadelOrgId)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.has("userId")) {
                return response.get("userId").asText();
            }
            throw new ZitadelApiException("No userId in Zitadel response");
        } catch (Exception e) {
            throw new ZitadelApiException("Failed to create user in Zitadel: " + e.getMessage(), e);
        }
    }

    public void addUserGrant(String userId, String projectId, String roleKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("projectId", projectId);
            body.put("roleKeys", new String[]{roleKey});

            restClient.post()
                    .uri("/management/v1/users/{userId}/grants", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ZitadelApiException("Failed to add user grant in Zitadel: " + e.getMessage(), e);
        }
    }

    public void createInviteCode(String userId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sendCode", true);

            restClient.post()
                    .uri("/v2/users/{userId}/invite", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
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
        } catch (RestClientException e) {
            log.error("Failed to delete user {} during compensation: {}", userId, e.getMessage());
        }
    }

    private String encodeEmail(String email) {
        return email.replace("@", "%40").replace("+", "%2B").replace(" ", "%20");
    }
}
