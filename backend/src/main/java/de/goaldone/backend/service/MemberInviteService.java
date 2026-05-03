package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.exception.UserAlreadyActiveException;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.model.MemberStatus;
import de.goaldone.backend.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberInviteService {

    private final AuthServiceManagementClient authServiceClient;
    private final MembershipRepository membershipRepository;

    /**
     * Invites a new member to an organization via the auth-service.
     * Creates an eager INVITED MembershipEntity to track the pending invitation.
     */
    @Transactional
    public void inviteMember(UUID xOrgID, InviteMemberRequest request) {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID inviterId = UUID.fromString(jwt.getSubject());

        MemberRole memberRole = request.getRole() != null ? request.getRole() : MemberRole.USER;

        UUID invitationId;
        try {
            invitationId = authServiceClient.createInvitation(xOrgID, request.getEmail(), inviterId, memberRole);
        } catch (AuthServiceManagementException e) {
            log.error("Failed to create invitation via auth-service: {}", e.getMessage());
            if (e.getStatusCode() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service error");
        }

        MembershipEntity membership = new MembershipEntity();
        membership.setId(UUID.randomUUID());
        membership.setOrganizationId(xOrgID);
        membership.setStatus(MemberStatus.INVITED.getValue());
        membership.setInvitationId(invitationId);
        membership.setRole(memberRole.getValue());
        membership.setEmail(request.getEmail());
        membership.setCreatedAt(Instant.now());

        membershipRepository.save(membership);
        log.info("Created INVITED membership for {} in org {}", request.getEmail(), xOrgID);
    }

    /**
     * Resends an invitation to a member whose invitation has not yet been accepted.
     * The membershipId (passed as userId path param) identifies the local INVITED membership.
     */
    @Transactional
    public void reinviteMember(UUID orgId, UUID membershipId) {
        MembershipEntity membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        if (!MemberStatus.INVITED.getValue().equals(membership.getStatus())) {
            throw new UserAlreadyActiveException(membershipId.toString());
        }

        if (membership.getInvitationId() != null) {
            try {
                authServiceClient.cancelInvitation(membership.getInvitationId());
            } catch (AuthServiceManagementException e) {
                log.warn("Failed to cancel old invitation {}: {}", membership.getInvitationId(), e.getMessage());
            }
        }

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID inviterId = UUID.fromString(jwt.getSubject());

        MemberRole memberRole = membership.getRole() != null
                ? MemberRole.fromValue(membership.getRole())
                : MemberRole.USER;

        UUID newInvitationId;
        try {
            newInvitationId = authServiceClient.createInvitation(orgId, membership.getEmail(), inviterId, memberRole);
        } catch (AuthServiceManagementException e) {
            log.error("Failed to re-create invitation: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service error");
        }

        membership.setInvitationId(newInvitationId);
        membershipRepository.save(membership);
        log.info("Reinvited membership {} in org {}", membershipId, orgId);
    }
}
