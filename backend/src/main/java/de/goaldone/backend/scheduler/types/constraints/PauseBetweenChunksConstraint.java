package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public class PauseBetweenChunksConstraint extends SoftConstraint {

    // TODO: Scorewert feinjustieren
    private static final int CONSTRAINT_VALUE = 3;

    private static final int REQUIRED_PAUSE_MINUTES = 15;

    public PauseBetweenChunksConstraint() {
        super(CONSTRAINT_VALUE);
    }

    @Override
    public void updateConstraint(SolverState schedule) {
        this.isActive = true;

        List<ScheduledChunk> sorted = schedule.scheduledChunks().stream()
                .sorted(Comparator
                        .comparing(ScheduledChunk::date)
                        .thenComparing(ScheduledChunk::startTime))
                .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            ScheduledChunk current = sorted.get(i);
            ScheduledChunk next = sorted.get(i + 1);

            LocalDateTime currentEnd = LocalDateTime.of(current.date(), current.endTime());
            LocalDateTime nextStart = LocalDateTime.of(next.date(), next.startTime());

            long gapMinutes = ChronoUnit.MINUTES.between(currentEnd, nextStart);
            if (gapMinutes < REQUIRED_PAUSE_MINUTES) {
                this.isActive = false;
                return;
            }
        }
    }

    @Override
    public ScheduleWarning getWarning() {
        return new ScheduleWarning(
            ScheduleWarning.TypeEnum.PAUSE_MISSING,
            "One or more consecutive chunks are missing the required 15-minute pause between them."
        );
    }
}

