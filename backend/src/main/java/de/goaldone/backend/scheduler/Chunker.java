package de.goaldone.backend.scheduler;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.scheduler.types.model.TaskChunk;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class Chunker {

    private static final int MIN_CHUNK_SIZE_MINUTES = 15;
    private static final int DEFAULT_WORKING_DAY_MINUTES = 480;
    private static final int CHUNK_SIZE_HIGH = 120;
    private static final int CHUNK_SIZE_MODERATE = 240;

    /**
     * Splits a list of tasks into chunks based on cognitive load or custom chunk size,
     * respecting the maximum working day length derived from working times.
     *
     * @param tasks        List of tasks to chunk
     * @param workingTimes List of working time definitions for the account
     * @return Map of UUID -> [TaskChunk] where the key is the original task ID and the value is the list of chunks for that task
     */
    public Map<UUID, List<TaskChunk>> chunkTasks(List<TaskResponse> tasks, List<WorkingTimeEntity> workingTimes) {
        int maxWorkingDayMinutes = computeMaxWorkingDayMinutes(workingTimes);
        Map<UUID, List<TaskChunk>> result = new HashMap<>();

        for (TaskResponse task : tasks) {
            List<TaskChunk> chunks = chunkSingleTask(task, maxWorkingDayMinutes);
            result.put(task.getId(), chunks);
        }

        return result;
    }


    private List<TaskChunk> chunkSingleTask(TaskResponse task, int maxWorkingDayMinutes) {

        int chunkSize = resolveChunkSize(task, maxWorkingDayMinutes);
        List<Integer> chunkDurations = splitIntoChunks(task.getDuration(), chunkSize);
        int totalChunks = chunkDurations.size();

        LocalDateTime notBefore = task.getDontScheduleBefore() != null
                ? task.getDontScheduleBefore().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
                : null;
        LocalDateTime deadline = task.getDeadline() != null
                ? task.getDeadline().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
                : null;

        List<UUID> dependsOnTaskIds = task.getDependencyIds() != null
                ? task.getDependencyIds()
                : List.of();

        List<TaskChunk> chunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            chunks.add(TaskChunk.builder()
                    .taskTitle(task.getTitle())
                    .chunkId(UUID.randomUUID())
                    .taskId(task.getId())
                    .chunkIndex(i)
                    .totalChunks(totalChunks)
                    .durationMinutes(chunkDurations.get(i))
                    .cognitiveLoad(task.getCognitiveLoad())
                    .notBefore(notBefore)
                    .deadline(deadline)
                    .isBreak(false)
                    .dependsOnTaskIds(dependsOnTaskIds)
                    .build());
        }
        return chunks;
    }



    /**
     * Computes the maximum working day length in minutes across all working time definitions.
     * Falls back to {@value DEFAULT_WORKING_DAY_MINUTES} minutes if no working times are defined.
     */
    private int computeMaxWorkingDayMinutes(List<WorkingTimeEntity> workingTimes) {
        if (workingTimes == null || workingTimes.isEmpty()) {
            return DEFAULT_WORKING_DAY_MINUTES;
        }
        return workingTimes.stream()
                .mapToInt(wt -> (int) Duration.between(wt.getStartTime(), wt.getEndTime()).toMinutes())
                .max()
                .orElse(DEFAULT_WORKING_DAY_MINUTES);
    }

    /**
     * Determines the chunk size for a task.
     * Priority: customChunkSize > cognitiveLoad-based size.
     */
    private int resolveChunkSize(TaskResponse task, int maxWorkingDayMinutes) {
        if (task.getCustomChunkSize() != null && task.getCustomChunkSize() > 0) {
            return task.getCustomChunkSize();
        }
        if (task.getCognitiveLoad() == null) {
            return maxWorkingDayMinutes;
        }
        return switch (task.getCognitiveLoad()) {
            case HIGH     -> CHUNK_SIZE_HIGH;
            case MODERATE -> CHUNK_SIZE_MODERATE;
            case LOW      -> maxWorkingDayMinutes;
        };
    }

    /**
     * Splits a total duration into a list of chunk durations.
     * If the last remaining piece is less than MIN_CHUNK_SIZE_MINUTES,
     * it gets appended to the previous chunk.
     *
     * @param totalDuration total task duration in minutes
     * @param chunkSize     maximum chunk size in minutes
     * @return ordered list of chunk durations
     */
    private List<Integer> splitIntoChunks(int totalDuration, int chunkSize) {
        if (totalDuration <= chunkSize) {
            return List.of(totalDuration);
        }

        List<Integer> chunks = new ArrayList<>();
        int remaining = totalDuration;

        while (remaining > chunkSize) {
            chunks.add(chunkSize);
            remaining -= chunkSize;
        }

        if (remaining == 0) {
            return chunks;
        }

        if (remaining < MIN_CHUNK_SIZE_MINUTES) {
            chunks.set(chunks.size() - 1, chunks.getLast() + remaining);
        } else {
            chunks.add(remaining);
        }

        return chunks;
    }
}
