package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u JOIN u.emails e LEFT JOIN FETCH u.memberships m LEFT JOIN FETCH m.company WHERE e.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u JOIN u.emails e LEFT JOIN FETCH u.memberships m LEFT JOIN FETCH m.company WHERE e.email = :email AND e.isPrimary = true")
    Optional<User> findByPrimaryEmail(@Param("email") String email);

    /**
     * Count all users with super_admin = true.
     * Used to check if a user is the last system super-administrator.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.superAdmin = true")
    long countSuperAdmins();
}
