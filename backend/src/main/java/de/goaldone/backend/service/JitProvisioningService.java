package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JitProvisioningService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public void provisionUser(Jwt jwt) {
        String sub = jwt.getSubject();

        // Check if user already exists
        if (userRepository.findByZitadelSub(sub).isPresent()) {
            // Update last_seen_at
            UserEntity user = userRepository.findByZitadelSub(sub).get();
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
            return;
        }

        // Extract org claims from JWT
        String zitadelOrgId = jwt.getClaimAsString("urn:zitadel:iam:user:resourceowner:id");
        String orgName = jwt.getClaimAsString("urn:zitadel:iam:user:resourceowner:name");

        // Find or create organization
        OrganizationEntity org = organizationRepository.findByZitadelOrgId(zitadelOrgId)
            .orElseGet(() -> createOrganization(zitadelOrgId, orgName));

        // Create user
        UserEntity newUser = new UserEntity();
        newUser.setId(UUID.randomUUID());
        newUser.setZitadelSub(sub);
        newUser.setOrganizationId(org.getId());
        newUser.setCreatedAt(Instant.now());
        newUser.setLastSeenAt(Instant.now());

        userRepository.save(newUser);
        log.info("Provisioned new user {} in organization {}", sub, org.getId());
    }

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
