package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findTopByEmailOrderByCreatedAtDesc(String email);

    @Query("SELECT i FROM Invitation i WHERE i.company.id = :companyId AND i.acceptanceReason IS NULL")
    List<Invitation> findPendingByCompanyId(@Param("companyId") UUID companyId);
}
