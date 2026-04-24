package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountEntity {
    @Id
    private UUID id;

    @Column(name = "zitadel_sub", nullable = false, unique = true, length = 64)
    private String zitadelSub;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_identity_id", nullable = false)
    private UUID userIdentityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @OneToMany(mappedBy = "userAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkingTimeEntity> workingTimes;
}
