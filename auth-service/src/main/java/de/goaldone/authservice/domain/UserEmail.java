package de.goaldone.authservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "user_emails")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String email;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;
}
