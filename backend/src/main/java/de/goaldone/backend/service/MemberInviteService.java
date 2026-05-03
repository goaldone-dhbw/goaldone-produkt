package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.exception.EmailAlreadyInUseException;
import de.goaldone.backend.exception.NotMemberOfOrganizationException;
import de.goaldone.backend.exception.UserAlreadyActiveException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberInviteService {

    private final ZitadelManagementClient zitadelManagementClient;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserService userService;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    /**
     * Invites a new member to an organization.
     *
     * @param xOrgID  The organization ID context.
     * @param request The invite request.
     */
    public void inviteMember(UUID xOrgID, InviteMemberRequest request) {
        userService.validateMembership(xOrgID);

        if (zitadelManagementClient.emailExists(request.getEmail())) {
            throw new EmailAlreadyInUseException(request.getEmail());
        }

        OrganizationEntity organization = organizationRepository.findById(xOrgID)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + xOrgID));

        String zitadelUserId = null;
        try {
            zitadelUserId = zitadelManagementClient.addHumanUser(
                    organization.getAuthCompanyId(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName()
            );

            String roleKey = request.getRole() != null ? request.getRole().getValue() : MemberRole.USER.getValue();
            zitadelManagementClient.addUserGrant(
                    zitadelUserId,
                    mainOrgId,
                    goaldoneProjectId,
                    roleKey
            );

            zitadelManagementClient.createInviteCode(zitadelUserId);

        } catch (Exception e) {
            log.error("Failed to invite member. Triggering compensation for user: {}", zitadelUserId, e);
            if (zitadelUserId != null) {
                zitadelManagementClient.deleteUser(zitadelUserId);
            }
            if (e instanceof ZitadelApiException) {
                throw e;
            }
            throw new ZitadelApiException("Failed to invite member: " + e.getMessage(), e);
        }
    }

    /**
     * Resends an invite code to a member who hasn't completed their registration.
     *
     * @param xOrgID         The organization ID context.
     * @param zitadelUserId The identity provider ID of the member.
     */
    public void reinviteMember(UUID xOrgID, String zitadelUserId) {
        userService.validateMembership(xOrgID);

        var userOpt = zitadelManagementClient.getUser(zitadelUserId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found in Zitadel: " + zitadelUserId);
        }

        var user = userOpt.get();
        String state = user.getState() != null ? user.getState().toString() : "";

        if (!"USER_STATE_INITIAL".equals(state)) {
            throw new UserAlreadyActiveException(zitadelUserId);
        }

        zitadelManagementClient.createInviteCode(zitadelUserId);
    }
}
