package de.goaldone.backend.repository;

import de.goaldone.backend.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link UserAccountEntity}.
 * Manages user accounts and their links to identities and Zitadel users.
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    /**
     * Finds a user account by its Zitadel sub (subject) ID.
     *
     * @param zitadelSub The subject ID from Zitadel.
     * @return An {@link Optional} containing the user account if found.
     */
    Optional<UserAccountEntity> findByZitadelSub(String zitadelSub);

    /**
     * Counts the number of user accounts associated with a specific user identity.
     *
     * @param userIdentityId The UUID of the user identity.
     * @return The count of user accounts.
     */
    long countByUserIdentityId(UUID userIdentityId);

    /**
     * Finds all user accounts associated with a specific user identity.
     *
     * @param userIdentityId The UUID of the user identity.
     * @return A list of {@link UserAccountEntity} objects.
     */
    List<UserAccountEntity> findAllByUserIdentityId(UUID userIdentityId);

    /**
     * Finds all user accounts by their IDs.
     * @param ids
     * @return A list of {@link UserAccountEntity} objects.
     */
    List<UserAccountEntity> findAllByIdIn(Collection<UUID> ids);

    /**
     * Finds a user account by its ID and the associated user identity ID.
     *
     * @param id             The UUID of the user account.
     * @param userIdentityId The UUID of the user identity.
     * @return An {@link Optional} containing the user account if found.
     */
    Optional<UserAccountEntity> findByIdAndUserIdentityId(UUID id, UUID userIdentityId);

    /**
     * Finds organization IDs where both specified user identities have accounts.
     * This is used to detect potential identity merges or conflicts across organizations.
     *
     * @param idA The UUID of the first user identity.
     * @param idB The UUID of the second user identity.
     * @return A list of organization UUIDs where both identities are present.
     */
    @Query("""
            SELECT DISTINCT a.organizationId FROM UserAccountEntity a
            WHERE a.userIdentityId IN (:idA, :idB)
            GROUP BY a.organizationId
            HAVING COUNT(DISTINCT a.userIdentityId) > 1
            """)
    List<UUID> findOrgIdsWithMultipleIdentities(@Param("idA") UUID idA, @Param("idB") UUID idB);
}
