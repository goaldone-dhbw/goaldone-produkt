package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.config.ZitadelManagementProperties;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.EmailAlreadyInUseException;
import de.goaldone.backend.exception.LastSuperAdminException;
import de.goaldone.backend.exception.UserNotFoundException;
import de.goaldone.backend.exception.ZitadelUpstreamException;
import de.goaldone.backend.model.CreateSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminListResponse;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {
    private final ZitadelManagementClient zitadelClient;
    private final ZitadelManagementProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final LinkTokenRepository linkTokenRepository;

    /**
     * List all super-admins in the Goaldone organization.
     */
    public SuperAdminListResponse listSuperAdmins() {
        var grantsResponse = zitadelClient.listSuperAdminGrants();

        List<SuperAdminResponse> superAdmins = grantsResponse.result().stream()
                .map(grant -> {
                    ZitadelManagementClient.UserDetail userDetail = zitadelClient.getUserById(grant.userId());
                    return toSuperAdminResponse(userDetail);
                })
                .toList();

        return new SuperAdminListResponse(superAdmins);
    }

    /**
     * Create a new super-admin.
     * Flow:
     * 1. Check if email already exists in Zitadel
     * 2. Add human user to Goaldone org
     * 3. Add SUPER_ADMIN grant (with compensation on failure)
     * 4. Create invite code and send email (with compensation on failure)
     */
    public SuperAdminResponse createSuperAdmin(CreateSuperAdminRequest request) {
        String email = request.getEmail();

        // Check if email already exists in Zitadel
        if (zitadelClient.userExistsByEmail(email)) {
            throw new EmailAlreadyInUseException("Email already in use: " + email);
        }

        // Step 1: Add human user
        String userId;
        try {
            userId = zitadelClient.addHumanUser(email);
        } catch (Exception e) {
            throw new ZitadelUpstreamException("Failed to create user: " + e.getMessage());
        }

        // Step 2: Add user grant (SUPER_ADMIN role)
        try {
            zitadelClient.addUserGrant(userId, properties.getGoaldoneProjectId(), "SUPER_ADMIN");
        } catch (Exception e) {
            // Compensate: delete the user we just created
            try {
                zitadelClient.deleteUser(userId);
            } catch (Exception deleteEx) {
                log.error("Failed to compensate user creation by deleting user {}: {}", userId, deleteEx.getMessage());
            }
            throw e;
        }

        // Step 3: Create invite code and send email
        try {
            zitadelClient.createInviteCode(userId);
        } catch (Exception e) {
            // Compensate: delete the user
            try {
                zitadelClient.deleteUser(userId);
            } catch (Exception deleteEx) {
                log.error("Failed to compensate user creation by deleting user {}: {}", userId, deleteEx.getMessage());
            }
            throw e;
        }

        // Fetch the created user details
        ZitadelManagementClient.UserDetail userDetail = zitadelClient.getUserById(userId);
        return toSuperAdminResponse(userDetail);
    }

    /**
     * Delete a super-admin from both Zitadel and local DB.
     * Order: Zitadel first, then local DB.
     * Guard: Cannot delete the last remaining super-admin.
     */
    @Transactional
    public void deleteSuperAdmin(UUID userId) {
        String userIdStr = userId.toString();

        // Step 1: Check if this is the last super-admin
        var grantsResponse = zitadelClient.listSuperAdminGrants();
        boolean isLastSuperAdmin = grantsResponse.totalResult() <= 1 &&
                grantsResponse.result().stream()
                        .anyMatch(grant -> grant.userId().equals(userIdStr));

        if (isLastSuperAdmin) {
            throw new LastSuperAdminException("Cannot delete the last remaining super-admin");
        }

        // Step 2: Delete from Zitadel (fails with UserNotFoundException if not found)
        try {
            zitadelClient.deleteUser(userIdStr);
        } catch (UserNotFoundException e) {
            throw e;
        }

        // Step 3: Delete local shadow record and cascade-related data
        Optional<UserAccountEntity> userAccount = userAccountRepository.findByZitadelSub(userIdStr);
        if (userAccount.isPresent()) {
            UserAccountEntity account = userAccount.get();
            UUID userIdentityId = account.getUserIdentityId();

            // Delete the link tokens associated with this user account
            linkTokenRepository.deleteByInitiatorAccountId(account.getId());

            // Delete the user account
            userAccountRepository.delete(account);

            // Clean up user identity if it has no more accounts
            if (userIdentityRepository.existsById(userIdentityId)) {
                long remainingAccounts = userAccountRepository.countByUserIdentityId(userIdentityId);
                if (remainingAccounts == 0) {
                    userIdentityRepository.deleteById(userIdentityId);
                }
            }
        }
    }

    private SuperAdminResponse toSuperAdminResponse(ZitadelManagementClient.UserDetail userDetail) {
        UUID userId = UUID.fromString(userDetail.userId());
        OffsetDateTime createdAt = OffsetDateTime.parse(userDetail.createdAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        SuperAdminResponse response = new SuperAdminResponse(userId, userDetail.email(), createdAt, userDetail.state());
        if (userDetail.firstName() != null) {
            response.firstName(userDetail.firstName());
        }
        if (userDetail.lastName() != null) {
            response.lastName(userDetail.lastName());
        }
        return response;
    }
}
