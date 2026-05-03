package de.goaldone.backend.repository;

import de.goaldone.backend.entity.MembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link MembershipEntity}.
 * Manages user memberships within organizations and their links to global users.
 */
@Repository
public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {
    /**
     * Finds a membership by the authentication user ID of the associated user.
     *
     * @param authUserId The authentication user ID.
     * @return An {@link Optional} containing the membership if found.
     */
    Optional<MembershipEntity> findByUserAuthUserId(String authUserId);

    /**
     * Finds all memberships by the authentication user ID of the associated user.
     *
     * @param authUserId The authentication user ID.
     * @return A list of {@link MembershipEntity} objects.
     */
    List<MembershipEntity> findAllByUserAuthUserId(String authUserId);

    /**
     * Finds a membership by the authentication user ID of the associated user and organization ID.
     *
     * @param authUserId     The authentication user ID.
     * @param organizationId The organization ID.
     * @return An {@link Optional} containing the membership if found.
     */
    Optional<MembershipEntity> findByUserAuthUserIdAndOrganizationId(String authUserId, UUID organizationId);

    /**
     * Counts the number of memberships associated with a specific user.
     *
     * @param userId The UUID of the user.
     * @return The count of memberships.
     */
    long countByUserId(UUID userId);

    /**
     * Finds all memberships associated with a specific user.
     *
     * @param userId The UUID of the user.
     * @return A list of {@link MembershipEntity} objects.
     */
    List<MembershipEntity> findAllByUserId(UUID userId);

    /**
     * Finds all memberships by their IDs.
     * @param ids The collection of membership IDs.
     * @return A list of {@link MembershipEntity} objects.
     */
    List<MembershipEntity> findAllByIdIn(Collection<UUID> ids);

    /**
     * Finds a membership by its ID and the associated user ID.
     *
     * @param id     The UUID of the membership.
     * @param userId The UUID of the user.
     * @return An {@link Optional} containing the membership if found.
     */
    Optional<MembershipEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds a membership by its ID and the organization ID.
     *
     * @param id             The UUID of the membership.
     * @param organizationId The UUID of the organization.
     * @return An {@link Optional} containing the membership if found.
     */
    Optional<MembershipEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds all memberships for a specific organization.
     *
     * @param organizationId The UUID of the organization.
     * @return A list of {@link MembershipEntity} objects.
     */
    List<MembershipEntity> findAllByOrganizationId(UUID organizationId);

    /**
     * Finds organization IDs where both specified users have memberships.
     * This is used to detect potential identity merges or conflicts across organizations.
     *
     * @param idA The UUID of the first user.
     * @param idB The UUID of the second user.
     * @return A list of organization UUIDs where both users are present.
     */
    @Query("""
            SELECT DISTINCT m.organizationId FROM MembershipEntity m
            WHERE m.user.id IN (:idA, :idB)
            GROUP BY m.organizationId
            HAVING COUNT(DISTINCT m.user.id) > 1
            """)
    List<UUID> findOrgIdsWithMultipleUsers(@Param("idA") UUID idA, @Param("idB") UUID idB);
}
