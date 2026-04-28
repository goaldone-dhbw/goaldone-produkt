package de.goaldone.backend.entity;

import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.CognitiveLoad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity representing a task in the GoalDone system.
 * This entity stores task details such as title, description, duration, status, and dependencies.
 * It maps to the "tasks" table and uses UUID for identification.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "dependencies")
public class TaskEntity {

    /** The unique identifier for the task. */
    @Id
    private UUID id;

    /** The ID of the account that owns this task. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** The title of the task. */
    @Column(name = "title", nullable = false)
    private String title;

    /** A detailed description of the task. */
    @Column(name = "description", length = 65535)
    private String description;

    /** The estimated duration of the task in minutes. */
    @Column(name = "duration", nullable = false)
    private Integer duration;

    /** The deadline for the task. */
    @Column(name = "deadline")
    private Instant deadline;

    /** The current status of the task (e.g., TODO, IN_PROGRESS, DONE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskStatus status;

    /** The mental effort required for the task (e.g., LOW, MEDIUM, HIGH). */
    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_load", nullable = false, length = 32)
    private CognitiveLoad cognitiveLoad;

    /** The earliest timestamp when this task should be scheduled. */
    @Column(name = "dont_schedule_before")
    private Instant dontScheduleBefore;

    /** Optional custom chunk size for splitting the task during scheduling. */
    @Column(name = "custom_chunk_size")
    private Integer customChunkSize;

    /** Other tasks that must be completed before this task can be started. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_dependencies",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "depends_on_task_id")
    )
    private Set<TaskEntity> dependencies = new LinkedHashSet<>();
}
