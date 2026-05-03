package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.Membership;
import de.goaldone.authservice.domain.MembershipId;
import de.goaldone.authservice.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link Membership} entity.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {

    boolean existsByUserIdAndCompanyId(UUID userId, UUID companyId);

    List<Membership> findByCompanyId(UUID companyId);

    @Modifying
    @Query("DELETE FROM Membership m WHERE m.user.id = :userId AND m.company.id = :companyId")
    void deleteByUserIdAndCompanyId(@Param("userId") UUID userId, @Param("companyId") UUID companyId);

    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.company.id = :companyId")
    Optional<Membership> findByUserIdAndCompanyId(@Param("userId") UUID userId, @Param("companyId") UUID companyId);

    /**
     * Count active (non-deleted) memberships with COMPANY_ADMIN role in an organization.
     * Used to check if a user is the last admin.
     */
    @Query("SELECT COUNT(m) FROM Membership m " +
           "WHERE m.company.id = :companyId AND m.role = :role")
    long countActiveAdminsByCompanyAndRole(@Param("companyId") UUID companyId, @Param("role") Role role);

    /**
     * Count active memberships with COMPANY_ADMIN role in an organization for a specific user.
     */
    @Query("SELECT COUNT(m) FROM Membership m " +
           "WHERE m.company.id = :companyId AND m.role = :role AND m.user.id = :userId")
    long countAdminsByCompanyRoleAndUser(@Param("companyId") UUID companyId,
                                          @Param("role") Role role,
                                          @Param("userId") UUID userId);
}
