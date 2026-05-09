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

        // TODO: scheduledTasks, unscheduledTasks

        return response;
    }

    private Integer getWorkMinutes(SolverState schedule) {
        return schedule
                .scheduledChunks()
                .stream()
                .mapToInt(c -> c.slot().durationMinutes())
                .sum();
    }

    private LocalDate getToDate(SolverState schedule, LocalDate fromDate) {
        Optional<LocalDate> latestDate = schedule
                .scheduledChunks()
                .stream()
                .map(c -> c.slot().date())
                .max(LocalDate::compareTo);
        return latestDate.orElse(fromDate);
    }

    private List<ScheduleEntry> getSchedule(SolverState state) {
        List<ScheduleEntry> schedule = new ArrayList<>();

        for (ScheduledChunk scheduledChunk : state.scheduledChunks()) {
            schedule.add(toScheduleEntry(scheduledChunk));
        }

        return schedule;
    }

    private ScheduleEntry toScheduleEntry(ScheduledChunk scheduledChunk) {
        return new ScheduleEntry()
                .source(ScheduleEntry.SourceEnum.ONE_TIME)
                .occurenceDate(scheduledChunk.date())
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