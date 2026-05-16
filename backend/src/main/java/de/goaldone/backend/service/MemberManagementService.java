package de.goaldone.backend.service;

import com.zitadel.Zitadel;
import com.zitadel.model.AuthorizationServiceAuthorization;
import com.zitadel.model.AuthorizationServiceListAuthorizationsResponse;
import com.zitadel.model.UserServiceListUsersResponse;
import com.zitadel.model.UserServiceUser;
import de.goaldone.backend.client.UserGrantDto;
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

import java.lang.reflect.Member;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberManagementService {

    private final ZitadelManagementClient zitadelManagementClient;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final DeletionService deletionService;
    private final UserIdentityService userIdentityService;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    @Value("${zitadel.goaldone.org-id}")
    private String mainOrgId;

    public MemberListResponse listMembers(UUID orgId) {

        OrganizationEntity organization = organizationRepository.findById(orgId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if(organization.getZitadelOrgId().equals(mainOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot list members of main organization on this endpoint");
        }

        UserServiceListUsersResponse zitadelUsers = zitadelManagementClient.listUsersOfOrg(organization.getZitadelOrgId());
        Map<String, List<MemberRole>> userRoles = findUserRoles(organization.getZitadelOrgId());


        List<String> userIds = new ArrayList<>(userRoles.keySet());
        List<UserAccountEntity> localAccounts = userAccountRepository.findAllByZitadelSubIn(userIds);

        List<MemberResponse> members = new ArrayList<>();

        zitadelUsers.getResult().stream().forEach(user -> {
            String zitadelUserId = user.getUserId();
            String email = user.getHuman() != null && user.getHuman().getEmail() != null ? user.getHuman().getEmail().getEmail() : "";
            String firstName = user.getHuman() != null && user.getHuman().getProfile() != null ? user.getHuman().getProfile().getGivenName() : "";
            String lastName = user.getHuman() != null && user.getHuman().getProfile() != null ? user.getHuman().getProfile().getFamilyName() : "";
            String state = user.getState() != null ? user.getState().toString() : "";

            Optional<UserAccountEntity> localAccount = localAccounts.stream()
                    .filter(acc -> acc.getZitadelSub().equals(zitadelUserId))
                    .findFirst();
            boolean localAccountExists = localAccount.isPresent();
            MemberStatus memberState;
            if(state.equals("USER_STATE_ACTIVE")) {
                if(localAccountExists) {
                    memberState = MemberStatus.ACTIVE;
                } else {
                    memberState = MemberStatus.INACTIVE;
                }
            } else {
                memberState = MemberStatus.INVITED;
            }

            UUID accountIdUuid = null;
            if (localAccount.isPresent()) {
                accountIdUuid = localAccount.get().getId();
            }
            members.add(new MemberResponse()
                    .zitadelUserId(zitadelUserId)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .createdAt(user.getDetails() != null && user.getDetails().getCreationDate() != null ? user.getDetails().getCreationDate() : null)
                    .status(memberState)
                    .role(userRoles.getOrDefault(zitadelUserId, List.of()).contains(MemberRole.COMPANY_ADMIN) ? MemberRole.COMPANY_ADMIN : MemberRole.USER)
                    .accountId(accountIdUuid)
             );
        });

        return new MemberListResponse(members);
    }

    /**
     * Internal method to fetch the roles of all users in an organization and map them to user IDs.
     * @param zitadelOrgId the ID of the organization
     * @return a map of user IDs with a list of their roles
     */
    private Map<String, List<MemberRole>> findUserRoles(String zitadelOrgId) {
        Map<String, List<MemberRole>> userRoles = new HashMap<>();

        AuthorizationServiceListAuthorizationsResponse grantsResponse = zitadelManagementClient.listAllGrants(goaldoneProjectId, zitadelOrgId);
        if (grantsResponse.getAuthorizations() != null) {
            grantsResponse.getAuthorizations().forEach(auth -> {
                String userId = auth.getUser() != null ? auth.getUser().getId() : null;
                if (userId == null || userId.isBlank()) return;

                List<MemberRole> rolesList = new ArrayList<>();
                if (auth.getRoles() != null) {
                    auth.getRoles().forEach(role -> rolesList.add(MemberRole.fromValue(role.getKey())));
                }
                userRoles.put(userId, rolesList);
            });
        }
        return userRoles;
    }

    /**
     * Changes the role of a member in the organization.
     *
     * @param orgId         the ID of the org (local DB Id)
     * @param zitadelUserId the ID of the user (Zitadel Id)
     * @param request       the request containing the new role
     */
    public void changeMemberRole(UUID orgId, String zitadelUserId, ChangeRoleRequest request) {
        MemberRole updatedRole = request.getRole();

        OrganizationEntity organization = organizationRepository.findById(orgId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        AuthorizationServiceListAuthorizationsResponse grantOpt = zitadelManagementClient.listAllGrants(goaldoneProjectId, organization.getZitadelOrgId());

        Optional<AuthorizationServiceAuthorization> user = grantOpt
                .getAuthorizations()
                .stream()
                .filter(auth -> auth.getUser() != null && auth.getUser().getId().equals(zitadelUserId)).findFirst();

        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in organization");
        }

        user.ifPresent(auth -> {
            if (auth.getRoles() != null) {
                if (auth.getRoles().size() != 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Multiple roles assigned to user, cannot change role");
                }

                List<MemberRole> currentRoles = auth
                        .getRoles()
                        .stream()
                        .map(role ->
                                MemberRole.fromValue(role.getKey())).collect(Collectors.toList());


                if (currentRoles.contains(updatedRole)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already assigned to user");
                }

                if (updatedRole == MemberRole.USER && currentRoles.contains(MemberRole.COMPANY_ADMIN) && !orgHasMoreThanOneAdmin(grantOpt.getAuthorizations())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED");
                }


                zitadelManagementClient.updateProjectAuthorization(auth.getId(), updatedRole.getValue());
            }
        });
    }

    /**
     * Checks if the organization has more than one admin.
     *
     * @param grants A list of grants to check.
     * @return true if the organization has more than one admin, false otherwise.
     */
    private boolean orgHasMoreThanOneAdmin(List<AuthorizationServiceAuthorization> grants) {
        AtomicInteger adminCount = new AtomicInteger();
        grants.forEach(auth -> {
            if (auth.getRoles() != null) {
                auth.getRoles().forEach(role -> {
                    if (MemberRole.COMPANY_ADMIN.getValue().equals(role.getKey())) {
                        adminCount.getAndIncrement();
                    }
                });

            }
        });

        return adminCount.get() > 1;
    }

    public void removeMember(UUID orgId, String zitadelUserId) {
        String callerSub = getCallerSub();
        if (callerSub.equals(zitadelUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CANNOT_REMOVE_SELF");
        }

        OrganizationEntity organization = organizationRepository.findById(orgId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        Optional<UserGrantDto> grantOpt = zitadelManagementClient.searchUserGrants(mainOrgId, goaldoneProjectId, zitadelUserId);
        if (grantOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in organization");
        }

        UserGrantDto grant = grantOpt.get();
        List<String> roles = new ArrayList<>(grant.roleKeys());

        if (roles.contains(MemberRole.COMPANY_ADMIN.getValue())) {
            // Check if last admin
            int orgAdminCount = LastAdminCheck(organization);
            if (orgAdminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_REMOVED");
            }
        }

        Optional<UserAccountEntity> accountOpt = userAccountRepository.findByZitadelSub(zitadelUserId).filter(acc -> acc.getOrganizationId().equals(orgId));

        if (accountOpt.isPresent()) {
            deletionService.deleteUserAccount(accountOpt.get().getId());
        } else {
            zitadelManagementClient.deleteUser(zitadelUserId);
        }
    }

    /**
     * Checks if the organization has only one admin left.
     *
     * @param organization the organization to check
     * @return the number of admin grants in the organization
     */
    private int LastAdminCheck(OrganizationEntity organization) {
        AuthorizationServiceListAuthorizationsResponse allGrants = zitadelManagementClient.listAllGrants(goaldoneProjectId, organization.getZitadelOrgId());
        int orgAdminCount = 0;
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
        return orgAdminCount;
    }

    /**
     * Helper method to get the subject (sub) of the caller's JWT.
     *
     * @return the subject (sub) of the caller's JWT
     */
    private String getCallerSub() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getSubject();
    }
}
