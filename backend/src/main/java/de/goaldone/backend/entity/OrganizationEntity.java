package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an organization within the GoalDone system.
 * After PK unification, the organization's local UUID equals the auth-service company UUID.
 * This entity is mapped to the "organizations" database table.
 */
@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {
    /** The unique internal identifier for the organization (equals the auth-service company UUID). */
    @Id
    private UUID id;

    /** The display name of the organization. */
    @Column(nullable = false, length = 255)
    private String name;

    /** The timestamp when this organization record was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
