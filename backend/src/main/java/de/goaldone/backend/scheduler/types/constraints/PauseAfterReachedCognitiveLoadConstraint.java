package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PauseAfterReachedCognitiveLoadConstraint extends SoftConstraint {

    // TODO: Scorewert feinjustieren
    private static final int CONSTRAINT_VALUE = 3;

    private static final int HIGH_LOAD_THRESHOLD_MINUTES = 120;
    private static final int MODERATE_LOAD_THRESHOLD_MINUTES = 240;

    public PauseAfterReachedCognitiveLoadConstraint() {
        super(CONSTRAINT_VALUE);
    }

    @Override
    public void updateConstraint(SolverState schedule) {
        this.isActive = true;

        // Group and sort chunks per day
        Map<LocalDate, List<ScheduledChunk>> chunksByDay = schedule.scheduledChunks().stream()
                .collect(Collectors.groupingBy(ScheduledChunk::date));

        for (List<ScheduledChunk> dayChunks : chunksByDay.values()) {
            List<ScheduledChunk> sorted = dayChunks.stream()
                    .sorted(Comparator.comparing(ScheduledChunk::startTime))
                    .toList();

            int accumulatedHighMinutes = 0;
            int accumulatedModerateMinutes = 0;
            boolean highThresholdReached = false;
            boolean moderateThresholdReached = false;

            for (int i = 0; i < sorted.size(); i++) {
                ScheduledChunk current = sorted.get(i);
                CognitiveLoad load = current.chunk().cognitiveLoad();

                // After threshold was reached in previous iteration, check for a gap (pause)
                if (highThresholdReached || moderateThresholdReached) {
                    if (i > 0) {
                        ScheduledChunk previous = sorted.get(i - 1);
                        LocalDateTime previousEnd = LocalDateTime.of(previous.date(), previous.endTime());
                        LocalDateTime currentStart = LocalDateTime.of(current.date(), current.startTime());
                        long gapMinutes = ChronoUnit.MINUTES.between(previousEnd, currentStart);
                        if (gapMinutes <= 0) {
                            // No pause after threshold was reached
                            this.isActive = false;
                            return;
                        }
                    }
                    // Pause was found – reset accumulators
                    accumulatedHighMinutes = 0;
                    accumulatedModerateMinutes = 0;
                    highThresholdReached = false;
                    moderateThresholdReached = false;
                }

                // Accumulate cognitive load minutes
                if (load == CognitiveLoad.HIGH) {
                    accumulatedHighMinutes += current.chunk().durationMinutes();
                    accumulatedModerateMinutes += current.chunk().durationMinutes();
                } else if (load == CognitiveLoad.MODERATE) {
                    accumulatedModerateMinutes += current.chunk().durationMinutes();
                }

                if (accumulatedHighMinutes >= HIGH_LOAD_THRESHOLD_MINUTES) {
                    highThresholdReached = true;
                }
                if (accumulatedModerateMinutes >= MODERATE_LOAD_THRESHOLD_MINUTES) {
                    moderateThresholdReached = true;
                }
            }
        }
    }

    @Override
    public ScheduleWarning getWarning() {
        return new ScheduleWarning(
                ScheduleWarning.TypeEnum.OVERLOADED_DAY,
                "A pause should be scheduled after the cognitive load threshold is reached."
        );
    }
}
