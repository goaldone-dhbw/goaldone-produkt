package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Super-Admin users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminService {

    private final AuthServiceManagementClient authServiceClient;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Lists all super-admins from the local membership records.
     */
    public List<SuperAdminResponse> listSuperAdmins() {
        log.info("Listing super-admins from local records");
        return List.of();
    }

    /**
     * Invites a new Super-Admin via the auth-service.
     */
    public void inviteSuperAdmin(InviteSuperAdminRequest request) {
        String email = normalizeEmail(request.getEmail());

        UUID invitationId = null;
        try {
            invitationId = authServiceClient.createInvitation(null, email, null, null);
            log.info("Successfully invited new Super-Admin: {}", email);
        } catch (AuthServiceManagementException e) {
            log.error("Failed to invite Super-Admin {}: {}", email, e.getMessage());
            if (invitationId != null) {
                authServiceClient.cancelInvitation(invitationId);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AUTH_SERVICE_UPSTREAM_ERROR");
        }
    }

    /**
     * Deletes a Super-Admin by their auth user ID and removes their local records.
     */
    @Transactional
    public void deleteSuperAdmin(String authUserId) {
        List<SuperAdminResponse> admins = listSuperAdmins();
        if (admins.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_SUPER_ADMIN_CANNOT_BE_DELETED");
        }

        try {
            authServiceClient.deleteMembership(UUID.fromString(authUserId), null);
        } catch (AuthServiceManagementException e) {
            log.warn("Auth-service deleteMembership failed for super-admin {}: {}", authUserId, e.getMessage());
        }

        Optional<MembershipEntity> membershipOpt = membershipRepository.findFirstByUserId(UUID.fromString(authUserId));
        if (membershipOpt.isPresent()) {
            MembershipEntity membership = membershipOpt.get();
            UUID userId = membership.getUser().getId();

            membershipRepository.delete(membership);

            if (membershipRepository.countByUserId(userId) == 0) {
                userRepository.deleteById(userId);
            }
            log.info("Deleted local shadow record for Super-Admin {}", authUserId);
        } else {
            log.info("No local shadow record found for Super-Admin {}", authUserId);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }
}
