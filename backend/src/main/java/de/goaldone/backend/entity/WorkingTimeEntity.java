package de.goaldone.backend.entity;

import de.goaldone.backend.model.DayOfWeek;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing the defined working hours for a user account on specific days of the week.
 * This entity is mapped to the "working_times" database table.
 */
@Entity
@Table(name = "working_times")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkingTimeEntity {

    /** The unique identifier for the working time definition. */
    @Id
    private UUID id;

    /** The {@link MembershipEntity} this working time belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private MembershipEntity membership;

    /** The ID of the organization this working time is associated with. */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The set of days of the week for which this time range applies. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "working_time_days", joinColumns = @JoinColumn(name = "working_time_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> days = new HashSet<>();

    /** The start time of the working period on the specified days. */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** The end time of the working period on the specified days. */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** The timestamp when this working time record was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
