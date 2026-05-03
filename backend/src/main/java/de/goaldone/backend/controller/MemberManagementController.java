package de.goaldone.backend.controller;

import de.goaldone.backend.api.MemberManagementApi;
import de.goaldone.backend.model.ChangeRoleRequest;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberListResponse;
import de.goaldone.backend.service.MemberInviteService;
import de.goaldone.backend.service.MemberManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for managing organization members and their roles.
 * All operations require COMPANY_ADMIN role and membership in the target organization.
 */
@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {

    private final MemberInviteService memberInviteService;
    private final MemberManagementService memberManagementService;

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#xOrgID)")
    public ResponseEntity<Void> inviteMember(UUID xOrgID, InviteMemberRequest inviteMemberRequest) {
        memberInviteService.inviteMember(xOrgID, inviteMemberRequest);
        return ResponseEntity.status(201).build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#xOrgID)")
    public ResponseEntity<Void> reinviteMember(UUID xOrgID, UUID userId) {
        memberInviteService.reinviteMember(xOrgID, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#xOrgID)")
    public ResponseEntity<MemberListResponse> listMembers(UUID xOrgID) {
        return ResponseEntity.ok(memberManagementService.listMembers(xOrgID));
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#xOrgID)")
    public ResponseEntity<Void> changeMemberRole(UUID xOrgID, UUID userId, ChangeRoleRequest changeRoleRequest) {
        memberManagementService.changeMemberRole(xOrgID, userId, changeRoleRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#xOrgID)")
    public ResponseEntity<Void> removeMember(UUID xOrgID, UUID userId) {
        memberManagementService.removeMember(xOrgID, userId);
        return ResponseEntity.noContent().build();
    }
}
