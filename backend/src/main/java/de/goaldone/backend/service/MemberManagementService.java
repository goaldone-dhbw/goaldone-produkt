package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.client.dto.AuthMemberResponse;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberManagementService {

    private final AuthServiceManagementClient authServiceClient;
    private final MembershipRepository membershipRepository;
    private final UserService userService;

    public MemberListResponse listMembers(UUID xOrgID) {
        userService.validateMembership(xOrgID);

        List<AuthMemberResponse> authMembers = authServiceClient.getMembers(xOrgID);

        List<MemberResponse> members = authMembers.stream().map(authMember -> {
            MemberResponse member = new MemberResponse();
            member.setUserId(authMember.getUserId());
            member.setEmail(authMember.getEmail());
            member.setFirstName(authMember.getFirstName());
            member.setLastName(authMember.getLastName());
            if (authMember.getRole() != null) {
                try {
                    member.setRole(MemberRole.fromValue(authMember.getRole()));
                } catch (IllegalArgumentException e) {
                    member.setRole(MemberRole.USER);
                }
            }
            if (authMember.getStatus() != null) {
                try {
                    member.setStatus(MemberStatus.fromValue(authMember.getStatus()));
                } catch (IllegalArgumentException e) {
                    member.setStatus(MemberStatus.ACTIVE);
                }
            }
            return member;
        }).collect(Collectors.toList());

        MemberListResponse response = new MemberListResponse();
        response.setMembers(members);
        return response;
    }

    public void changeMemberRole(UUID xOrgID, UUID userId, ChangeRoleRequest request) {
        userService.validateMembership(xOrgID);

        try {
            authServiceClient.updateMembershipRole(userId, xOrgID, request.getRole());
        } catch (AuthServiceManagementException e) {
            if (e.getStatusCode() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_DEMOTED");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service error");
        }
    }

    public void removeMember(UUID xOrgID, UUID userId) {
        userService.validateMembership(xOrgID);

        String callerSub = getCallerSub();
        if (callerSub.equals(userId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CANNOT_REMOVE_SELF");
        }

        try {
            authServiceClient.deleteMembership(userId, xOrgID);
        } catch (AuthServiceManagementException e) {
            if (e.getStatusCode() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_CANNOT_BE_REMOVED");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service error");
        }

        membershipRepository.findByUserIdAndOrganizationId(userId, xOrgID)
                .ifPresent(membershipRepository::delete);
    }

    private String getCallerSub() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userIdClaim = jwt.getClaimAsString("user_id");
        return (userIdClaim != null) ? userIdClaim : jwt.getSubject();
    }
}
