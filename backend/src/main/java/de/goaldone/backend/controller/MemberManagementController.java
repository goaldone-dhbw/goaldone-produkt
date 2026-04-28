package de.goaldone.backend.controller;

import de.goaldone.backend.api.MemberManagementApi;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.service.MemberInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MemberManagementController implements MemberManagementApi {

    private final MemberInviteService memberInviteService;

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Void> inviteMember(UUID orgId, InviteMemberRequest inviteMemberRequest) {
        memberInviteService.inviteMember(orgId, inviteMemberRequest);
        return ResponseEntity.status(201).build();
    }

    @Override
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Void> reinviteMember(UUID orgId, String zitadelUserId) {
        memberInviteService.reinviteMember(orgId, zitadelUserId);
        return ResponseEntity.noContent().build();
    }
}
