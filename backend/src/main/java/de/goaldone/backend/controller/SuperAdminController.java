package de.goaldone.backend.controller;

import de.goaldone.backend.api.SuperAdminApi;
import de.goaldone.backend.model.CreateSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminListResponse;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController implements SuperAdminApi {
    private final SuperAdminService superAdminService;

    @Override
    public ResponseEntity<SuperAdminListResponse> listSuperAdmins() {
        return ResponseEntity.ok(superAdminService.listSuperAdmins());
    }

    @Override
    public ResponseEntity<SuperAdminResponse> createSuperAdmin(CreateSuperAdminRequest createSuperAdminRequest) {
        SuperAdminResponse response = superAdminService.createSuperAdmin(createSuperAdminRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<Void> deleteSuperAdmin(UUID userId) {
        superAdminService.deleteSuperAdmin(userId);
        return ResponseEntity.noContent().build();
    }
}
