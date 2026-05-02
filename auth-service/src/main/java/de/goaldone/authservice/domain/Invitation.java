package de.goaldone.authservice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Linking status tracking fields
    @Column(name = "linking_attempted", nullable = false)
    private boolean linkingAttempted = false;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(name = "linking_timestamp")
    private LocalDateTime linkingTimestamp;

    @Column(name = "acceptance_reason")
    private String acceptanceReason;

    // Convenience methods for status updates
    public void markAsAcceptedWithNewAccount(User newUser) {
        this.acceptanceReason = "NEW_ACCOUNT";
        this.linkedUserId = newUser.getId();
        this.linkingTimestamp = LocalDateTime.now();
        this.linkingAttempted = false;
    }

    public void markAsAcceptedWithLinking(User linkedUser) {
        this.acceptanceReason = "ACCOUNT_LINKING";
        this.linkedUserId = linkedUser.getId();
        this.linkingTimestamp = LocalDateTime.now();
        this.linkingAttempted = true;
    }

    public void markAsDeclined(String reason) {
        this.acceptanceReason = "DECLINED";
        this.linkingTimestamp = LocalDateTime.now();
    }
}
