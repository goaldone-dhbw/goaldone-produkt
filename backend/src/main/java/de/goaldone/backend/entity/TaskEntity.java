package de.goaldone.backend.entity;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_tasks_account_id", columnList = "account_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private Integer estimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CognitiveLoad cognitiveLoad;

    private Integer customChunkSizeMinutes;

    private Instant deadline;

    private Instant notBefore;

    private UUID dependsOnTaskId;

    @Column(nullable = false)
    private Instant createdAt;
}
