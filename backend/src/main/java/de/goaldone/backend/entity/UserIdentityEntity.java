package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a global user identity that can be linked to multiple {@link UserAccountEntity}s.
 * This allows a user to consolidate their data and presence across multiple organizations.
 * This entity is mapped to the "user_identities" database table.
 */
@Entity
@Table(name = "user_identities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentityEntity {
    /** The unique identifier for the user identity. */
    @Id
    private UUID id;

    /** The timestamp when this user identity record was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
