package de.goaldone.backend.scheduler.types.model;
import de.goaldone.backend.model.ScheduleEntry;
import java.time.format.DateTimeFormatter;

public class ScheduleMapper {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private ScheduleMapper() {
    }

    public static Schedule toSchedule(SolverState state) {
        Schedule schedule = new Schedule();

        for (ScheduledChunk scheduledChunk : state.scheduledChunks()) {
            ScheduleEntry entry = toScheduleEntry(scheduledChunk);
            schedule.addScheduleEntry(entry);
        }

        return schedule;
    }

    private static ScheduleEntry toScheduleEntry(ScheduledChunk scheduledChunk) {
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