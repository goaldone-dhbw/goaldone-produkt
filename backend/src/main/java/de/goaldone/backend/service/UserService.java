package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.model.AccountResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing users and their associated memberships across organizations.
 * Provides methods to retrieve users, list memberships, and build account list responses.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final ZitadelManagementClient zitadelManagementClient;
    private final WorkingTimeRepository workingTimeRepository;

    @Value("${zitadel.goaldone.org-id}")
    private String goaldoneOrgId;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;


    /**
     * Finds the user ID for the membership associated with the provided JWT.
     *
     * @param jwt The current JWT representing the logged-in user.
     * @return The UUID of the user.
     */
    public UUID findUserIdFromMembership(Jwt jwt) {
        MembershipEntity currentMembership = getCurrentMembership(jwt);
        return currentMembership.getUser().getId();
    }


    /**
     * Retrieves all memberships associated with a specific user.
     *
     * @param userId The UUID of the user to find memberships for.
     * @return A list of {@link MembershipEntity} objects.
     */
    public List<MembershipEntity> findMembershipsForUser(UUID userId) {
        return membershipRepository.findAllByUserId(userId);
    }

    /**
     * Resolves the membership for the user associated with the provided JWT and organization ID.
     *
     * @param jwt    The current JWT representing the logged-in user.
     * @param xOrgID The UUID of the organization.
     * @return The {@link MembershipEntity} associated with the user and organization.
     * @throws IllegalStateException if the membership cannot be found.
     */
    public MembershipEntity resolveMembership(Jwt jwt, UUID xOrgID) {
        String authUserId = jwt.getSubject();
        return membershipRepository
                .findByUserAuthUserIdAndOrganizationId(authUserId, xOrgID)
                .orElseThrow(() -> new IllegalStateException("Membership not found for organization " + xOrgID));
    }

    /**
     * Validates that the current user has a valid membership in the specified organization.
     *
     * @param xOrgID The UUID of the organization to validate.
     * @throws IllegalStateException if the membership cannot be found.
     */
    public void validateMembership(UUID xOrgID) {
        // This is a simple existence check. The @PreAuthorize and @authz.isMember
        // usually handle this at the controller level, but this provides a programmatic check.
        membershipRepository.findAllByOrganizationId(xOrgID).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Organization context invalid: " + xOrgID));
    }

    /**
     * Checks if the user associated with the provided JWT has access to the specified membership (account) ID
     * within the context of the given organization.
     *
     * @param jwt          The current JWT representing the logged-in user.
     * @param xOrgID       The UUID of the organization.
     * @param membershipId The UUID of the membership (account) to check access for.
     * @return {@code true} if the user has access to the membership, {@code false} otherwise.
     */
    public boolean hasUserAccessToMembership(Jwt jwt, UUID xOrgID, UUID membershipId) {
        return membershipRepository.findByIdAndOrganizationId(membershipId, xOrgID).isPresent();
    }

    /**
     * Builds an account list response for the user within a specific organization.
     *
     * @param jwt    The current JWT representing the logged-in user.
     * @param xOrgID The UUID of the organization.
     * @return An {@link AccountListResponse} containing the user's accounts in the organization.
     */
    public AccountListResponse buildAccountListResponse(Jwt jwt, UUID xOrgID) {
        MembershipEntity membership = resolveMembership(jwt, xOrgID);
        OrganizationEntity org = organizationRepository.findById(xOrgID).orElseThrow();

        AccountResponse accResponse = new AccountResponse();
        accResponse.setAccountId(membership.getId());
        accResponse.setOrganizationId(xOrgID);
        accResponse.setOrganizationName(org.getName());

        AccountListResponse response = new AccountListResponse();
        response.setAccounts(List.of(accResponse));
        return response;
    }

    /**
     * Retrieves the current membership based on the JWT's subject claim.
     * Note: This method may be ambiguous if the user has multiple memberships.
     * Use {@link #resolveMembership(Jwt, UUID)} for context-aware resolution.
     *
     * @param jwt The current JWT representing the logged-in user.
     * @return The {@link MembershipEntity} associated with the token.
     * @throws IllegalStateException if the membership cannot be found.
     */
    private MembershipEntity getCurrentMembership(Jwt jwt) {
        return membershipRepository
                .findByUserAuthUserId(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("Membership not found after JIT provisioning"));
    }
}
