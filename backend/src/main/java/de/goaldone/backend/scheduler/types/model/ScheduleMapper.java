package de.goaldone.backend.scheduler.types.model;
import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.model.ScheduleResponse;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class ScheduleMapper {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * @param accountId The account the schedule was generated for
     * @param context The information base of the schedule
     * @param schedule The generated schedule
     * @return A schedule result for the backend
     */
    public ScheduleResponse mapToScheduleResult(UUID accountId, SchedulingContext context, SchedulingResult schedule) {
        ScheduleResponse response = new ScheduleResponse();

        response.generatedAt(OffsetDateTime.now());
        response.accountId(accountId);
        response.from(context.fromDate());
        response.score(schedule.score());
        response.setWarnings(schedule.scheduleWarnings());

        response.to(
                getToDate(schedule.schedule(), context.fromDate())
        );

        response.totalWorkMinutes(
                getWorkMinutes(schedule.schedule())
        );

        response.entries(
                getSchedule(schedule.schedule())
        );

        response.unscheduledTasks(schedule.schedule().unscheduledChunks());

        return response;
    }

    /**
     * @param schedule The generated schedule
     * @return The total amount of time planed as working time
     */
    private Integer getWorkMinutes(SolverState schedule) {
        return schedule
                .scheduledChunks()
                .stream()
                .mapToInt(c -> c.slot().durationMinutes())
                .sum();
    }

    /**
     * @param schedule The generated schedule
     * @param fromDate The date from which on the plan is generated
     * @return The date of the latest scheduled task
     */
    private LocalDate getToDate(SolverState schedule, LocalDate fromDate) {
        Optional<LocalDate> latestDate = schedule
                .scheduledChunks()
                .stream()
                .map(c -> c.slot().date())
                .max(LocalDate::compareTo);
        return latestDate.orElse(fromDate);
    }

    /**
     * @param state The generated schedule
     * @return ScheduledChunks mapped as List of ScheduleEntry
     */
    private List<ScheduleEntry> getSchedule(SolverState state) {
        List<ScheduleEntry> schedule = new ArrayList<>();

        for (ScheduledChunk scheduledChunk : state.scheduledChunks()) {
            schedule.add(toScheduleEntry(scheduledChunk));
        }

        return schedule;
    }

    /**
     * @param scheduledChunk A single scheduled chunk
     * @return A ScheduledChunk mapped as ScheduleEntry
     */
    private ScheduleEntry toScheduleEntry(ScheduledChunk scheduledChunk) {
        return new ScheduleEntry()
                .taskTitle(scheduledChunk.chunk().taskTitle())
                .source(ScheduleEntry.SourceEnum.ONE_TIME)
                .occurrenceDate(scheduledChunk.date())
                .startTime(scheduledChunk.startTime().format(TIME_FORMATTER))
                .endTime(scheduledChunk.endTime().format(TIME_FORMATTER))
                .type(ScheduleEntry.TypeEnum.TASK)
                .isBreak(false)
                .isCompleted(false)
                .isPinned(scheduledChunk.chunk().isPinned())
                .originalItemId(scheduledChunk.chunk().taskId())
                .chunkIndex(scheduledChunk.chunk().chunkIndex())
                .totalChunks(scheduledChunk.chunk().totalChunks());
    }
}