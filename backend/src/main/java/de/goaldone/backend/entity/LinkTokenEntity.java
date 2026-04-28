package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a short-lived token used for linking user accounts across different organizations.
 * This entity is mapped to the "link_tokens" database table.
 */
@Entity
@Table(name = "link_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkTokenEntity {
    /** The unique token (UUID) generated for the linking process. */
    @Id
    private UUID token;

    /** The ID of the account that initiated the link request. */
    @Column(name = "initiator_account_id", nullable = false)
    private UUID initiatorAccountId;

    /** The timestamp when this link token becomes invalid. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** The timestamp when this link token was generated. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
