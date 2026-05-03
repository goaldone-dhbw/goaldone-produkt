package de.goaldone.backend.client;

import de.goaldone.backend.client.dto.AuthMemberResponse;
import de.goaldone.backend.model.MemberRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

/**
 * REST client for the auth-service management API.
 * Uses M2M OAuth2 client_credentials grant for authentication.
 */
@Slf4j
@Component
public class AuthServiceManagementClient {

    private final RestClient authServiceRestClient;

    public AuthServiceManagementClient(
            @Qualifier("authServiceRestClient") RestClient authServiceRestClient) {
        this.authServiceRestClient = authServiceRestClient;
    }

    /**
     * Creates a new invitation in the auth-service.
     */
    public void createInvitation(UUID companyId, String email, UUID inviterId, MemberRole role) {
        try {
            authServiceRestClient.post()
                    .uri("/api/v1/invitations")
                    .body(new InvitationRequestBody(email, companyId, inviterId, role != null ? role.getValue() : null))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Auth-service invitation failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthServiceManagementException("Failed to create invitation: " + e.getMessage(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new AuthServiceManagementException("Failed to create invitation", e);
        }
    }

    /**
     * Cancels (deletes) an invitation by token.
     */
    public void cancelInvitation(UUID token) {
        try {
            authServiceRestClient.delete()
                    .uri("/api/v1/invitations/{token}", token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Auth-service cancel invitation failed: status={}", e.getStatusCode());
            throw new AuthServiceManagementException("Failed to cancel invitation: " + e.getMessage(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new AuthServiceManagementException("Failed to cancel invitation", e);
        }
    }

    /**
     * Returns all members (active + invited) of an organization.
     */
    public List<AuthMemberResponse> getMembers(UUID companyId) {
        try {
            return authServiceRestClient.get()
                    .uri("/api/v1/organizations/{companyId}/members", companyId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AuthMemberResponse>>() {});
        } catch (RestClientResponseException e) {
            log.error("Auth-service getMembers failed: status={}", e.getStatusCode());
            throw new AuthServiceManagementException("Failed to get members: " + e.getMessage(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new AuthServiceManagementException("Failed to get members", e);
        }
    }

    /**
     * Deletes a membership. Throws with statusCode=409 if user is the last admin.
     */
    public void deleteMembership(UUID userId, UUID companyId) {
        try {
            authServiceRestClient.delete()
                    .uri("/api/v1/users/{userId}/memberships/{companyId}", userId, companyId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Auth-service deleteMembership failed: status={}", e.getStatusCode());
            throw new AuthServiceManagementException("Failed to delete membership: " + e.getMessage(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new AuthServiceManagementException("Failed to delete membership", e);
        }
    }

    /**
     * Updates a membership role. Throws with statusCode=409 if last-admin constraint is violated.
     */
    public void updateMembershipRole(UUID userId, UUID companyId, MemberRole newRole) {
        try {
            authServiceRestClient.patch()
                    .uri("/api/v1/users/{userId}/memberships/{companyId}?newRole={role}", userId, companyId, newRole.getValue())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Auth-service updateMembershipRole failed: status={}", e.getStatusCode());
            throw new AuthServiceManagementException("Failed to update membership role: " + e.getMessage(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new AuthServiceManagementException("Failed to update membership role", e);
        }
    }

    /**
     * Health check for the auth-service.
     */
    public boolean isReachable() {
        try {
            authServiceRestClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Auth-service health check failed: {}", e.getMessage());
            return false;
        }
    }

    private record InvitationRequestBody(String email, UUID companyId, UUID inviterId, String role) {}
}
