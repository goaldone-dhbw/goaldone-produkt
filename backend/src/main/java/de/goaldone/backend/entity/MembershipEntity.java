package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a specific user's membership within an organization.
 * A single {@link UserEntity} can have multiple {@link MembershipEntity}s across different organizations.
 * This entity is mapped to the "memberships" database table.
 */
@Entity
@Table(name = "memberships")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipEntity {
    /** The unique identifier for the membership. */
    @Id
    private UUID id;

    /** The ID of the {@link OrganizationEntity} this membership belongs to. */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The {@link UserEntity} this membership is associated with. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** The timestamp when this membership record was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** The timestamp when this member was last seen/active. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** The list of working times defined for this specific membership. */
    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkingTimeEntity> workingTimes;
}
