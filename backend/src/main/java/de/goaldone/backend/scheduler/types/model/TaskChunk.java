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
    String taskTitle,
    int chunkIndex,
    int totalChunks,
    int durationMinutes,
    CognitiveLoad cognitiveLoad,
    LocalDateTime notBefore,
    LocalDateTime deadline,
    boolean isBreak,
    List<UUID> dependsOnTaskIds
) {
}
