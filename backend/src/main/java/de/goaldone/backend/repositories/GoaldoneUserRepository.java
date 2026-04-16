package de.goaldone.backend.repositories;

import de.goaldone.backend.entity.GoaldoneUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GoaldoneUserRepository extends JpaRepository<GoaldoneUser, UUID> {

}
