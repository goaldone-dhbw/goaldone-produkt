package de.goaldone.backend.scheduler;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTest {

    private Chunker chunker;
    private List<WorkingTimeEntity> workingTimes;

    @BeforeEach
    void setUp() {
        chunker = new Chunker();

        WorkingTimeEntity wt = new WorkingTimeEntity();
        wt.setId(UUID.randomUUID());
        wt.setStartTime(LocalTime.of(9, 0));
        wt.setEndTime(LocalTime.of(17, 0)); // 480 min
        wt.setDays(new HashSet<>());
        workingTimes = List.of(wt);
    }

    private TaskResponse task(int durationMinutes, CognitiveLoad load, Integer customChunkSize) {
        TaskResponse t = new TaskResponse();
        t.setId(UUID.randomUUID());
        t.setDuration(durationMinutes);
        t.setCognitiveLoad(load);
        t.setCustomChunkSize(customChunkSize);
        t.setDependencyIds(List.of());
        return t;
    }

    @Test
    void positive_high_300min_results_in_3_chunks() {
        // 300 / 120 -> 120 + 120 + 60
        TaskResponse t = task(300, CognitiveLoad.HIGH, null);
        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).durationMinutes()).isEqualTo(120);
        assertThat(chunks.get(1).durationMinutes()).isEqualTo(120);
        assertThat(chunks.get(2).durationMinutes()).isEqualTo(60);
        assertThat(chunks.get(0).totalChunks()).isEqualTo(3);
    }

    @Test
    void edge_case_high_250min_last_remainder_below_threshold_gets_merged() {
        // 250 / 120 -> 120 + 120 + 10 -> 10 < 15 -> gets appended -> 120 + 130
        TaskResponse t = task(250, CognitiveLoad.HIGH, null);
        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).durationMinutes()).isEqualTo(120);
        assertThat(chunks.get(1).durationMinutes()).isEqualTo(130);
    }

    @Test
    void negative_duration_less_than_customChunkSize_no_splitting() {
        // 10 min task, customChunkSize 30 -> stays as 1 chunk with 10 min
        TaskResponse t = task(10, CognitiveLoad.HIGH, 30);
        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().durationMinutes()).isEqualTo(10);
    }

    @Test
    void low_task_exceeding_working_day_gets_split() {
        // LOW -> chunkSize = 480 min (working day)
        // task has 600 min -> 480 + 120
        TaskResponse t = task(600, CognitiveLoad.LOW, null);
        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).durationMinutes()).isEqualTo(480);
        assertThat(chunks.get(1).durationMinutes()).isEqualTo(120);
    }

    @Test
    void chunkIndex_and_totalChunks_are_set_correctly() {
        TaskResponse t = task(300, CognitiveLoad.HIGH, null);
        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
            assertThat(chunks.get(i).totalChunks()).isEqualTo(3);
        }
    }

    @Test
    void dependsOnTaskIds_are_correctly_transferred() {
        UUID depId = UUID.randomUUID();
        TaskResponse t = task(60, CognitiveLoad.MODERATE, null);
        t.setDependencyIds(List.of(depId));

        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        assertThat(chunks.getFirst().dependsOnTaskIds()).containsExactly(depId);
    }

    @Test
    void edge_case_high_240min_exact_multiple_of_chunk_size_no_zero_chunk() {
        // 240 / 120 -> exactly 2 chunks of 120, no 0-min remainder
        TaskResponse t = task(240, CognitiveLoad.HIGH, null);
        List<TaskChunk> chunks = chunker.chunkTasks(List.of(t), workingTimes);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).durationMinutes()).isEqualTo(120);
        assertThat(chunks.get(1).durationMinutes()).isEqualTo(120);
    }
}
