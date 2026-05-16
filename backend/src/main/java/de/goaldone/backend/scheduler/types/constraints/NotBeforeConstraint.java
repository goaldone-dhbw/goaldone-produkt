package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.HardConstraint;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.time.LocalDateTime;

public class NotBeforeConstraint extends HardConstraint {

    @Override
    public void updateConstraint(SolverState schedule) {
        this.isViolated = false;

        for (ScheduledChunk scheduledChunk : schedule.scheduledChunks()) {
            LocalDateTime notBefore = scheduledChunk.chunk().notBefore();
            if (notBefore == null) {
                continue;
            }
            LocalDateTime scheduledStart = LocalDateTime.of(scheduledChunk.date(), scheduledChunk.startTime());
            if (scheduledStart.isBefore(notBefore)) {
                this.isViolated = true;
                return;
            }
        }
    }

    @Override
    public ScheduleWarning getWarning() {
        return new ScheduleWarning(
            ScheduleWarning.TypeEnum.NOT_BEFORE_VIOLATED,
            "One or more tasks are scheduled before their 'not before' date."
        );
    }
}

