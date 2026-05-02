package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a specific user's account within an organization.
 * A single {@link UserIdentityEntity} can have multiple {@link UserAccountEntity}s across different organizations.
 * This entity is mapped to the "user_accounts" database table.
 */
@Entity
@Table(name = "user_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountEntity {
    /** The unique identifier for the user account. */
    @Id
    private UUID id;

    /** The unique subject identifier (SUB) assigned to this user by the Identity Provider (IAM). */
    @Column(name = "auth_user_id", nullable = false, unique = true, length = 64)
    private String authUserId;

    /** The ID of the {@link OrganizationEntity} this account belongs to. */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The ID of the {@link UserIdentityEntity} this account is associated with. */
    @Column(name = "user_identity_id", nullable = false)
    private UUID userIdentityId;

    /** The timestamp when this user account record was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** The timestamp when this user account was last seen/active. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** The list of working times defined for this specific account. */
    @OneToMany(mappedBy = "userAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkingTimeEntity> workingTimes;
}
