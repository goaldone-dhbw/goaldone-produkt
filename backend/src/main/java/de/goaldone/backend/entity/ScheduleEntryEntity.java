package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a single scheduled time block within a {@link SchedulePlanEntity}.
 * A schedule entry corresponds to one task chunk or appointment slot assigned to a specific
 * time interval. The {@code isCompleted} flag is updated via the completion tracking endpoint.
 * This entity is mapped to the "schedule_entries" database table.
 */
@Entity
@Table(name = "schedule_entries", indexes = {
    @Index(name = "idx_schedule_entries_account_id", columnList = "account_id"),
    @Index(name = "idx_schedule_entries_original_item_id", columnList = "original_item_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEntryEntity {

    /** Unique identifier for this schedule entry. Returned as entryId in API responses. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The parent schedule plan this entry belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SchedulePlanEntity plan;

    /**
     * Denormalized account ID for efficient lookup without joining to schedule_plans.
     * Must match plan.accountId.
     */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Start date and time of this scheduled block (LocalDateTime, stored as TIMESTAMP). */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** End date and time of this scheduled block (LocalDateTime, stored as TIMESTAMP). */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /**
     * The calendar date of this entry's occurrence. Used for sorting and date-range filtering.
     * Matches the date portion of startAt.
     */
    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    /** Entry type: TASK (scheduled task chunk) or APPOINTMENT (fixed appointment slot). */
    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    /** True if this entry represents a break rather than a productive block. */
    @Column(name = "is_break", nullable = false)
    private Boolean isBreak;

    /** Whether this entry has been marked as completed via the completion tracking endpoint. */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    /**
     * The ID of the original Task or Appointment that this entry was generated from.
     * Used for TASK-scope completion (mark all entries with same originalItemId as done).
     */
    @Column(name = "original_item_id")
    private UUID originalItemId;

    /** Human-readable title of the original Task or Appointment. */
    @Column(name = "original_item_title", length = 500)
    private String originalItemTitle;

    /**
     * Zero-based index of this chunk within the parent task's total chunks.
     * Null for APPOINTMENT type entries.
     */
    @Column(name = "chunk_index")
    private Integer chunkIndex;

    /**
     * Total number of chunks the parent task was split into.
     * Null for APPOINTMENT type entries.
     */
    @Column(name = "total_chunks")
    private Integer totalChunks;
}
