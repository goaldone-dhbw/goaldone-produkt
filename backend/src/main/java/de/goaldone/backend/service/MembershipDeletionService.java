package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
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
    private final ZitadelManagementClient zitadelManagementClient;

    /**
     * Deletes a membership by its ID.
     * If the membership is the last one associated with a user, the user is also deleted.
     *
     * @param membershipId The UUID of the membership to be deleted.
     * @throws IllegalStateException if the membership cannot be found.
     */
    @Transactional
    public void deleteMembership(UUID membershipId) {
        MembershipEntity membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new IllegalStateException("Membership not found"));

        UUID userId = membership.getUser().getId();
        String authUserId = membership.getUser().getAuthUserId();
        long count = membershipRepository.countByUserId(userId);

        zitadelManagementClient.deleteUser(authUserId);

        membershipRepository.delete(membership);

        if (count == 1) {
            // Last membership for user — clean up user too
            userRepository.deleteById(userId);
            log.info("Deleted membership {} and its user identity {}", membershipId, userId);
        } else {
            log.info("Deleted membership {}, user {} remains with {} memberships",
                membershipId, userId, count - 1);
        }
    }
}
