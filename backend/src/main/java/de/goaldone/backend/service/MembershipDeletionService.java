package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for deleting user accounts.
 * Handles the removal of user account records and their associated identities if they are no longer needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipDeletionService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuthServiceManagementClient authServiceManagementClient;

    /**
     * Deletes a membership by its ID.
     * For ACTIVE memberships: removes membership from auth-service and local DB; removes user if last membership.
     * For INVITED memberships: cancels the invitation in auth-service and removes the local record.
     *
     * @param membershipId The UUID of the membership to be deleted.
     * @throws IllegalStateException if the membership cannot be found.
     */
    @Transactional
    public void deleteMembership(UUID membershipId) {
        MembershipEntity membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new IllegalStateException("Membership not found"));

        if (membership.getUser() != null) {
            UUID userId = membership.getUser().getId();
            long count = membershipRepository.countByUserId(userId);

            try {
                authServiceManagementClient.deleteMembership(userId, membership.getOrganizationId());
            } catch (AuthServiceManagementException e) {
                log.warn("Auth-service deleteMembership failed for user {}: {}", userId, e.getMessage());
            }

            membershipRepository.delete(membership);

            if (count == 1) {
                userRepository.deleteById(userId);
                log.info("Deleted membership {} and its user identity {}", membershipId, userId);
            } else {
                log.info("Deleted membership {}, user {} remains with {} memberships",
                    membershipId, userId, count - 1);
            }
        } else {
            if (membership.getInvitationId() != null) {
                try {
                    authServiceManagementClient.cancelInvitation(membership.getInvitationId());
                } catch (AuthServiceManagementException e) {
                    log.warn("Auth-service cancelInvitation failed for {}: {}", membership.getInvitationId(), e.getMessage());
                }
            }
            membershipRepository.delete(membership);
            log.info("Deleted INVITED membership {}", membershipId);
        }
    }
}

