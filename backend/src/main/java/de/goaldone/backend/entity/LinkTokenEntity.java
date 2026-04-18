package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "link_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkTokenEntity {
    @Id
    private UUID token;

    @Column(name = "initiator_account_id", nullable = false)
    private UUID initiatorAccountId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
