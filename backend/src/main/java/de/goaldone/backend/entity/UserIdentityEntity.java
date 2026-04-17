package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentityEntity {
    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
