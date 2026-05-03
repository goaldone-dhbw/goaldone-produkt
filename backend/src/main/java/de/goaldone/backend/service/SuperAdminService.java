package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Super-Admin users.
 * Handles listing, inviting, and deleting Super-Admins, coordinating with Zitadel and local shadow records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminService {

    private final ZitadelManagementClient zitadelClient;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Value("${zitadel.goaldone.org-id}")
    private String goaldoneOrgId;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Retrieves a list of all users with the Super-Admin role from Zitadel.
     *
     * @return A list of {@link SuperAdminResponse} objects containing details about each Super-Admin.
     */
    public List<SuperAdminResponse> listSuperAdmins() {
        List<String> userIds = zitadelClient.listUserIdsByRole(goaldoneOrgId, goaldoneProjectId, ROLE_SUPER_ADMIN);
        List<SuperAdminResponse> result = new ArrayList<>();

        for (String userId : userIds) {
            zitadelClient.getUser(userId).ifPresent(user -> {
                SuperAdminResponse admin = new SuperAdminResponse();
                admin.setZitadelId(userId);

                if (user.getHuman() != null) {
                    var human = user.getHuman();
                    admin.setEmail(human.getEmail() != null ? human.getEmail().getEmail() : "");
                    if (human.getProfile() != null) {
                        admin.setFirstName(human.getProfile().getGivenName() != null ? human.getProfile().getGivenName() : "");
                        admin.setLastName(human.getProfile().getFamilyName() != null ? human.getProfile().getFamilyName() : "");
                    }
                }
                admin.setStatus(user.getState() != null ? user.getState().toString() : "");

                if (user.getDetails() != null && user.getDetails().getCreationDate() != null) {
                    OffsetDateTime creationDate = user.getDetails().getCreationDate();
                    if (creationDate != null) {
                        admin.setCreatedAt(creationDate);
                    } else {
                        // Fallback: try to parse as string if it's not already an OffsetDateTime
                        try {
                            Instant createdAtInstant = Instant.parse(creationDate.toString());
                            admin.setCreatedAt(createdAtInstant.atOffset(ZoneOffset.UTC));
                        } catch (Exception e) {
                            log.warn("Could not parse creation date for user {}: {}", userId, e.getMessage());
                        }
                    }
                }

                result.add(admin);
            });
        }
        return result;
    }

    /**
     * Invites a new Super-Admin by creating a user in Zitadel, assigning the role, and generating an invite code.
     *
     * @param request The {@link InviteSuperAdminRequest} containing the email of the person to invite.
     * @throws ResponseStatusException with CONFLICT if the email is already in use, or BAD_GATEWAY if a Zitadel error occurs.
     */
    public void inviteSuperAdmin(InviteSuperAdminRequest request) {
        String email = normalizeEmail(request.getEmail());
        // 1. Check if email already exists in Zitadel (instance-wide)
        if (zitadelClient.emailExists(email)) {
            log.warn("Attempt to invite existing email as Super-Admin: {}", email);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE");
        }

        String userId = null;
        try {
            // 2. Add Human User in Goaldone Org
            // Note: We use email as both username (implied by Zitadel config) and email.
            // We use dummy names or leave them empty if the API allows.
            userId = zitadelClient.addHumanUser(goaldoneOrgId, email, "Super", "Admin");

            // 3. Add User Grant
            zitadelClient.addUserGrant(userId, goaldoneOrgId, goaldoneProjectId, ROLE_SUPER_ADMIN);

            // 4. Create Invite Code
            zitadelClient.createInviteCode(userId);

            log.info("Successfully invited new Super-Admin: {}", email);
        } catch (Exception e) {
            log.error("Failed to invite Super-Admin {}: {}", email, e.getMessage());
            if (userId != null) {
                log.info("Compensating: Deleting partially created user {}", userId);
                zitadelClient.deleteUser(userId);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZITADEL_UPSTREAM_ERROR");
        }
    }

    /**
     * Deletes a Super-Admin user from Zitadel and their local shadow record.
     * Prevents deletion of the last remaining Super-Admin.
     *
     * @param zitadelId The identity provider ID of the Super-Admin to delete.
     * @throws ResponseStatusException with CONFLICT if attempting to delete the last Super-Admin.
     */
    @Transactional
    public void deleteSuperAdmin(String zitadelId) {
        // 1. Check if last Super-Admin
        List<String> admins = zitadelClient.listUserIdsByRole(goaldoneOrgId, goaldoneProjectId, ROLE_SUPER_ADMIN);
        if (admins.size() <= 1 && admins.contains(zitadelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_SUPER_ADMIN_CANNOT_BE_DELETED");
        }

        // 2. Delete in Zitadel first (Source of Truth)
        zitadelClient.deleteUser(zitadelId);

        // 3. Delete local shadow record if exists
        Optional<MembershipEntity> membershipOpt = membershipRepository.findByUserAuthUserId(zitadelId);
        if (membershipOpt.isPresent()) {
            MembershipEntity membership = membershipOpt.get();
            UUID userId = membership.getUser().getId();

            // TODO: Cascade delete Tasks, Breaks, etc. (Stubs)
            log.info("TODO: Cascade delete tasks for membership {}", membership.getId());

            membershipRepository.delete(membership);

            // Clean up user if it was the last membership
            if (membershipRepository.countByUserId(userId) == 0) {
                userRepository.deleteById(userId);
            }
            log.info("Deleted local shadow record for Super-Admin {}", zitadelId);
        } else {
            log.info("No local shadow record found for Super-Admin {}, Zitadel user was deleted.", zitadelId);
        }
    }
    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }
}
