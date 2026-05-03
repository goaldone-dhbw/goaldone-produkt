package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a global user.
 * After PK unification, the user's local UUID equals the auth-service user UUID.
 * This entity is mapped to the "users" database table.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    /**
     * The unique identifier for the user (equals the auth-service user UUID).
     */
    @Id
    private UUID id;

    /**
     * The timestamp when this user record was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
