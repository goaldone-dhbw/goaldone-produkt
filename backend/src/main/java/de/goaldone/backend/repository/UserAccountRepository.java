package de.goaldone.backend.repository;

import de.goaldone.backend.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    Optional<UserAccountEntity> findByZitadelSub(String zitadelSub);

    long countByUserIdentityId(UUID userIdentityId);

    List<UserAccountEntity> findAllByUserIdentityId(UUID userIdentityId);

    Optional<UserAccountEntity> findByIdAndUserIdentityId(UUID id, UUID userIdentityId);

    @Query("""
            SELECT DISTINCT a.organizationId FROM UserAccountEntity a
            WHERE a.userIdentityId IN (:idA, :idB)
            GROUP BY a.organizationId
            HAVING COUNT(DISTINCT a.userIdentityId) > 1
            """)
    List<UUID> findOrgIdsWithMultipleIdentities(@Param("idA") UUID idA, @Param("idB") UUID idB);
}
