package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an organization within the GoalDone system.
 * Organizations are mapped to organizations in the external IAM (Zitadel).
 * This entity is mapped to the "organizations" database table.
 */
@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {
    /** The unique internal identifier for the organization. */
    @Id
    private UUID id;

    /** The unique identifier assigned to this organization by Zitadel (IAM). */
    @Column(name = "zitadel_org_id", nullable = false, unique = true, length = 64)
    private String zitadelOrgId;

    /** The display name of the organization. */
    @Column(nullable = false, length = 255)
    private String name;

    /** The timestamp when this organization record was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
