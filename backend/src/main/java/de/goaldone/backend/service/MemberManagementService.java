package de.goaldone.backend.service;

import com.zitadel.model.AuthorizationServiceListAuthorizationsResponse;
import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.client.UserGrantDto;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.exception.NotMemberOfOrganizationException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.MembershipRepository;
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
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipDeletionService membershipDeletionService;
    private final UserService userService;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    /**
     * Lists all members of a specific organization.
     * Roles are fetched dynamically from the identity provider.
     *
     * @param xOrgID The organization ID context.
     * @return A {@link MemberListResponse} containing the list of members.
     */
    public MemberListResponse listMembers(UUID xOrgID) {
        userService.validateMembership(xOrgID);

        OrganizationEntity organization = organizationRepository.findById(xOrgID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        AuthorizationServiceListAuthorizationsResponse grantsResponse = zitadelManagementClient.listAllGrants(
                mainOrgId, goaldoneProjectId, organization.getAuthCompanyId()
        );

        List<String> userIds = new ArrayList<>();
        Map<String, List<String>> userRoles = new HashMap<>();

        if (grantsResponse.getAuthorizations() != null) {
            grantsResponse.getAuthorizations().forEach(auth -> {
                String userId = auth.getUser() != null ? auth.getUser().getId() : null;
                if (userId == null || userId.isBlank()) return;

                userIds.add(userId);

                List<String> rolesList = new ArrayList<>();
                if (auth.getRoles() != null) {
                    auth.getRoles().forEach(role -> rolesList.add(role.getKey()));
                }
                userRoles.put(userId, rolesList);
            });
        }

        List<UserServiceUser> zitadelUsers = zitadelManagementClient.listUsersByIds(userIds);
        List<MembershipEntity> localMemberships = membershipRepository.findAllByOrganizationId(xOrgID);

        List<MemberResponse> members = zitadelUsers.stream().map(user -> {
            String zitadelUserId  = user.getUserId();
            String email          = user.getHuman() != null && user.getHuman().getEmail() != null ? user.getHuman().getEmail().getEmail() : "";
            String firstName      = user.getHuman() != null && user.getHuman().getProfile() != null ? user.getHuman().getProfile().getGivenName() : "";
            String lastName       = user.getHuman() != null && user.getHuman().getProfile() != null ? user.getHuman().getProfile().getFamilyName() : "";
            String state          = user.getState() != null ? user.getState().toString() : "";

            log.debug("Mapping user: {} ({})", email, zitadelUserId);

            OffsetDateTime createdAt;
            if (user.getDetails() != null && user.getDetails().getCreationDate() != null) {
                Object creationDate = user.getDetails().getCreationDate();
                if (creationDate instanceof OffsetDateTime) {
                    createdAt = (OffsetDateTime) creationDate;
                } else {
                    try {
                        Instant createdAtInstant = Instant.parse(creationDate.toString());
                        createdAt = createdAtInstant.atOffset(ZoneOffset.UTC);
                    } catch (Exception e) {
                        log.warn("Could not parse creation date for user {}: {}", zitadelUserId, e.getMessage());
                        createdAt = Instant.now().atOffset(ZoneOffset.UTC);
                    }
                }
            } else {
                createdAt = Instant.now().atOffset(ZoneOffset.UTC);
            }

            Optional<MembershipEntity> localMembership = localMemberships.stream()
                    .filter(m -> m.getUser().getAuthUserId().equals(zitadelUserId))
                    .findFirst();

            MemberResponse member = new MemberResponse();
            member.setZitadelUserId(zitadelUserId);
            member.setEmail(email);
            member.setFirstName(firstName);
            member.setLastName(lastName);
            member.setCreatedAt(createdAt);
            member.setAccountId(localMembership.map(MembershipEntity::getId).orElse(null));

            if (localMembership.isPresent() && "USER_STATE_ACTIVE".equals(state)) {
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

    /**
     * Changes the role of a member within an organization.
     *
     * @param xOrgID         The organization ID context.
     * @param zitadelUserId The identity provider ID of the member.
     * @param request       The change role request.
     */
    public void changeMemberRole(UUID xOrgID, String zitadelUserId, ChangeRoleRequest request) {
        userService.validateMembership(xOrgID);
        MemberRole newRole = request.getRole();

        OrganizationEntity organization = organizationRepository.findById(xOrgID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        Optional<UserGrantDto> grantOpt = zitadelManagementClient.searchUserGrants(mainOrgId, goaldoneProjectId, zitadelUserId);
        if (grantOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User grant not found");
        }

        UserGrantDto grant = grantOpt.get();
        String grantId = grant.grantId();
        List<String> currentRoles = new ArrayList<>(grant.roleKeys());

        if (currentRoles.contains(newRole.getValue()) && currentRoles.size() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_UNCHANGED");
        }

        // Last admin check
        if (newRole == MemberRole.USER && currentRoles.contains(MemberRole.COMPANY_ADMIN.getValue())) {
            AuthorizationServiceListAuthorizationsResponse allGrants = zitadelManagementClient.listAllGrants(mainOrgId, goaldoneProjectId, organization.getAuthCompanyId());
            long orgAdminCount = 0;
            if (allGrants.getAuthorizations() != null) {
                for (var auth : allGrants.getAuthorizations()) {
                    if (auth.getRoles() != null) {
                        for (var role : auth.getRoles()) {
                            if (MemberRole.COMPANY_ADMIN.getValue().equals(role.getKey())) {
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

    /**
     * Removes a member from an organization.
     *
     * @param xOrgID         The organization ID context.
     * @param zitadelUserId The identity provider ID of the member.
     */
    public void removeMember(UUID xOrgID, String zitadelUserId) {
        userService.validateMembership(xOrgID);

        String callerSub = getCallerSub();
        if (callerSub.equals(zitadelUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CANNOT_REMOVE_SELF");
        }

        OrganizationEntity organization = organizationRepository.findById(xOrgID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        Optional<UserGrantDto> grantOpt = zitadelManagementClient.searchUserGrants(mainOrgId, goaldoneProjectId, zitadelUserId);
        if (grantOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in organization");
        }

        UserGrantDto grant = grantOpt.get();
        List<String> roles = new ArrayList<>(grant.roleKeys());

        if (roles.contains(MemberRole.COMPANY_ADMIN.getValue())) {
            AuthorizationServiceListAuthorizationsResponse allGrants = zitadelManagementClient.listAllGrants(mainOrgId, goaldoneProjectId, organization.getAuthCompanyId());
            long orgAdminCount = 0;
            if (allGrants.getAuthorizations() != null) {
                for (var auth : allGrants.getAuthorizations()) {
                    if (auth.getRoles() != null) {
                        for (var role : auth.getRoles()) {
                            if (MemberRole.COMPANY_ADMIN.getValue().equals(role.getKey())) {
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

        Optional<MembershipEntity> membershipOpt = membershipRepository.findByUserAuthUserIdAndOrganizationId(zitadelUserId, xOrgID);

        if (membershipOpt.isPresent()) {
            membershipDeletionService.deleteMembership(membershipOpt.get().getId());
        } else {
            zitadelManagementClient.deleteUser(zitadelUserId);
        }
    }

    private String getCallerSub() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getSubject();
    }
}
