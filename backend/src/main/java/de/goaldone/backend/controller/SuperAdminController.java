package de.goaldone.backend.controller;

import de.goaldone.backend.api.SuperAdminManagementApi;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController implements SuperAdminManagementApi {

    private final SuperAdminService superAdminService;

    @Override
    public ResponseEntity<List<SuperAdminResponse>> listSuperAdmins() {
        return ResponseEntity.ok(superAdminService.listSuperAdmins());
    }

    @Override
    public ResponseEntity<Void> inviteSuperAdmin(InviteSuperAdminRequest inviteSuperAdminRequest) {
        superAdminService.inviteSuperAdmin(inviteSuperAdminRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> deleteSuperAdmin(String userId) {
        superAdminService.deleteSuperAdmin(userId);
        return ResponseEntity.noContent().build();
    }
}
