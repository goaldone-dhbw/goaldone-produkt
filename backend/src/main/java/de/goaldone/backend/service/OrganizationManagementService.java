package de.goaldone.backend.service;

import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationManagementService {

    private final ZitadelManagementClient zitadelManagementClient;
    private final OrganizationRepository organizationRepository;

    @Value("${zitadel.goaldone.project-id}")
    private String projectId;

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest req) {
        // Preconditions: check before any Zitadel writes
        if (zitadelManagementClient.emailExists(req.getAdminEmail())) {
            log.warn("Email {} already exists in Zitadel", req.getAdminEmail());
            throw new ConflictException("EMAIL_ALREADY_IN_USE");
        }
        if (organizationRepository.existsByName(req.getName())) {
            log.warn("Organization name {} already exists", req.getName());
            throw new ConflictException("ORGANIZATION_NAME_ALREADY_EXISTS");
        }

        String zitadelOrgId = null;
        UUID localOrgId = null;
        String zitadelUserId = null;

        try {
            // Step 3: Create organization in Zitadel
            log.info("Creating organization in Zitadel: {}", req.getName());
            zitadelOrgId = zitadelManagementClient.addOrganization(req.getName());

            // Step 4: Create local shadow record
            localOrgId = UUID.randomUUID();
            organizationRepository.save(new OrganizationEntity(
                    localOrgId,
                    zitadelOrgId,
                    req.getName(),
                    Instant.now()
            ));

            // Step 5: Create human user in Zitadel organization
            zitadelUserId = zitadelManagementClient.addHumanUser(zitadelOrgId, req.getAdminEmail(), req.getAdminFirstName(), req.getAdminLastName());

            // Step 6: Assign COMPANY_ADMIN role to user
            zitadelManagementClient.addUserGrant(zitadelUserId, zitadelOrgId, projectId, "COMPANY_ADMIN");

            // Step 7: Create invite code (sends email)
            zitadelManagementClient.createInviteCode(zitadelUserId);

            return new OrganizationResponse()
                    .id(localOrgId)
                    .zitadelOrganizationId(zitadelOrgId)
                    .name(req.getName())
                    .adminEmail(req.getAdminEmail())
                    .createdAt(OffsetDateTime.now(ZoneId.systemDefault()));

        } catch (Exception e) {
            compensate(zitadelUserId, localOrgId, zitadelOrgId);
            throw new ZitadelApiException("Failed to create organization: " + e.getMessage(), e);
        }
    }

    private void compensate(String zitadelUserId, UUID localOrgId, String zitadelOrgId) {
        // Reverse order: user -> local -> org
        if (zitadelUserId != null) {
            tryDeleteUser(zitadelUserId);
        }
        if (localOrgId != null) {
            tryDeleteLocalOrg(localOrgId);
        }
        if (zitadelOrgId != null) {
            tryDeleteZitadelOrg(zitadelOrgId);
        }
    }

    private void tryDeleteUser(String userId) {
        try {
            zitadelManagementClient.deleteUser(userId);
        } catch (Exception e) {
            log.error("Error deleting user {} during compensation: {}", userId, e.getMessage());
        }
    }

    private void tryDeleteLocalOrg(UUID orgId) {
        try {
            organizationRepository.deleteById(orgId);
        } catch (Exception e) {
            log.error("Error deleting local organization {} during compensation: {}", orgId, e.getMessage());
        }
    }

    private void tryDeleteZitadelOrg(String zitadelOrgId) {
        try {
            zitadelManagementClient.deleteOrganization(zitadelOrgId);
        } catch (Exception e) {
            log.error("Error deleting Zitadel organization {} during compensation: {}", zitadelOrgId, e.getMessage());
        }
    }
}
