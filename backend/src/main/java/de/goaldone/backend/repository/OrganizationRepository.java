package de.goaldone.backend.repository;

import de.goaldone.backend.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for {@link OrganizationEntity}.
 * After PK unification, organization.id equals the auth-service company UUID.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    /**
     * Checks if an organization with the given name already exists.
     *
     * @param name The name of the organization.
     * @return {@code true} if an organization with this name exists, {@code false} otherwise.
     */
    boolean existsByName(String name);
}
