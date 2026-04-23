package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.CognitiveLoad;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskChunk(
    UUID chunkId,
    UUID taskId,
    int chunkIndex,
    int totalChunks,
    int durationMinutes,
    int topologicalLevel,
    long slackMinutes,
    CognitiveLoad cognitiveLoad,
    LocalDateTime notBefore,
    LocalDateTime deadline,
    boolean isPinned,
    UUID dependsOnTaskId
) {
}
