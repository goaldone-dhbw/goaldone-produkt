package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findTopByEmailOrderByCreatedAtDesc(String email);
}
