package de.goaldone.backend.controller;

import de.goaldone.backend.api.MemberManagementApi;
import de.goaldone.backend.model.ChangeRoleRequest;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberListResponse;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.security.AuthorizationFacade;
import de.goaldone.backend.service.MemberInviteService;
import de.goaldone.backend.service.MemberManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {

    private final MemberInviteService memberInviteService;
    private final MemberManagementService memberManagementService;
    private final AuthorizationFacade authorizationFacade;

    @Override
    public ResponseEntity<Void> inviteMember(UUID orgId, InviteMemberRequest inviteMemberRequest) {
        authorizationFacade.requireOrgRole(orgId, MemberRole.COMPANY_ADMIN);
        memberInviteService.inviteMember(orgId, inviteMemberRequest);
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<Void> reinviteMember(UUID orgId, String zitadelUserId) {
        authorizationFacade.requireOrgRole(orgId, MemberRole.COMPANY_ADMIN);
        memberInviteService.reinviteMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MemberListResponse> listMembers(UUID orgId) {
        authorizationFacade.requireOrgRole(orgId, MemberRole.COMPANY_ADMIN);
        return ResponseEntity.ok(memberManagementService.listMembers(orgId));
    }

    @Override
    public ResponseEntity<Void> changeMemberRole(UUID orgId, String zitadelUserId, ChangeRoleRequest changeRoleRequest) {
        authorizationFacade.requireOrgRole(orgId, MemberRole.COMPANY_ADMIN);
        memberManagementService.changeMemberRole(orgId, zitadelUserId, changeRoleRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeMember(UUID orgId, String zitadelUserId) {
        authorizationFacade.requireOrgRole(orgId, MemberRole.COMPANY_ADMIN);
        memberManagementService.removeMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }
}
