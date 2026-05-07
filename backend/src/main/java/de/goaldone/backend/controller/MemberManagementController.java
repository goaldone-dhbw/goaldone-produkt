package de.goaldone.backend.controller;

import de.goaldone.backend.api.MemberManagementApi;
import de.goaldone.backend.model.ChangeRoleRequest;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberListResponse;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.MemberInviteService;
import de.goaldone.backend.service.MemberManagementService;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {

    private final MemberInviteService memberInviteService;
    private final MemberManagementService memberManagementService;
    private final CurrentUserResolver currrentUserResolver;
    private final UserIdentityService userIdentityService;

    @Override
    public ResponseEntity<Void> inviteMember(UUID orgId, InviteMemberRequest inviteMemberRequest) {
        if(userIdentityService.hasUserAccessToOrganizationWithRole(currrentUserResolver.extractJwt(), orgId, MemberRole.COMPANY_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot list members of organization on this endpoint");
        }
        memberInviteService.inviteMember(orgId, inviteMemberRequest);
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<Void> reinviteMember(UUID orgId, String zitadelUserId) {
        if(userIdentityService.hasUserAccessToOrganizationWithRole(currrentUserResolver.extractJwt(), orgId, MemberRole.COMPANY_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot list members of organization on this endpoint");
        }
        memberInviteService.reinviteMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MemberListResponse> listMembers(UUID orgId) {
        if(userIdentityService.hasUserAccessToOrganizationWithRole(currrentUserResolver.extractJwt(), orgId, MemberRole.COMPANY_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot list members of organization on this endpoint");
        }
        return ResponseEntity.ok(memberManagementService.listMembers(orgId));
    }

    @Override
    public ResponseEntity<Void> changeMemberRole(UUID orgId, String zitadelUserId, ChangeRoleRequest changeRoleRequest) {
        if(userIdentityService.hasUserAccessToOrganizationWithRole(currrentUserResolver.extractJwt(), orgId, MemberRole.COMPANY_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot list members of organization on this endpoint");
        }
        memberManagementService.changeMemberRole(orgId, zitadelUserId, changeRoleRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeMember(UUID orgId, String zitadelUserId) {
        if(userIdentityService.hasUserAccessToOrganizationWithRole(currrentUserResolver.extractJwt(), orgId, MemberRole.COMPANY_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot list members of organization on this endpoint");
        }
        memberManagementService.removeMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }
}
