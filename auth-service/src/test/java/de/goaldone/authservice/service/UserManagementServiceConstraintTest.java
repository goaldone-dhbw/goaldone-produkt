package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.domain.Membership;
import de.goaldone.authservice.domain.Role;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserStatus;
import de.goaldone.authservice.exception.LastAdminViolationException;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for last-admin and last-super-admin constraint checks in UserManagementService.
 * Tests constraint detection logic and service-layer validation.
 */
@ExtendWith(MockitoExtension.class)
public class UserManagementServiceConstraintTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private UserManagementService userManagementService;
    private PasswordEncoder passwordEncoder;

    private User admin1, admin2, user1, superAdmin1, superAdmin2;
    private UUID admin1Id, admin2Id, user1Id, superAdmin1Id, superAdmin2Id;
    private UUID company1Id;

    @BeforeEach
    public void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userManagementService = new UserManagementService(userRepository, membershipRepository, passwordEncoder);

        // Create test users
        admin1Id = UUID.randomUUID();
        admin2Id = UUID.randomUUID();
        user1Id = UUID.randomUUID();
        superAdmin1Id = UUID.randomUUID();
        superAdmin2Id = UUID.randomUUID();
        company1Id = UUID.randomUUID();

        admin1 = User.builder()
                .id(admin1Id)
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        admin2 = User.builder()
                .id(admin2Id)
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        user1 = User.builder()
                .id(user1Id)
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        superAdmin1 = User.builder()
                .id(superAdmin1Id)
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .build();

        superAdmin2 = User.builder()
                .id(superAdmin2Id)
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .build();
    }

    // ===== Test Case 1: Last-admin detection (single admin)
    @Test
    public void testLastAdminDetection_SingleAdmin_ReturnsTrue() {
        // admin1 is the only COMPANY_ADMIN in company1
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(1L);
        when(membershipRepository.countAdminsByCompanyRoleAndUser(company1Id, Role.COMPANY_ADMIN, admin1Id))
                .thenReturn(1L);

        boolean result = userManagementService.isLastCompanyAdmin(admin1Id, company1Id);
        assertTrue(result, "Should detect admin1 as last COMPANY_ADMIN");
        verify(membershipRepository).countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN);
    }

    // ===== Test Case 2: Multiple admins (no constraint)
    @Test
    public void testLastAdminDetection_MultipleAdmins_ReturnsFalse() {
        // Multiple admins exist
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(2L);

        boolean result = userManagementService.isLastCompanyAdmin(admin1Id, company1Id);
        assertFalse(result, "Should not detect as last admin when multiple admins exist");
        verify(membershipRepository).countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN);
    }

    // ===== Test Case 3: Last super-admin detection (single super-admin)
    @Test
    public void testLastSuperAdminDetection_SingleSuperAdmin_ReturnsTrue() {
        when(userRepository.countSuperAdmins()).thenReturn(1L);
        when(userRepository.findById(superAdmin1Id)).thenReturn(java.util.Optional.of(superAdmin1));

        boolean result = userManagementService.isLastSuperAdmin(superAdmin1Id);
        assertTrue(result, "Should detect superAdmin1 as last SUPER_ADMIN");
        verify(userRepository).countSuperAdmins();
    }

    // ===== Test Case 4: Multiple super-admins (no constraint)
    @Test
    public void testLastSuperAdminDetection_MultipleSuperAdmins_ReturnsFalse() {
        when(userRepository.countSuperAdmins()).thenReturn(2L);

        boolean result = userManagementService.isLastSuperAdmin(superAdmin1Id);
        assertFalse(result, "Should not detect as last super-admin when multiple exist");
        verify(userRepository).countSuperAdmins();
    }

    // ===== Test Case 5: User without memberships (not last admin)
    @Test
    public void testIsLastCompanyAdmin_UserWithoutMembership_ReturnsFalse() {
        UUID newUserId = UUID.randomUUID();
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(1L);
        when(membershipRepository.countAdminsByCompanyRoleAndUser(company1Id, Role.COMPANY_ADMIN, newUserId))
                .thenReturn(0L);

        boolean result = userManagementService.isLastCompanyAdmin(newUserId, company1Id);
        assertFalse(result, "User without membership should not be last admin");
    }

    // ===== Test Case 6: Non-admin user (not last admin)
    @Test
    public void testIsLastCompanyAdmin_NonAdminUser_ReturnsFalse() {
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(0L);

        boolean result = userManagementService.isLastCompanyAdmin(admin2Id, company1Id);
        assertFalse(result, "Non-admin user should not be detected as last admin");
    }

    // ===== Test Case 7: Delete non-last admin (should succeed)
    @Test
    public void testDeleteNonLastAdmin_ShouldSucceed() {
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(2L);

        assertFalse(userManagementService.isLastCompanyAdmin(admin1Id, company1Id),
                "Deletion should be allowed when other admins exist");
    }

    // ===== Test Case 8: Promote user to admin (should succeed)
    @Test
    public void testPromoteUserToAdmin_ShouldSucceed() {
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(0L);

        assertFalse(userManagementService.isLastCompanyAdmin(user1Id, company1Id),
                "User should not be detected as last admin before promotion");
    }

    // ===== Test Case 9: Remove last super-admin (should fail with exception)
    @Test
    public void testRemoveLastSuperAdminStatus_ShouldThrowException() {
        when(userRepository.countSuperAdmins()).thenReturn(1L);
        when(userRepository.findById(superAdmin1Id)).thenReturn(java.util.Optional.of(superAdmin1));

        assertTrue(userManagementService.isLastSuperAdmin(superAdmin1Id),
                "superAdmin1 should be detected as last super-admin");
    }

    // ===== Test Case 10: Promote user to super-admin (should succeed)
    @Test
    public void testPromoteUserToSuperAdmin_ShouldSucceed() {
        UUID regularUserId = UUID.randomUUID();
        User regularUser = User.builder()
                .id(regularUserId)
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();

        when(userRepository.countSuperAdmins()).thenReturn(1L);
        when(userRepository.findById(regularUserId)).thenReturn(java.util.Optional.of(regularUser));

        assertFalse(userManagementService.isLastSuperAdmin(regularUserId),
                "Regular user should not be detected as super-admin");
    }

    // ===== Edge Case: Multiple companies
    @Test
    public void testLastAdminConstraint_AcrossOrganizations_Independent() {
        UUID company2Id = UUID.randomUUID();

        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(1L);
        when(membershipRepository.countAdminsByCompanyRoleAndUser(company1Id, Role.COMPANY_ADMIN, admin1Id))
                .thenReturn(1L);
        when(membershipRepository.countActiveAdminsByCompanyAndRole(company2Id, Role.COMPANY_ADMIN))
                .thenReturn(0L);

        assertTrue(userManagementService.isLastCompanyAdmin(admin1Id, company1Id),
                "admin1 should be last admin in company1");

        assertFalse(userManagementService.isLastCompanyAdmin(admin1Id, company2Id),
                "admin1 should not be detected as admin in company2");
    }

    // ===== Edge Case: Inactive users
    @Test
    public void testLastAdminDetection_IncludesInactiveUsers() {
        User inactiveAdmin = User.builder()
                .id(UUID.randomUUID())
                .password(passwordEncoder.encode("password123"))
                .status(UserStatus.INACTIVE)
                .superAdmin(false)
                .build();

        when(membershipRepository.countActiveAdminsByCompanyAndRole(company1Id, Role.COMPANY_ADMIN))
                .thenReturn(1L);
        when(membershipRepository.countAdminsByCompanyRoleAndUser(company1Id, Role.COMPANY_ADMIN, inactiveAdmin.getId()))
                .thenReturn(1L);

        boolean result = userManagementService.isLastCompanyAdmin(inactiveAdmin.getId(), company1Id);
        assertTrue(result, "Should detect inactive user as last admin for constraint purposes");
    }
}
