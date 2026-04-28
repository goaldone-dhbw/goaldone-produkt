package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * Provisions a user based on the information in the provided JWT.
     * If the user already exists, it updates their 'last seen' timestamp.
     * If the organization does not exist, it creates a new one.
     *
     * @param jwt The {@link Jwt} containing the user's information and organization claims.
     */
    @Transactional
    public void provisionUser(Jwt jwt) {
        String sub = jwt.getSubject();

        // Check if user already exists
        if (userAccountRepository.findByZitadelSub(sub).isPresent()) {
            // Update last_seen_at
            UserAccountEntity user = userAccountRepository.findByZitadelSub(sub).get();
            user.setLastSeenAt(Instant.now());
            userAccountRepository.save(user);
            return;
        }

        // Extract org claims from JWT
        String zitadelOrgId = jwt.getClaimAsString("urn:zitadel:iam:user:resourceowner:id");
        String orgName = jwt.getClaimAsString("urn:zitadel:iam:user:resourceowner:name");

        // Find or create organization
        OrganizationEntity org = organizationRepository.findByZitadelOrgId(zitadelOrgId)
            .orElseGet(() -> createOrganization(zitadelOrgId, orgName));

        // Create user identity
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setId(UUID.randomUUID());
        identity.setCreatedAt(Instant.now());
        userIdentityRepository.save(identity);

        // Create user account
        UserAccountEntity newUser = new UserAccountEntity();
        newUser.setId(UUID.randomUUID());
        newUser.setZitadelSub(sub);
        newUser.setOrganizationId(org.getId());
        newUser.setUserIdentityId(identity.getId());
        newUser.setCreatedAt(Instant.now());
        newUser.setLastSeenAt(Instant.now());

        userAccountRepository.save(newUser);
        log.info("Provisioned new user {} in organization {} with identity {}", sub, org.getId(), identity.getId());
    }

    /**
     * Helper method to create a new organization record.
     * Handles potential race conditions by catching unique constraint violations.
     *
     * @param zitadelOrgId The unique organization ID from Zitadel.
     * @param orgName      The name of the organization.
     * @return The newly created or already existing {@link OrganizationEntity}.
     * @throws RuntimeException if organization creation fails due to reasons other than race conditions.
     */
    private OrganizationEntity createOrganization(String zitadelOrgId, String orgName) {
        try {
            OrganizationEntity org = new OrganizationEntity();
            org.setId(UUID.randomUUID());
            org.setZitadelOrgId(zitadelOrgId);
            org.setName(orgName != null ? orgName : zitadelOrgId);
            org.setCreatedAt(Instant.now());
            return organizationRepository.save(org);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created the org. Fetch it.
            log.debug("Organization {} already created by another thread, fetching existing", zitadelOrgId);
            return organizationRepository.findByZitadelOrgId(zitadelOrgId)
                .orElseThrow(() -> new RuntimeException("Organization creation failed", e));
        }
    }
}
