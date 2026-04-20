package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskSlack(
    UUID taskId,
    LocalDateTime earliestStart,
    LocalDateTime earliestEnd,
    LocalDateTime latestStart,
    LocalDateTime latestEnd,
    long slackMinutes
) {
}
