package de.goaldone.backend.repository;

import de.goaldone.backend.entity.UserIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {
}
