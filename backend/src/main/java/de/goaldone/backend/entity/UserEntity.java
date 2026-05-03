package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a global user.
 * This entity is mapped to the "users" database table and contains identity information
 * that is consistent across different organizations.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    /**
     * The unique identifier for the user.
     */
    @Id
    private UUID id;

    /**
     * The unique subject identifier (SUB) assigned to this user by the Identity Provider (IAM).
     */
    @Column(name = "auth_user_id", nullable = false, unique = true, length = 64)
    private String authUserId;

    /**
     * The timestamp when this user record was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
