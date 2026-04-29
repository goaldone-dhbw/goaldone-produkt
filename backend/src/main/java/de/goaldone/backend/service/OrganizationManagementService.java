package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.client.ZitadelManagementClient.ZitadelOrgInfo;
import de.goaldone.backend.client.ZitadelManagementClient.ZitadelUserInfo;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.PartialDeletionException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.model.OrganizationListItem;
import de.goaldone.backend.model.OrganizationListResponse;
import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing the creation of organizations and their initial admin users.
 * This involves coordinating calls to Zitadel for identity management and maintaining local shadow records.
 * It also includes compensation logic to handle failures during the multi-step creation process.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationManagementService {

    private final ZitadelManagementClient zitadelManagementClient;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountDeletionService userAccountDeletionService;

    @Value("${zitadel.goaldone.project-id}")
    private String projectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    /**
     * Creates a new organization in Zitadel and a corresponding local record.
     * Also creates an initial admin user for the organization and assigns them the COMPANY_ADMIN role.
     *
     * @param req The {@link CreateOrganizationRequest} containing organization and admin user details.
     * @return An {@link OrganizationResponse} summarizing the created organization.
     * @throws IllegalStateException if required configuration is missing.
     * @throws ConflictException if the admin email or organization name already exists.
     * @throws ZitadelApiException if any step in the multi-stage creation process fails.
     */
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest req) {
        // Fast-fail: check configuration
        if (projectId == null || projectId.isBlank() || mainOrgId == null || mainOrgId.isBlank()) {
            throw new IllegalStateException("ZITADEL_GOALDONE_PROJECT_ID or ZITADEL_GOALDONE_ORG_ID is not configured");
        }
        String adminEmail = normalizeEmail(req.getAdminEmail());
        // Preconditions: check before any Zitadel writes
        if (zitadelManagementClient.emailExists(adminEmail)) {
            log.warn("Email {} already exists in Zitadel", adminEmail);
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
            log.info("Creating local shadow record for organization {}", zitadelOrgId);
            localOrgId = UUID.randomUUID();
            organizationRepository.save(new OrganizationEntity(
                    localOrgId,
                    zitadelOrgId,
                    req.getName(),
                    Instant.now()
            ));

            // Step 5: Create human user in Zitadel organization
            log.info("Creating human user in Zitadel organization {}", zitadelOrgId);
            zitadelUserId = zitadelManagementClient.addHumanUser(
                    zitadelOrgId,
                    adminEmail,
                    req.getAdminFirstName(),
                    req.getAdminLastName()
            );
            // Step 6: Assign COMPANY_ADMIN role to user
            log.info("Assigning COMPANY_ADMIN role to user {}", zitadelUserId);
            zitadelManagementClient.addUserGrant(zitadelUserId, mainOrgId, projectId, "COMPANY_ADMIN");

            // Step 7: Create invite code (sends email)
            log.info("Creating invite code for user {}", zitadelUserId);
            zitadelManagementClient.createInviteCode(zitadelUserId);

            return new OrganizationResponse()
                    .id(localOrgId)
                    .zitadelOrganizationId(zitadelOrgId)
                    .name(req.getName())
                    .adminEmail(adminEmail)
                    .createdAt(OffsetDateTime.now(ZoneId.systemDefault()));

        } catch (Exception e) {
            compensate(zitadelUserId, localOrgId, zitadelOrgId);
            throw new ZitadelApiException("Failed to create organization: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all organizations from Zitadel (source of truth), excluding the home organization.
     * The local DB record {@code id} and {@code createdAt} are populated when a shadow record exists;
     * otherwise {@code id} is {@code null} and {@code createdAt} falls back to Zitadel's creation date.
     *
     * @return an {@link OrganizationListResponse} containing all non-home organizations
     */
    public OrganizationListResponse listOrganizations() {
        // Build local lookup map: zitadelOrgId → entity
        Map<String, OrganizationEntity> localByZitadelId = organizationRepository.findAll().stream()
                .collect(Collectors.toMap(OrganizationEntity::getZitadelOrgId, e -> e));

        log.info("Listing organizations in DB {}", localByZitadelId.keySet());

        List<ZitadelOrgInfo> zitadelOrgs = zitadelManagementClient.listAllOrganizations();

        List<OrganizationListItem> items = zitadelOrgs.stream()
                .filter(zOrg -> !mainOrgId.equals(zOrg.id()))
                .map(zOrg -> {
                    OrganizationEntity local = localByZitadelId.get(zOrg.id());
                    log.info("Mapping zitadelOrgId={} to localOrgId={}", zOrg.id(), local != null ? local.getId() : null);
                    /*
                    List<String> userIds = zitadelManagementClient.listAllUserIdsForOrgProject(zOrg.id(), projectId);
                    Map<String, String> states = zitadelManagementClient.getUserStates(userIds);
                    long active = states.values().stream().filter("USER_STATE_ACTIVE"::equals).count();
                    long invited = states.values().stream().filter("USER_STATE_INITIAL"::equals).count();


                     */
                    OffsetDateTime createdAt = (local != null)
                            ? local.getCreatedAt().atOffset(ZoneOffset.UTC)
                            : zOrg.creationDate();

                    return new OrganizationListItem()
                            .id(local != null ? local.getId() : null)
                            .zitadelOrganizationId(zOrg.id())
                            .name(zOrg.name())
                            .createdAt(createdAt);
                }).toList();

        return new OrganizationListResponse().organizations(items);
    }

    /**
     * Deletes an organization and all its members in a cascading operation across Zitadel and the local database.
     * <p>
     * Deletion order: active members (via {@link UserAccountDeletionService}) → invited members (direct Zitadel delete)
     * → Zitadel organization → local record.
     * <p>
     * If any individual user deletion fails, the operation collects all failures and throws a
     * {@link PartialDeletionException} without deleting the organization itself, enabling a retry.
     *
     * @param zitadelOrgId the local UUID of the organization to delete
     * @throws ResponseStatusException   with 404 if the organization is not found
     * @throws ZitadelApiException       if listing org members or deleting the Zitadel organization fails
     * @throws PartialDeletionException  if one or more member deletions fail
     */
    public void deleteOrganization(String zitadelOrgId) {

        ZitadelOrgInfo zitadelOrg = zitadelManagementClient.getOrgInfo(zitadelOrgId);

        OrganizationEntity org = organizationRepository.findByZitadelOrgId(zitadelOrg.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND"));

        if (mainOrgId.equals(org.getZitadelOrgId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HOME_ORGANIZATION_CANNOT_BE_DELETED");
        }

        List<ZitadelUserInfo> users = zitadelManagementClient.listUsersInOrganization(zitadelOrgId);

        List<String> failedUserIds = new ArrayList<>();

        for (ZitadelUserInfo user : users) {
            if ("USER_STATE_ACTIVE".equals(user.state())) {
                Optional<UserAccountEntity> account = userAccountRepository.findByZitadelSub(user.id());
                if (account.isPresent()) {
                    try {
                        userAccountDeletionService.deleteUserAccount(account.get().getId());
                    } catch (Exception e) {
                        log.error("Failed to delete active user account {} (zitadelSub={}): {}",
                                account.get().getId(), user.id(), e.getMessage());
                        failedUserIds.add(user.id());
                    }
                } else {
                    // Active in Zitadel but no local shadow record — delete directly
                    try {
                        zitadelManagementClient.deleteUserOrThrow(user.id());
                    } catch (Exception e) {
                        log.error("Failed to delete Zitadel user {} (no local account): {}", user.id(), e.getMessage());
                        failedUserIds.add(user.id());
                    }
                }
            } else {
                // Invited (USER_STATE_INITIAL) or any other state — no local record, delete directly in Zitadel
                try {
                    zitadelManagementClient.deleteUserOrThrow(user.id());
                } catch (Exception e) {
                    log.error("Failed to delete invited user {} in Zitadel: {}", user.id(), e.getMessage());
                    failedUserIds.add(user.id());
                }
            }
        }

        if (!failedUserIds.isEmpty()) {
            throw new PartialDeletionException(failedUserIds);
        }

        zitadelManagementClient.deleteOrganizationOrThrow(org.getZitadelOrgId());

        try {
            organizationRepository.deleteById(org.getId());
            log.info("Deleted organization {} (zitadelOrgId={})", org.getId(), org.getZitadelOrgId());
        } catch (Exception e) {
            log.error("Failed to delete local organization record {}: {}", org.getId(), e.getMessage());
            throw new ZitadelApiException("Failed to delete local organization record: " + e.getMessage(), e);
        }
    }

    /**
     * Compensates for a failed organization creation by attempting to delete partially created resources.
     * Deletes resources in reverse order: user -> local record -> Zitadel organization.
     *
     * @param zitadelUserId The Zitadel ID of the partially created user.
     * @param localOrgId    The local UUID of the partially created organization record.
     * @param zitadelOrgId  The Zitadel ID of the partially created organization.
     */
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

    /**
     * Attempts to delete a user from Zitadel. Logs errors without throwing.
     *
     * @param userId The Zitadel user ID to delete.
     */
    private void tryDeleteUser(String userId) {
        try {
            zitadelManagementClient.deleteUser(userId);
        } catch (Exception e) {
            log.error("Error deleting user {} during compensation: {}", userId, e.getMessage());
        }
    }

    /**
     * Attempts to delete a local organization record. Logs errors without throwing.
     *
     * @param orgId The local organization UUID to delete.
     */
    private void tryDeleteLocalOrg(UUID orgId) {
        try {
            organizationRepository.deleteById(orgId);
        } catch (Exception e) {
            log.error("Error deleting local organization {} during compensation: {}", orgId, e.getMessage());
        }
    }

    /**
     * Attempts to delete an organization from Zitadel. Logs errors without throwing.
     *
     * @param zitadelOrgId The Zitadel organization ID to delete.
     */
    private void tryDeleteZitadelOrg(String zitadelOrgId) {
        try {
            zitadelManagementClient.deleteOrganization(zitadelOrgId);
        } catch (Exception e) {
            log.error("Error deleting Zitadel organization {} during compensation: {}", zitadelOrgId, e.getMessage());
        }
    }
    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }
}
