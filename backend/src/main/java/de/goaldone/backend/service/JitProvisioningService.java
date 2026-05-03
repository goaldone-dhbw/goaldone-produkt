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
 * After PK unification, user.id == auth-service user UUID and organization.id == auth-service company UUID.
 * When a user logs in for the first time with a JWT, this service ensures that
 * the corresponding local records (UserEntity and Organization) are created.
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
        String authUserIdStr = jwt.getClaimAsString("user_id");
        if (authUserIdStr == null) {
            authUserIdStr = jwt.getSubject();
        }

        UUID authUserId;
        try {
            authUserId = UUID.fromString(authUserIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Cannot parse auth user UUID from JWT claim '{}', skipping provisioning", authUserIdStr);
            return;
        }

        List<Map<String, Object>> orgs = jwt.getClaim("orgs");
        if (orgs == null || orgs.isEmpty()) {
            log.warn("No 'orgs' claim found for user {}, skipping provisioning", authUserId);
            return;
        }

        log.info("[JIT] Processing JWT orgs claim for user {}: {} organizations", authUserId, orgs.size());
        orgs.forEach(org -> log.info("[JIT] Org claim data: id={} (type: {}), name={}, slug={}, role={}",
            org.get("id"), org.get("id") != null ? org.get("id").getClass().getSimpleName() : "null",
            org.get("name"), org.get("slug"), org.get("role")));

        // 1. Resolve or create the User record using the auth-service UUID as PK
        UserEntity user = resolveOrCreateUser(authUserId);

        // 2. Iterate and provision memberships
        for (Map<String, Object> orgData : orgs) {
            String authCompanyIdStr = (String) orgData.get("id");
            String orgName = (String) orgData.get("name");

            if (authCompanyIdStr == null) {
                log.warn("[JIT] Org data missing 'id' field, skipping. Data: {}", orgData);
                continue;
            }

            UUID authCompanyId;
            try {
                authCompanyId = UUID.fromString(authCompanyIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("[JIT] Cannot parse org UUID '{}' from JWT claim, skipping", authCompanyIdStr);
                continue;
            }

            log.info("[JIT] Provisioning membership: user={}, company={}", authUserId, authCompanyId);
            provisionMembership(authUserId, user, authCompanyId, orgName);
        }
    }

    private UserEntity resolveOrCreateUser(UUID authUserId) {
        return userRepository.findById(authUserId)
            .orElseGet(() -> {
                UserEntity newUser = new UserEntity();
                newUser.setId(authUserId);
                newUser.setCreatedAt(Instant.now());
                return userRepository.save(newUser);
            });
    }

    private void provisionMembership(UUID authUserId, UserEntity user, UUID authCompanyId, String orgName) {
        // Find or create organization using the auth-service UUID as PK
        OrganizationEntity org = organizationRepository.findById(authCompanyId)
            .orElseGet(() -> createOrganization(authCompanyId, orgName));

        // Check if membership already exists for this (user, org)
        membershipRepository.findByUserIdAndOrganizationId(authUserId, org.getId())
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
     * After PK unification, the organization's local UUID IS the auth-service company UUID.
     * Handles potential race conditions by catching unique constraint violations.
     *
     * @param authCompanyId The auth-service UUID used as the organization PK.
     * @param orgName       The name of the organization.
     * @return The newly created or already existing {@link OrganizationEntity}.
     */
    private OrganizationEntity createOrganization(UUID authCompanyId, String orgName) {
        try {
            OrganizationEntity org = new OrganizationEntity();
            org.setId(authCompanyId);
            org.setName(orgName != null ? orgName : authCompanyId.toString());
            org.setCreatedAt(Instant.now());
            return organizationRepository.save(org);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created the org. Fetch it.
            log.debug("Organization {} already created by another thread, fetching existing", authCompanyId);
            return organizationRepository.findById(authCompanyId)
                .orElseThrow(() -> new RuntimeException("Organization creation failed", e));
        }
    }
}
