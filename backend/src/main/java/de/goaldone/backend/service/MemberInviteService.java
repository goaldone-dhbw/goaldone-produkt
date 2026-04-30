package de.goaldone.backend.service;

import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.EmailAlreadyInUseException;
import de.goaldone.backend.exception.NotMemberOfOrganizationException;
import de.goaldone.backend.exception.UserAlreadyActiveException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
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
    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    public void inviteMember(UUID orgId, InviteMemberRequest request) {
        String callerSub = getCallerSub();
        UserAccountEntity callerAccount = userAccountRepository.findByZitadelSub(callerSub)
                .orElseThrow(() -> new NotMemberOfOrganizationException("Caller account not found"));

        if (!callerAccount.getOrganizationId().equals(orgId)) {
            throw new NotMemberOfOrganizationException("Caller does not belong to organization: " + orgId);
        }

        if (zitadelManagementClient.emailExists(request.getEmail())) {
            throw new EmailAlreadyInUseException(request.getEmail());
        }

        OrganizationEntity organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + orgId));

        String zitadelUserId = null;
        try {
            zitadelUserId = zitadelManagementClient.addHumanUser(
                    organization.getZitadelOrgId(),
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

    public void reinviteMember(UUID orgId, String zitadelUserId) {
        String callerSub = getCallerSub();
        UserAccountEntity callerAccount = userAccountRepository.findByZitadelSub(callerSub)
                .orElseThrow(() -> new NotMemberOfOrganizationException("Caller account not found"));

        if (!callerAccount.getOrganizationId().equals(orgId)) {
            throw new NotMemberOfOrganizationException("Caller does not belong to organization: " + orgId);
        }

        var userOpt = zitadelManagementClient.getUser(zitadelUserId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found in Zitadel: " + zitadelUserId);
        }

        var user = userOpt.get();
        String state = user.getState() != null ? user.getState().toString() : "";

        // Status names in Zitadel v2 are usually USER_STATE_INITIAL, USER_STATE_ACTIVE, etc.
        if (!"USER_STATE_INITIAL".equals(state)) {
            throw new UserAlreadyActiveException(zitadelUserId);
        }

        zitadelManagementClient.createInviteCode(zitadelUserId);
    }

    private String getCallerSub() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getSubject();
    }
}
