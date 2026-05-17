package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a persisted schedule plan for a single user account.
 * One plan is stored per account; generating a new schedule replaces the existing one.
 * Metadata columns mirror the ScheduleResponse DTO to allow full fidelity on retrieval.
 * This entity is mapped to the "schedule_plans" database table.
 */
@Entity
@Table(name = "schedule_plans", uniqueConstraints = {
    @UniqueConstraint(name = "uq_schedule_plans_account_id", columnNames = "account_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePlanEntity {

    /** Unique identifier for this schedule plan. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The ID of the user account this plan belongs to (one plan per account). */
    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    /** Timestamp when this schedule was generated. */
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** Start date of the planned period (inclusive). */
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    /** End date of the planned period (inclusive, dynamically determined by algorithm). */
    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /** Total minutes of task work scheduled in this plan. */
    @Column(name = "total_work_minutes", nullable = false)
    private Integer totalWorkMinutes;

    /** Optimisation score assigned by the scheduling algorithm. */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** Jackson-serialized JSON array of ScheduleWarning objects. TEXT for H2/PostgreSQL compatibility. */
    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    /** Jackson-serialized JSON array of UnscheduledTask objects. TEXT for H2/PostgreSQL compatibility. */
    @Column(name = "unscheduled_tasks_json", columnDefinition = "TEXT")
    private String unscheduledTasksJson;

    /** All schedule entries belonging to this plan. Cascade ensures entries are deleted with the plan. */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ScheduleEntryEntity> entries = new ArrayList<>();
}
