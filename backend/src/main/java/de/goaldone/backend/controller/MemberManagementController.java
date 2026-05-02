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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {

    private final MemberInviteService memberInviteService;
    private final MemberManagementService memberManagementService;

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#orgId)")
    public ResponseEntity<Void> inviteMember(UUID orgId, InviteMemberRequest inviteMemberRequest) {
        memberInviteService.inviteMember(orgId, inviteMemberRequest);
        return ResponseEntity.status(201).build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#orgId)")
    public ResponseEntity<Void> reinviteMember(UUID orgId, String zitadelUserId) {
        memberInviteService.reinviteMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#orgId)")
    public ResponseEntity<MemberListResponse> listMembers(UUID orgId) {
        return ResponseEntity.ok(memberManagementService.listMembers(orgId));
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#orgId)")
    public ResponseEntity<Void> changeMemberRole(UUID orgId, String zitadelUserId, ChangeRoleRequest changeRoleRequest) {
        memberManagementService.changeMemberRole(orgId, zitadelUserId, changeRoleRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN') and @authz.isMember(#orgId)")
    public ResponseEntity<Void> removeMember(UUID orgId, String zitadelUserId) {
        memberManagementService.removeMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }
}
