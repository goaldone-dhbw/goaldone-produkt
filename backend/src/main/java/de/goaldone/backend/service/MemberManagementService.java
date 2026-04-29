package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.NotMemberOfOrganizationException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberManagementService {

    private final ZitadelManagementClient zitadelManagementClient;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountDeletionService userAccountDeletionService;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    public MemberListResponse listMembers(UUID orgId) {
        validateCallerBelongsToOrg(orgId);

        OrganizationEntity organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        JsonNode grantsResponse = zitadelManagementClient.listAllGrants(
                mainOrgId, goaldoneProjectId, organization.getZitadelOrgId()
        );

        List<String> userIds = new ArrayList<>();
        Map<String, List<String>> userRoles = new HashMap<>();

        // "authorizations" ist der korrekte Feldname laut tatsächlichem Response
        if (grantsResponse.has("authorizations")) {
            grantsResponse.get("authorizations").forEach(auth -> {
                // Struktur: auth -> user -> id  (NICHT auth -> userId)
                String userId = auth.path("user").path("id").asText(null);
                if (userId == null || userId.isBlank()) return;

                userIds.add(userId);

                List<String> rolesList = new ArrayList<>();
                if (auth.has("roles")) {
                    auth.get("roles").forEach(role -> {
                        String key = role.path("key").asText(null);
                        if (key != null) rolesList.add(key);
                    });
                }
                userRoles.put(userId, rolesList);
            });
        }

        List<JsonNode> zitadelUsers = zitadelManagementClient.listUsersByIds(userIds);
        List<UserAccountEntity> localAccounts = userAccountRepository.findAll();

        List<MemberResponse> members = zitadelUsers.stream().map(userNode -> {
            String zitadelUserId  = userNode.path("userId").asText();
            String email          = userNode.path("human").path("email").path("email").asText();
            String firstName      = userNode.path("human").path("profile").path("givenName").asText();
            String lastName       = userNode.path("human").path("profile").path("familyName").asText();
            String state          = userNode.path("state").asText();

            log.info("User info: zitadelUserId {}, email {}, firstName {}, lastName {}, state {}", zitadelUserId, email, firstName, lastName, state);

            String creationDateStr = userNode.path("details").path("creationDate").asText(null);
            Instant createdAtInstant = (creationDateStr != null && !creationDateStr.isBlank())
                    ? Instant.parse(creationDateStr)
                    : Instant.now();
            OffsetDateTime createdAt = createdAtInstant.atOffset(ZoneOffset.UTC);

            Optional<UserAccountEntity> localAccount = localAccounts.stream()
                    .filter(acc -> acc.getZitadelSub().equals(zitadelUserId)
                            && acc.getOrganizationId().equals(orgId))
                    .findFirst();

            MemberResponse member = new MemberResponse();
            member.setZitadelUserId(zitadelUserId);
            member.setEmail(email);
            member.setFirstName(firstName);
            member.setLastName(lastName);
            member.setCreatedAt(createdAt);
            member.setAccountId(localAccount.map(UserAccountEntity::getId).orElse(null));

            if (localAccount.isPresent() && "USER_STATE_ACTIVE".equals(state)) {
                member.setStatus(MemberStatus.ACTIVE);
            } else {
                member.setStatus(MemberStatus.INVITED);
            }

            List<String> roles = userRoles.getOrDefault(zitadelUserId, List.of());
            if (roles.contains(MemberRole.COMPANY_ADMIN.getValue())) {
                member.setRole(MemberRole.COMPANY_ADMIN);
            } else {
                member.setRole(MemberRole.USER);
            }

            return member;
        }).collect(Collectors.toList());

        MemberListResponse response = new MemberListResponse();
        response.setMembers(members);
        return response;
    }

    public void changeMemberRole(UUID orgId, String zitadelUserId, ChangeRoleRequest request) {
        validateCallerBelongsToOrg(orgId);
        MemberRole newRole = request.getRole();

        OrganizationEntity organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        Optional<JsonNode> grantOpt = zitadelManagementClient.searchUserGrants(mainOrgId, goaldoneProjectId, zitadelUserId);
        if (grantOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User grant not found");
        }

        JsonNode grant = grantOpt.get();
        String grantId = grant.get("grantId").asText();
        List<String> currentRoles = new ArrayList<>();
        grant.get("roleKeys").forEach(role -> currentRoles.add(role.asText()));

        if (currentRoles.contains(newRole.getValue()) && currentRoles.size() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_UNCHANGED");
        }

        // Last admin check
        if (newRole == MemberRole.USER && currentRoles.contains(MemberRole.COMPANY_ADMIN.getValue())) {
            int adminCount = zitadelManagementClient.countGrantsByRole(mainOrgId, goaldoneProjectId, MemberRole.COMPANY_ADMIN.getValue());
            // Filter further by user organization if possible, but countGrantsByRole in root org with specific role already does part of it.
            // Actually, we need to count admins ONLY in this organization.
            
            // Re-fetch all grants for this org to be sure
            JsonNode allGrants = zitadelManagementClient.listAllGrants(mainOrgId, goaldoneProjectId, organization.getZitadelOrgId());
            long orgAdminCount = 0;
            if (allGrants.has("authorizations")) {
                for (JsonNode auth : allGrants.get("authorizations")) {
                    if (auth.has("roles")) {
                        for (JsonNode role : auth.get("roles")) {
                            if (MemberRole.COMPANY_ADMIN.getValue().equals(role.path("key").asText())) {
                                orgAdminCount++;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (orgAdminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED");
            }
        }

        zitadelManagementClient.updateUserGrant(grantId, mainOrgId, List.of(newRole.getValue()));
    }

    public void removeMember(UUID orgId, String zitadelUserId) {
        validateCallerBelongsToOrg(orgId);

        String callerSub = getCallerSub();
        if (callerSub.equals(zitadelUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CANNOT_REMOVE_SELF");
        }

        OrganizationEntity organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        Optional<JsonNode> grantOpt = zitadelManagementClient.searchUserGrants(mainOrgId, goaldoneProjectId, zitadelUserId);
        if (grantOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in organization");
        }

        JsonNode grant = grantOpt.get();
        List<String> roles = new ArrayList<>();
        grant.get("roleKeys").forEach(role -> roles.add(role.asText()));

        if (roles.contains(MemberRole.COMPANY_ADMIN.getValue())) {
            // Check if last admin
            JsonNode allGrants = zitadelManagementClient.listAllGrants(mainOrgId, goaldoneProjectId, organization.getZitadelOrgId());
            long orgAdminCount = 0;
            if (allGrants.has("authorizations")) {
                for (JsonNode auth : allGrants.get("authorizations")) {
                    if (auth.has("roles")) {
                        for (JsonNode role : auth.get("roles")) {
                            if (MemberRole.COMPANY_ADMIN.getValue().equals(role.path("key").asText())) {
                                orgAdminCount++;
                                break;
                            }
                        }
                    }
                }
            }
            if (orgAdminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_REMOVED");
            }
        }

        Optional<UserAccountEntity> accountOpt = userAccountRepository.findByZitadelSub(zitadelUserId)
                .filter(acc -> acc.getOrganizationId().equals(orgId));

        if (accountOpt.isPresent()) {
            userAccountDeletionService.deleteUserAccount(accountOpt.get().getId());
        } else {
            zitadelManagementClient.deleteUser(zitadelUserId);
        }
    }

    private void validateCallerBelongsToOrg(UUID orgId) {
        String callerSub = getCallerSub();
        UserAccountEntity callerAccount = userAccountRepository.findByZitadelSub(callerSub)
                .orElseThrow(() -> new NotMemberOfOrganizationException("Caller account not found"));

        if (!callerAccount.getOrganizationId().equals(orgId)) {
            throw new NotMemberOfOrganizationException("Caller does not belong to organization: " + orgId);
        }
    }

    private String getCallerSub() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getSubject();
    }
}
