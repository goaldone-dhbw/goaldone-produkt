package de.goaldone.backend.scheduler.types.model;

import java.util.UUID;

public record UnscheduledTaskResult(
    UUID taskId,
    UnscheduledReason reason,
    int shortfallMinutes
) {
}
