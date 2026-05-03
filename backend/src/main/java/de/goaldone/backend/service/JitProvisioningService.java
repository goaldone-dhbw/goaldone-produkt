package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for Just-In-Time (JIT) provisioning of users and organizations.
 * When a user logs in for the first time with a JWT from Zitadel, this service
 * ensures that the corresponding local records (UserAccount, UserIdentity, and Organization) are created.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JitProvisioningService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * Provisions a user based on the information in the provided JWT.
     * Iterates through all organizations in the 'orgs' claim and ensures memberships exist.
     *
     * @param jwt The {@link Jwt} containing the user's information and organization claims.
     */
    @Transactional
    public void provisionUser(Jwt jwt) {
        String authUserId = jwt.getClaimAsString("user_id");
        if (authUserId == null) {
            authUserId = jwt.getSubject();
        }

        List<Map<String, Object>> orgs = jwt.getClaim("orgs");
        if (orgs == null || orgs.isEmpty()) {
            log.warn("No 'orgs' claim found for user {}, skipping provisioning", authUserId);
            return;
        }

        // 1. Resolve or create the User for this authUserId
        // Since we now allow multiple MembershipEntity for one authUserId,
        // they should all point to the same UserEntity.
        UserEntity user = resolveOrCreateUser(authUserId);

        // 2. Iterate and provision memberships
        for (Map<String, Object> orgData : orgs) {
            String authCompanyId = (String) orgData.get("id");
            String orgName = (String) orgData.get("name");

            if (authCompanyId == null) continue;

            provisionMembership(authUserId, user, authCompanyId, orgName);
        }
    }

    private UserEntity resolveOrCreateUser(String authUserId) {
        return userRepository.findByAuthUserId(authUserId)
            .orElseGet(() -> {
                UserEntity newUser = new UserEntity();
                newUser.setId(UUID.randomUUID());
                newUser.setAuthUserId(authUserId);
                newUser.setCreatedAt(Instant.now());
                return userRepository.save(newUser);
            });
    }

    private void provisionMembership(String authUserId, UserEntity user, String authCompanyId, String orgName) {
        // Find or create organization
        OrganizationEntity org = organizationRepository.findByAuthCompanyId(authCompanyId)
            .orElseGet(() -> createOrganization(authCompanyId, orgName));

        // Check if membership already exists for this (user, org)
        membershipRepository.findByUserAuthUserIdAndOrganizationId(authUserId, org.getId())
            .ifPresentOrElse(
                membership -> {
                    membership.setLastSeenAt(Instant.now());
                    membershipRepository.save(membership);
                },
                () -> {
                    MembershipEntity newMembership = new MembershipEntity();
                    newMembership.setId(UUID.randomUUID());
                    newMembership.setOrganizationId(org.getId());
                    newMembership.setUser(user);
                    newMembership.setCreatedAt(Instant.now());
                    newMembership.setLastSeenAt(Instant.now());
                    membershipRepository.save(newMembership);
                    log.info("Provisioned new membership for user {} in organization {}", authUserId, org.getId());
                }
            );
    }

    /**
     * Helper method to create a new organization record.
     * Handles potential race conditions by catching unique constraint violations.
     *
     * @param authCompanyId The unique organization ID from the Identity Provider.
     * @param orgName      The name of the organization.
     * @return The newly created or already existing {@link OrganizationEntity}.
     * @throws RuntimeException if organization creation fails due to reasons other than race conditions.
     */
    private OrganizationEntity createOrganization(String authCompanyId, String orgName) {
        try {
            OrganizationEntity org = new OrganizationEntity();
            org.setId(UUID.randomUUID());
            org.setAuthCompanyId(authCompanyId);
            org.setName(orgName != null ? orgName : authCompanyId);
            org.setCreatedAt(Instant.now());
            return organizationRepository.save(org);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created the org. Fetch it.
            log.debug("Organization {} already created by another thread, fetching existing", authCompanyId);
            return organizationRepository.findByAuthCompanyId(authCompanyId)
                .orElseThrow(() -> new RuntimeException("Organization creation failed", e));
        }
    }
}
