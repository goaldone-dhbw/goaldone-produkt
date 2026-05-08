package de.goaldone.backend.repository;

import de.goaldone.backend.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link OrganizationEntity}.
 * Manages organization data, including integration with Zitadel.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
    /**
     * Finds an organization by its Zitadel organization ID.
     *
     * @param zitadelOrgId The organization ID from Zitadel.
     * @return An {@link Optional} containing the organization if found.
     */
    Optional<OrganizationEntity> findByZitadelOrgId(String zitadelOrgId);

    /**
     * Checks if an organization with the given name already exists.
     *
     * @param name The name of the organization.
     * @return {@code true} if an organization with this name exists, {@code false} otherwise.
     */
    boolean existsByName(String name);
}
