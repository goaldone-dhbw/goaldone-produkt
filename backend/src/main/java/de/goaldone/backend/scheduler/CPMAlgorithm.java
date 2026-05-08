package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.scheduler.types.model.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class CPMAlgorithm {

    private final TaskSorter taskSorter;
    private final Chunker chunker;

    private ArrayList<TimeSlot> globalAvailableTimeSlots;


    public CPMAlgorithm() {
        this.chunker = new Chunker();
        this.taskSorter = new TaskSorter();
    }


    /**
     * Creates initial schedule
     * @param context Scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return A first, heuristic generated schedule
     */
    public SolverState generateInitialSchedule(SchedulingContext context) {

        this.globalAvailableTimeSlots = new ArrayList<>(context.availableSlots());


        List<TaskResponse> tasks = context.tasks();
        // Assumption: There is more available time than needed to schedule all tasks


        // Sort chunks based on dependencies, slackTime and kognitive load
        List<TaskSlack> taskSlacks = calculateSlack(context);
        List<UUID> sortedTasks = taskSorter.sort(tasks, taskSlacks);

        Map<UUID, List<TaskChunk>> chunkMap = chunker.chunkTasks(tasks, context.workingTimes());

        // Container for result
        List<ScheduledChunk> resultChunks = new ArrayList<>();

        for (UUID taskId : sortedTasks) {
            List<TaskChunk> chunks = chunkMap.get(taskId);

            List<ScheduledChunk> tempResults = new ArrayList<>();
            boolean splitChunks = false;
            for (TaskChunk chunk : chunks) {
                List<ScheduledChunk> scheduledChunk = findTimeSlotForChunk(chunk);
                splitChunks = scheduledChunk.size() > 1; // True if the chunk was further split in "findTimeSlotForChunk"
                tempResults.addAll(scheduledChunk);
            }

            if (splitChunks) {
                resultChunks.addAll(updateChunks(tempResults));
            } else {
                resultChunks.addAll(tempResults);
            }
        }
        return new SolverState (resultChunks, this.globalAvailableTimeSlots);
    }

    /**
     * @param chunk The chunk to fit in a slot
     * @param slot The time slot in question
     * @return True, if the chunk fits within the times slot and the date is suitable
     */
    private boolean isSuitableSlot(TaskChunk chunk, TimeSlot slot) {
        boolean canFit = slot.canFit(chunk.durationMinutes());
        boolean isSuitableDate = isSuitableDate(chunk, slot);
        return canFit && isSuitableDate;
    }

    /**
     * @param chunk The chunk to fit in a slot
     * @param slot The time slot in question
     * @return True, if the time slot is before the deadline and after dontScheduleBefore.
     */
    private boolean isSuitableDate(TaskChunk chunk, TimeSlot slot) {
        LocalDateTime slotDateTime = LocalDateTime.of(slot.date(), slot.startTime());

        boolean isNotBefore = false;
        if (chunk.notBefore() != null) {
            isNotBefore = !slotDateTime.isBefore(chunk.notBefore());
        } else {
            isNotBefore = true;
        }

        boolean isBeforeDeadline = false;
        if (chunk.deadline() != null) {
            isBeforeDeadline = !slotDateTime.isAfter(chunk.deadline());
        } else {
            isBeforeDeadline = true;
        }
        return isNotBefore && isBeforeDeadline;

    }


    /**
     * Finds suitable time slots for the given task chunk. If no single slot is large
     * enough, the chunk is split across multiple available slots.
     * @param chunk the task chunk to schedule
     * @return a list of scheduled chunks with assigned time slots
     * @throws IllegalStateException if there is not enough free time available
     */
    private List<ScheduledChunk> findTimeSlotForChunk(TaskChunk chunk) {

        // Find the earliest available slot that can accommodate this chunk
        Optional<TimeSlot> suitableSlot = this.globalAvailableTimeSlots.stream()
                .filter(slot -> isSuitableSlot(chunk, slot))
                .findFirst();

        if (suitableSlot.isPresent()) {
            TimeSlot slot = suitableSlot.get();

            // Create the scheduled slot only for the needed duration
            TimeSlot scheduledSlot = new TimeSlot(
                    slot.date(),
                    slot.startTime(),
                    slot.startTime().plusMinutes(chunk.durationMinutes())
            );

            removeTimeSlot(slot, chunk);
            return List.of(new ScheduledChunk(chunk, scheduledSlot));
        }

        // At this point, no slot was large enough to accommodate this chunk
        // -> further splitting

        List<TimeSlot> nextAvailableSlots = this.globalAvailableTimeSlots.stream()
                .filter(slot -> isSuitableDate(chunk, slot))
                .collect(Collectors.toCollection(ArrayList::new));

        List<ScheduledChunk> result = new ArrayList<>();
        int remainingMinutes = chunk.durationMinutes();

        for (TimeSlot slot : nextAvailableSlots) {

            if (remainingMinutes <= 0) {
                break;
            }

            int usableMinutes = Math.min(slot.durationMinutes(), remainingMinutes);

            // Only split if remainder is at least 15 minutes
            int remainder = remainingMinutes - usableMinutes;

            if (remainder > 0 && remainder < 15) {
                usableMinutes = remainingMinutes;
            }

            TimeSlot scheduledSlot = new TimeSlot(
                    slot.date(),
                    slot.startTime(),
                    slot.startTime().plusMinutes(usableMinutes)
            );

            TaskChunk partialChunk = TaskChunk.builder()
                    .chunkId(chunk.chunkId())
                    .taskId(chunk.taskId())
                    .chunkIndex(chunk.chunkIndex())
                    .totalChunks(chunk.totalChunks() + 1)
                    .durationMinutes(usableMinutes)
                    .topologicalLevel(chunk.topologicalLevel())
                    .slackMinutes(chunk.slackMinutes())
                    .cognitiveLoad(chunk.cognitiveLoad())
                    .notBefore(chunk.notBefore())
                    .deadline(chunk.deadline())
                    .isPinned(chunk.isPinned())
                    .dependsOnTaskIds(chunk.dependsOnTaskIds())
                    .build();

            result.add(new ScheduledChunk(partialChunk, scheduledSlot));

            remainingMinutes -= usableMinutes;

            removeTimeSlot(slot, partialChunk);
        }

        if (remainingMinutes > 0) {
            throw new IllegalStateException("Not enough free time available");
        }

        return result;
    }


    /**
     * Updates the attributes totalChunks and chunkIndex for each chunk
     * @param scheduledChunks List of scheduled chunks that belong to the same task and need to be updated due to further splitting
     * @return Updated list of scheduled chunks with correct chunkIndex and totalChunks attributes
     */
    public List<ScheduledChunk> updateChunks(List<ScheduledChunk> scheduledChunks) {

        int totalChunks = scheduledChunks.size();
        List<ScheduledChunk> result = new ArrayList<>();

        int currentIndex = 0;
        for (ScheduledChunk scheduledChunk : scheduledChunks) {
            TaskChunk chunk = scheduledChunk.chunk();
            TimeSlot timeSlot = scheduledChunk.slot();

            chunk = TaskChunk.builder()
                    .chunkId(chunk.chunkId())
                    .taskId(chunk.taskId())
                    .chunkIndex(currentIndex)
                    .totalChunks(totalChunks)
                    .durationMinutes(chunk.durationMinutes())
                    .topologicalLevel(chunk.topologicalLevel())
                    .slackMinutes(chunk.slackMinutes())
                    .cognitiveLoad(chunk.cognitiveLoad())
                    .notBefore(chunk.notBefore())
                    .deadline(chunk.deadline())
                    .isPinned(chunk.isPinned())
                    .dependsOnTaskIds(chunk.dependsOnTaskIds())
                    .build();

            currentIndex += 1;
            result.add(new ScheduledChunk(chunk, timeSlot));

        }
        return result;
    }



    /**
     * Removes the time occupied by a scheduled chunk from the list of available time slots, potentially splitting
     * existing slots if the chunk occupies only part of a slot.
     * @param target The time slot we want to remove from the available slots
     * @param chunk The chunk which occupies the target time slot
     */
    private void removeTimeSlot(TimeSlot target, TaskChunk chunk) {

        if (target == null || chunk == null) {
            return;
        }

        // Case 1: TimeSlot is more than 15 minutes bigger than the chunk -> split it into two separate slots
        if (target.durationMinutes() > chunk.durationMinutes() + 15) {

            LocalTime newStartTime = target.startTime().plusMinutes(chunk.durationMinutes());

            TimeSlot newTimeSlot = new TimeSlot(
                    target.date(),
                    newStartTime,
                    target.endTime()
            );

            this.globalAvailableTimeSlots.remove(target);
            this.globalAvailableTimeSlots.add(newTimeSlot);


        }
        // Case 2: TimeSlot is equal to or only lightly bigger than the chunk
        else  {
            //this.globalAvailableTimeSlots.contains(target);
            this.globalAvailableTimeSlots.remove(target);
        }
    }


    /**
     * Forward pass: compute Earliest Start (ES) and Earliest End (EF) for each task.
     * ES = max(notBefore, max(EF of predecessors))
     * EF = ES + task duration in available work minutes
     */
    private List<TaskSlack> calculateSlack(SchedulingContext context) {

        ArrayList<TaskSlack> results = new ArrayList<>();

        for (TaskResponse task : context.tasks()) {

            // No deadline -> infinite slack
            if (task.getDeadline() == null) {
                results.add(new TaskSlack(
                        task.getId(),
                        null,
                        null,
                        null,
                        null,
                        Long.MAX_VALUE
                ));
                continue;
            }

            LocalDateTime earliestStart;
            if (task.getDontScheduleBefore() == null) {
                earliestStart = context.fromDate().
                        atTime(context.workingTimes().getFirst().getStartTime());
            } else {
                earliestStart = task.getDontScheduleBefore().toLocalDateTime();
            }

            LocalDateTime latestFinish = task.getDeadline().toLocalDateTime();
            LocalDateTime earliestFinish = earliestStart.plusMinutes(task.getDuration());
            LocalDateTime latestStart = latestFinish.minusMinutes(task.getDuration());
            long slackMinutes = java.time.Duration.between(earliestStart, latestStart).toMinutes();

            results.add(new TaskSlack(
                    task.getId(),
                    earliestStart,
                    earliestFinish,
                    latestStart,
                    latestFinish,
                    slackMinutes
            ));
        }
        return results;
    }
}


