package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminService {

    private final ZitadelManagementClient zitadelClient;
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Value("${zitadel.goaldone.org-id}")
    private String goaldoneOrgId;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    public List<SuperAdminResponse> listSuperAdmins() {
        List<String> userIds = zitadelClient.listUserIdsByRole(goaldoneOrgId, goaldoneProjectId, ROLE_SUPER_ADMIN);
        List<SuperAdminResponse> result = new ArrayList<>();

        for (String userId : userIds) {
            zitadelClient.getUser(userId).ifPresent(userNode -> {
                SuperAdminResponse admin = new SuperAdminResponse();
                admin.setZitadelId(userId);

                JsonNode human = userNode.path("human");
                admin.setEmail(human.path("email").path("email").asText(""));
                admin.setFirstName(human.path("profile").path("givenName").asText(""));
                admin.setLastName(human.path("profile").path("familyName").asText(""));
                admin.setStatus(userNode.path("state").asText());
                
                String createdAtStr = userNode.path("details").path("createdDate").asText();
                if (!createdAtStr.isEmpty()) {
                    admin.setCreatedAt(OffsetDateTime.parse(createdAtStr));
                }
                
                result.add(admin);
            });
        }
        return result;
    }

    public void inviteSuperAdmin(InviteSuperAdminRequest request) {
        String email = request.getEmail();

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

    @Transactional
    public void deleteSuperAdmin(String zitadelId, String currentSub) {
        // 1. Check if last Super-Admin
        List<String> admins = zitadelClient.listUserIdsByRole(goaldoneOrgId, goaldoneProjectId, ROLE_SUPER_ADMIN);
        if (admins.size() <= 1 && admins.contains(zitadelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_SUPER_ADMIN_CANNOT_BE_DELETED");
        }

        // 2. Delete in Zitadel first (Source of Truth)
        zitadelClient.deleteUser(zitadelId);

        // 3. Delete local shadow record if exists
        Optional<UserAccountEntity> accountOpt = userAccountRepository.findByZitadelSub(zitadelId);
        if (accountOpt.isPresent()) {
            UserAccountEntity account = accountOpt.get();
            UUID identityId = account.getUserIdentityId();
            
            // TODO: Cascade delete Tasks, Breaks, etc. (Stubs)
            log.info("TODO: Cascade delete tasks for user {}", account.getId());

            userAccountRepository.delete(account);
            
            // Clean up identity if it was the last account
            if (userAccountRepository.countByUserIdentityId(identityId) == 0) {
                userIdentityRepository.deleteById(identityId);
            }
            log.info("Deleted local shadow record for Super-Admin {}", zitadelId);
        } else {
            log.info("No local shadow record found for Super-Admin {}, Zitadel user was deleted.", zitadelId);
        }
    }
}
