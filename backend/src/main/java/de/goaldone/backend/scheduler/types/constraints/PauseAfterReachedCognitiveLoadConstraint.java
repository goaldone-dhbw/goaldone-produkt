package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.time.LocalDate;
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

            for (ScheduledChunk current : sorted) {
                CognitiveLoad load = current.chunk().cognitiveLoad();

                // After threshold was reached in previous iteration, check for a pause (Break-Chunk)
                if (highThresholdReached || moderateThresholdReached) {
                    // TODO: Sobald TaskChunk.isBreak() verfügbar ist, hier prüfen ob current ein
                    //       Break-Chunk ist. Ein Break-Chunk signalisiert die eingeplante Pause.
                    //       Falls current KEIN Break-Chunk ist, fehlt die Pause nach der Schwelle:
                    //       if (!current.chunk().isBreak()) { this.isActive = false; return; }
                    //       Break-Chunks sollen außerdem keine kognitive Last akkumulieren (→ continue).
                    //  Stub: Pause gilt als vorhanden – Akkumulatoren zurücksetzen
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
