package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Service for managing organization creation.
 * Creates the local organization record and invites the initial admin via the auth-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationManagementService {

    private final AuthServiceManagementClient authServiceClient;
    private final OrganizationRepository organizationRepository;

    /**
     * Creates a new organization and invites the initial admin via the auth-service.
     *
     * @param req The {@link CreateOrganizationRequest} containing organization and admin user details.
     * @return An {@link OrganizationResponse} summarizing the created organization.
     */
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest req) {
        String adminEmail = normalizeEmail(req.getAdminEmail());

        if (organizationRepository.existsByName(req.getName())) {
            log.warn("Organization name {} already exists", req.getName());
            throw new ConflictException("ORGANIZATION_NAME_ALREADY_EXISTS");
        }

        UUID localOrgId = UUID.randomUUID();
        UUID invitationId = null;

        try {
            organizationRepository.save(new OrganizationEntity(
                    localOrgId,
                    req.getName(),
                    Instant.now()
            ));

            invitationId = authServiceClient.createInvitation(
                    localOrgId, adminEmail, null, MemberRole.COMPANY_ADMIN
            );

            log.info("Created organization {} with admin invitation for {}", localOrgId, adminEmail);

            return new OrganizationResponse()
                    .id(localOrgId)
                    .name(req.getName())
                    .adminEmail(adminEmail)
                    .createdAt(OffsetDateTime.now(ZoneId.systemDefault()));

        } catch (Exception e) {
            log.error("Failed to create organization: {}", e.getMessage());
            if (invitationId != null) {
                try {
                    authServiceClient.cancelInvitation(invitationId);
                } catch (AuthServiceManagementException ex) {
                    log.error("Error cancelling invitation {} during compensation: {}", invitationId, ex.getMessage());
                }
            }
            organizationRepository.deleteById(localOrgId);
            throw new AuthServiceManagementException("Failed to create organization: " + e.getMessage(), e);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }
}
