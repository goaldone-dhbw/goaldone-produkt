package de.goaldone.backend.entity;

import de.goaldone.backend.model.AppointmentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entity representing an appointment or a scheduled block of time for an account.
 * Appointments can be one-time events or recurring based on an RFC 5545 RRULE string.
 * This entity is mapped to the "appointments" database table.
 */
@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appointments_account_id", columnList = "account_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEntity {
    /** Unique identifier for the appointment. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The ID of the account (user identity associated with an organization) that owns this appointment. */
    @Column(nullable = false)
    private UUID accountId;

    /** The title or subject of the appointment. */
    @Column(nullable = false)
    private String title;

    /** Indicates whether this appointment represents a break in the schedule. */
    @Column(nullable = false)
    private Boolean isBreak;

    /** The classification of the appointment (e.g., PLANNED, ACTUAL). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentType appointmentType;

    /** The specific date of the appointment (for non-recurring events). */
    private LocalDate date;

    /** The start time of the appointment on its scheduled day. */
    @Column(nullable = false)
    private LocalTime startTime;

    /** The end time of the appointment on its scheduled day. */
    @Column(nullable = false)
    private LocalTime endTime;

    /** An iCalendar RFC 5545 recurrence rule defining how the appointment repeats. */
    @Column(columnDefinition = "TEXT")
    private String rrule;

    /** Timestamp indicating when this appointment record was created. */
    @Column(nullable = false)
    private Instant createdAt;
}
