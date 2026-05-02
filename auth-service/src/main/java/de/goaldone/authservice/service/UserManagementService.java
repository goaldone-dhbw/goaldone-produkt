package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.Role;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserEmail;
import de.goaldone.authservice.dto.UserRequest;
import de.goaldone.authservice.dto.UserResponse;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(UserRequest request) {
        User user = User.builder()
                .password(passwordEncoder.encode(request.getPassword()))
                .status(request.getStatus())
                .superAdmin(request.isSuperAdmin())
                .build();

        if (request.getEmails() != null) {
            request.getEmails().forEach(emailReq -> {
                UserEmail userEmail = UserEmail.builder()
                        .email(emailReq.getEmail())
                        .isPrimary(emailReq.isPrimary())
                        .verified(emailReq.isVerified())
                        .user(user)
                        .build();
                user.addEmail(userEmail);
            });
        }

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByPrimaryEmail(String email) {
        User user = userRepository.findByPrimaryEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with primary email: " + email));
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setStatus(request.getStatus());
        user.setSuperAdmin(request.isSuperAdmin());

        // Update emails if needed. For simplicity, we might want to handle this separately
        // but the plan says "Update status, superAdmin, etc."
        // If we want to support updating emails here:
        if (request.getEmails() != null) {
            user.getEmails().clear();
            request.getEmails().forEach(emailReq -> {
                UserEmail userEmail = UserEmail.builder()
                        .email(emailReq.getEmail())
                        .isPrimary(emailReq.isPrimary())
                        .verified(emailReq.isVerified())
                        .user(user)
                        .build();
                user.addEmail(userEmail);
            });
        }

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .status(user.getStatus())
                .superAdmin(user.isSuperAdmin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .emails(user.getEmails().stream()
                        .map(e -> UserResponse.EmailResponse.builder()
                                .id(e.getId())
                                .email(e.getEmail())
                                .primary(e.isPrimary())
                                .verified(e.isVerified())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Check if a user is the last COMPANY_ADMIN in an organization.
     * Queries the membership repository for active admins with COMPANY_ADMIN role.
     *
     * @param userId the user ID to check
     * @param companyId the organization ID to check
     * @return true if exactly one admin exists and that admin is userId, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isLastCompanyAdmin(UUID userId, UUID companyId) {
        log.debug("Checking if user {} is last COMPANY_ADMIN in organization {}", userId, companyId);

        long totalAdmins = membershipRepository.countActiveAdminsByCompanyAndRole(companyId, Role.COMPANY_ADMIN);
        if (totalAdmins != 1) {
            log.debug("Organization {} has {} COMPANY_ADMINs, not a last-admin scenario", companyId, totalAdmins);
            return false;
        }

        long userAdminCount = membershipRepository.countAdminsByCompanyRoleAndUser(companyId, Role.COMPANY_ADMIN, userId);
        boolean isLastAdmin = userAdminCount > 0;

        log.debug("User {} is {} the last COMPANY_ADMIN in organization {}", userId, isLastAdmin ? "" : "NOT", companyId);
        return isLastAdmin;
    }

    /**
     * Check if a user is the last system super-administrator.
     * Queries the user repository for all super-admins system-wide.
     *
     * @param userId the user ID to check
     * @return true if exactly one super-admin exists and that super-admin is userId, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isLastSuperAdmin(UUID userId) {
        log.debug("Checking if user {} is last SUPER_ADMIN in the system", userId);

        long totalSuperAdmins = userRepository.countSuperAdmins();
        if (totalSuperAdmins != 1) {
            log.debug("System has {} super-admins, not a last-super-admin scenario", totalSuperAdmins);
            return false;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        boolean isLastSuperAdmin = user.isSuperAdmin();

        log.debug("User {} is {} the last SUPER_ADMIN in the system", userId, isLastSuperAdmin ? "" : "NOT");
        return isLastSuperAdmin;
    }
}
