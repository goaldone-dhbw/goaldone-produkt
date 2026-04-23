package de.goaldone.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "working_times")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkingTimeEntity {

    @Id
    private UUID id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "user_identity_id", nullable = false)
    private UUID userIdentityId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

