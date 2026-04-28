package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.CognitiveLoad;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
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
    List<UUID> dependsOnTaskIds
) {
}
