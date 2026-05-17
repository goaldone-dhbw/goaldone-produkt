package de.goaldone.backend.scheduler;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.TimeSlot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Gemeinsame Test-Hilfsmethoden für Scheduler-Tests.
 */
public final class SchedulerTestHelper {

    private SchedulerTestHelper() {
    }

    // -------------------------------------------------------------------------
    // TaskResponse-Builder
    // -------------------------------------------------------------------------

    public static TaskResponse task(int taskDuration) {
        return task(taskDuration, List.of());
    }

    public static TaskResponse task(int taskDuration, List<UUID> dependencyIds) {
        return task(taskDuration, dependencyIds, null, null);
    }

    public static TaskResponse task(int taskDuration, List<UUID> dependencyIds,
                                    LocalDateTime dontScheduleBefore, LocalDate deadline) {
        return task(taskDuration, dependencyIds, dontScheduleBefore, deadline, CognitiveLoad.LOW);
    }

    public static TaskResponse task(int taskDuration, List<UUID> dependencyIds,
                                    LocalDateTime dontScheduleBefore, LocalDate deadline,
                                    CognitiveLoad cognitiveLoad) {
        OffsetDateTime convertedDeadline = deadline == null ? null
                : OffsetDateTime.of(deadline, LocalTime.of(0, 0), ZoneOffset.ofHours(0));

        OffsetDateTime convertedNotBefore = dontScheduleBefore == null ? null
                : OffsetDateTime.of(dontScheduleBefore, ZoneOffset.ofHours(0));

        return new TaskResponse()
                .id(UUID.randomUUID())
                .title("Task")
                .description("Description")
                .duration(taskDuration)
                .deadline(convertedDeadline)
                .status(TaskStatus.OPEN)
                .cognitiveLoad(cognitiveLoad)
                .customChunkSize(null)
                .dontScheduleBefore(convertedNotBefore)
                .dependencyIds(dependencyIds);
    }

    // -------------------------------------------------------------------------
    // TimeSlot-Builder
    // -------------------------------------------------------------------------

    public static TimeSlot slot(LocalDate date, int startHour, int endHour) {
        return slot(date, startHour, 0, endHour, 0);
    }

    public static TimeSlot slot(LocalDate date, int startHour, int startMin, int endHour, int endMin) {
        return new TimeSlot(
                date,
                LocalTime.of(startHour, startMin),
                LocalTime.of(endHour, endMin)
        );
    }

    // -------------------------------------------------------------------------
    // WorkingTimeEntity-Builder
    // -------------------------------------------------------------------------

    public static WorkingTimeEntity working(List<DayOfWeek> days, int endHour) {
        WorkingTimeEntity entity = new WorkingTimeEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserAccount(null);
        entity.setOrganizationId(null);
        entity.setDays(new HashSet<>(days));
        entity.setStartTime(LocalTime.of(8, 0));
        entity.setEndTime(LocalTime.of(endHour, 0));
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    // -------------------------------------------------------------------------
    // SchedulingContext-Builder
    // -------------------------------------------------------------------------

    /**
     * Liefert einen leeren {@link SchedulingContext} für Tests, die keinen
     * tatsächlichen Kontext benötigen (z. B. Move-Tests).
     */
    public static SchedulingContext emptyContext() {
        return new SchedulingContext(
                LocalDateTime.of(2026, 1, 1, 0, 0),
                List.of(),
                List.of(),
                null,
                List.of()
        );
    }
}

