package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.HardConstraint;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DependencyConstraint extends HardConstraint {

    @Override
    public void updateConstraint(SolverState schedule) {
        this.isViolated = false;

        // Build a map: taskId -> latest end time across all its scheduled chunks
        Map<UUID, LocalDateTime> latestEndByTask = schedule.scheduledChunks().stream()
                .collect(Collectors.toMap(
                        sc -> sc.chunk().taskId(),
                        sc -> LocalDateTime.of(sc.date(), sc.endTime()),
                        (a, b) -> a.isAfter(b) ? a : b
                ));

        for (ScheduledChunk scheduledChunk : schedule.scheduledChunks()) {
            List<UUID> dependencies = scheduledChunk.chunk().dependsOnTaskIds();
            if (dependencies == null || dependencies.isEmpty()) {
                continue;
            }

            LocalDateTime chunkStart = LocalDateTime.of(scheduledChunk.date(), scheduledChunk.startTime());

            for (UUID dependencyTaskId : dependencies) {
                LocalDateTime dependencyEnd = latestEndByTask.get(dependencyTaskId);
                // Dependency not scheduled at all, or ends after this chunk starts -> violation
                if (dependencyEnd == null || dependencyEnd.isAfter(chunkStart)) {
                    this.isViolated = true;
                    return;
                }
            }
        }
    }

    @Override
    public ScheduleWarning getWarning() {
        return new ScheduleWarning(
            ScheduleWarning.TypeEnum.DEPENDENCY_VIOLATED,
            "One or more tasks are scheduled before their dependencies have been completed."
        );
    }
}

