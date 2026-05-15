package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.UnscheduledTask;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TaskSlack;
import de.goaldone.backend.scheduler.types.model.TimeSlot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.*;

public class CPMAlgorithm {

    private static final int DEFAULT_AUTO_BREAK_DURATION = 15;
    private static final String AUTOMATED_BREAK_TITLE = "Automatische Pause";
    private final int MAX_BUFFER = 15;

    private ArrayList<TimeSlot> tempAvailableTimeSlots;
    private UnscheduledTask.ReasonEnum unscheduledReason;

    private final Chunker chunker = new Chunker();
    private final TaskSorter taskSorter = new TaskSorter();

    private record TimeSlotTuple(TimeSlot occupiedSlot, TimeSlot breakSlot) {
    }

    /**
     * Creates initial schedule
     * @param context Scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return A first, heuristic generated schedule
     */
    public SolverState generateInitialSchedule(SchedulingContext context) {
        ArrayList<TimeSlot> availableTimeSlots = new ArrayList<>(context.availableSlots());
        ArrayList<UnscheduledTask> unscheduledTask = new ArrayList<>();

        List<TaskResponse> tasks = context.tasks();
        Map<UUID,TaskResponse> taskMap = tasks.stream().collect(Collectors.toMap(TaskResponse::getId, t -> t));
        Map<UUID, List<TaskChunk>> chunkMap = chunker.chunkTasks(tasks, context.workingTimes());

        // Sort chunks based on dependencies, slackTime and kognitive load
        List<TaskSlack> taskSlacks = calculateSlack(context);
        List<UUID> sortedTasks = taskSorter.sort(tasks, taskSlacks);

        List<ScheduledChunk> resultChunks = new ArrayList<>();

        boolean totalTaskFit;
        for (UUID taskId : sortedTasks) {
            // Create snapshot of available times to later either
            // - accept if the complete task fits in the plan
            // - or revert if it doesn't
            this.tempAvailableTimeSlots = new ArrayList<>(availableTimeSlots);
            totalTaskFit = true; //default true, is set to false if task doesn't fit

            // Try fitting all chunks
            List<TaskChunk> chunks = chunkMap.get(taskId);
            List<ScheduledChunk> tempResults = new ArrayList<>();
            for (TaskChunk chunk : chunks) {
                List<ScheduledChunk> scheduledChunk = findTimeSlotForChunk(chunk, taskMap.get(taskId));

                if (scheduledChunk.isEmpty()) {
                    totalTaskFit = false;
                    break;
                }
                tempResults.addAll(scheduledChunk);
            }
            tempResults = updateChunks(tempResults);

            if (totalTaskFit) {
                resultChunks.addAll(tempResults);
                availableTimeSlots = this.tempAvailableTimeSlots;
            } else {
                unscheduledTask.add(new UnscheduledTask(
                        taskId, taskMap.get(taskId).getTitle(), this.unscheduledReason
                ));
                this.unscheduledReason = null;
            }

        }
        return new SolverState (resultChunks, availableTimeSlots, unscheduledTask);
    }

    /**
     * Finds suitable time slots for the given task chunk. If no single slot is large
     * enough, the chunk is split across multiple available slots.
     * @param chunk the task chunk to schedule
     * @return a list of scheduled chunks with assigned time slots
     * @throws IllegalStateException if there is not enough free time available
     */
    private List<ScheduledChunk> findTimeSlotForChunk(TaskChunk chunk, TaskResponse task) {

        List<List<TimeSlot>> suitableSlots = getSuitableTimeSlots(chunk);
        if (suitableSlots.isEmpty()) return List.of();


        List<TimeSlot> slotsBetween = suitableSlots.getLast();

        if (slotsBetween.isEmpty()) {
            this.unscheduledReason = UnscheduledTask.ReasonEnum.CAPACITY_EXCEEDED;
            return List.of();
        }

        // At this point, at least one timeslot was found in between notBefore and deadline

        int additionalBreakMinutes = getAdditionalTimeForAutomatedBreaks(chunk, task);

        Optional<TimeSlot> suitableSlot = slotsBetween.stream()
                .filter(slot -> slot.canFit(chunk.durationMinutes()))
                .min(Comparator.comparing(TimeSlot::date).thenComparing(TimeSlot::startTime));

        if (suitableSlot.isPresent()) {
            TimeSlot slot = suitableSlot.get();

            TimeSlotTuple timeSlotTuple = updateTimeSlots(slot, chunk, additionalBreakMinutes);
            List<ScheduledChunk> scheduledChunks = new ArrayList<>();
            scheduledChunks.add(new ScheduledChunk(chunk, timeSlotTuple.occupiedSlot()));

            if (timeSlotTuple.breakSlot() != null) {
                scheduledChunks.add(new ScheduledChunk(
                        createAutomatedBreakChunk(timeSlotTuple.breakSlot(), task.getId()),
                        timeSlotTuple.breakSlot()
                ));
            }

            return scheduledChunks;
        }

        // At this point, no slot was large enough to accommodate this chunk
        // -> try further splitting

        // Check if there is enough time in all the slots
        if (!enoughTimeLeftWithBuffer(suitableSlots, chunk)) {
            return List.of();
        }

        // Enough time left -> Split chunk further down
        List<ScheduledChunk> result = new ArrayList<>();
        int remainingMinutes = chunk.durationMinutes();

        for (TimeSlot slot : slotsBetween) {

            if (remainingMinutes <= 0) {
                break;
            }

            int usableMinutes = Math.min(slot.durationMinutes(), remainingMinutes);

            // Only split if remainder is at least 15 minutes
            int remainder = remainingMinutes - usableMinutes;

            if (remainder > 0 && remainder <= this.MAX_BUFFER) {
                usableMinutes = remainingMinutes;
            }

            TimeSlot scheduledSlot = new TimeSlot(
                    slot.date(),
                    slot.startTime(),
                    slot.startTime().plusMinutes(usableMinutes)
            );

            TaskChunk partialChunk = TaskChunk.builder()
                    .taskTitle(chunk.taskTitle())
                    .chunkId(chunk.chunkId())
                    .taskId(chunk.taskId())
                    .chunkIndex(chunk.chunkIndex())
                    .totalChunks(chunk.totalChunks())
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

            int partialBreakMinutes = remainingMinutes == 0 ? additionalBreakMinutes : 0;
            TimeSlotTuple timeSlotTuple = updateTimeSlots(slot, partialChunk, partialBreakMinutes);
            if (timeSlotTuple.breakSlot() != null) {
                result.add(new ScheduledChunk(
                        createAutomatedBreakChunk(timeSlotTuple.breakSlot(), task.getId()),
                        timeSlotTuple.breakSlot()
                ));
            }
        }

        if (remainingMinutes > 0) {
            this.unscheduledReason = UnscheduledTask.ReasonEnum.OTHER;
            return List.of();
        }

        return result;
    }

    private int getAdditionalTimeForAutomatedBreaks(TaskChunk chunk, TaskResponse task) {

        // Return 0 minutes for custom chunk size
        if (task.getCustomChunkSize() != null) {
            return 0;
        }

        // Return 0 minutes for tasks with low cognitive load
        if (task.getCognitiveLoad() == CognitiveLoad.LOW) {
            return 0;
        }

        int maxChunkSize = switch (task.getCognitiveLoad()) {
            case HIGH -> 120;
            case MODERATE -> 480;
            default -> 24*60;       // default value: 24 hours
        };

        if (chunk.durationMinutes() >= maxChunkSize) {
            return DEFAULT_AUTO_BREAK_DURATION;
        }
        return 0;
    }

    /**
     * @param chunk The chunk to fit in a slot
     * @param slot The time slot in question
     * @return True, if the start time of the time slot after dontScheduleBefore.
     */
    private boolean isAfterNotBefore(TaskChunk chunk, TimeSlot slot) {
        LocalDateTime slotStartDateTime = LocalDateTime.of(slot.date(), slot.startTime());

        return chunk.notBefore() == null // Either true, because there is no "not before"
                ||  !slotStartDateTime.isBefore(chunk.notBefore()); // Or check notBefore
    }

    /**
     * @param chunk The chunk to fit in a slot
     * @param slot The time slot in question
     * @return True, if the end time of the time slot is before the deadline.
     */
    private boolean isBeforeDeadline(TaskChunk chunk, TimeSlot slot) {
        LocalDateTime slotEndDateTime = LocalDateTime.of(slot.date(), slot.endTime());
        return chunk.deadline() == null  // Either true because there is no deadline
                || !slotEndDateTime.isAfter(chunk.deadline()); // Or check deadline
    }

    /**
     * @param suitableSlots List of suitable slots for this chunk
     * @param chunk The chunk to fit in the slots
     * @return True, if the total duration of all suitable slots is bigger
     *          than the duration of the chunk
     */
    private boolean enoughTimeLeftWithBuffer(List<List<TimeSlot>> suitableSlots, TaskChunk chunk) {
        List<TimeSlot> slotsAfterNotBefore = suitableSlots.getFirst();
        List<TimeSlot> slotsBetween = suitableSlots.getLast();

        int totalMinAvailable = slotsBetween.stream().mapToInt(TimeSlot::durationMinutes).sum();

        if (totalMinAvailable + this.MAX_BUFFER < chunk.durationMinutes()) {

            // Not enough time left. Find reason
            int totalMinAfterNotBefore = slotsAfterNotBefore.stream().mapToInt(TimeSlot::durationMinutes).sum();
            if (totalMinAfterNotBefore < chunk.durationMinutes()) {
                this.unscheduledReason = UnscheduledTask.ReasonEnum.CAPACITY_EXCEEDED;
            } else {
                this.unscheduledReason = UnscheduledTask.ReasonEnum.DEADLINE_IMPOSSIBLE;
            }
            return false;
        }
        return true;
    }

    /**
     * @param chunk The chunk to fit containing notBefore and the deadline
     * @return Two lists:
     *      1. List of time slots after the date "notBefore"
     *      2. List of time slots in between notBefore and the deadline
     */
    private List<List<TimeSlot>> getSuitableTimeSlots(TaskChunk chunk) {
        List<TimeSlot> slotsAfterNotBefore = this.tempAvailableTimeSlots.stream()
                .filter(slot -> isAfterNotBefore(chunk, slot))
                .toList();

        if (slotsAfterNotBefore.isEmpty()) {
            this.unscheduledReason = UnscheduledTask.ReasonEnum.NO_TIMESLOTS_AFTER_NOTBEFORE;
            return List.of();
        }

        List<TimeSlot> slotsBetween = slotsAfterNotBefore.stream()
                .filter(slot -> isBeforeDeadline(chunk, slot))
                .toList();
        if (slotsBetween.isEmpty()) {
            this.unscheduledReason = UnscheduledTask.ReasonEnum.DEADLINE_IMPOSSIBLE;
            return List.of();
        }
        return List.of(slotsAfterNotBefore, slotsBetween);
    }

    /**
     * Updates the attributes totalChunks and chunkIndex for each chunk
     * @param scheduledChunks List of scheduled chunks that belong to the same task and need to be updated due to further splitting
     * @return Updated list of scheduled chunks with correct chunkIndex and totalChunks attributes
     */
    public List<ScheduledChunk> updateChunks(List<ScheduledChunk> scheduledChunks) {

        int totalChunks = (int) scheduledChunks.stream()
                .filter(scheduledChunk -> !isAutomatedBreak(scheduledChunk.chunk()))
                .count();
        List<ScheduledChunk> result = new ArrayList<>();

        int currentIndex = 0;
        for (ScheduledChunk scheduledChunk : scheduledChunks) {
            TaskChunk chunk = scheduledChunk.chunk();
            TimeSlot timeSlot = scheduledChunk.slot();

            if (isAutomatedBreak(chunk)) {
                result.add(scheduledChunk);
                continue;
            }

            chunk = TaskChunk.builder()
                    .taskTitle(chunk.taskTitle())
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
     * @return The occupied work slot and, if generated, the automatic break slot directly afterward
     */
    private TimeSlotTuple updateTimeSlots(TimeSlot target, TaskChunk chunk, int additionalBreakTime) {

        if (target == null || chunk == null) {
            throw new IllegalArgumentException("There was a problem in updating the available time slots");
        }

        TimeSlot occupiedSlot = new TimeSlot(
                target.date(),
                target.startTime(),
                target.startTime().plusMinutes(chunk.durationMinutes())
        );

        // Case 1: TimeSlot is more than 30 minutes bigger than the chunk
        // -> split it into three separate slots
        //  1. Slot for chunk
        //  2. Slot for automated break;
        //  3. Rest of time slot
        if (target.durationMinutes() > chunk.durationMinutes() + additionalBreakTime + this.MAX_BUFFER) {
            TimeSlot automatedBreakSlot = additionalBreakTime > 0
                    ? new TimeSlot(
                            target.date(),
                            occupiedSlot.endTime(),
                            occupiedSlot.endTime().plusMinutes(additionalBreakTime)
                    )
                    : null;
            TimeSlot newTimeSlot = new TimeSlot(
                    target.date(),
                    automatedBreakSlot != null ? automatedBreakSlot.endTime() : occupiedSlot.endTime(),
                    target.endTime()
            );

            this.tempAvailableTimeSlots.remove(target);
            this.tempAvailableTimeSlots.add(newTimeSlot);
            return new TimeSlotTuple(occupiedSlot, automatedBreakSlot);
        }
        // Case 2: TimeSlot is only lightly bigger than the chunk
        // -> split it into two separate slots
        // 1. Slot for chunk
        // 2. Slot for small break
        else if (target.durationMinutes() >= chunk.durationMinutes() + this.MAX_BUFFER) {
            this.tempAvailableTimeSlots.remove(target);

            TimeSlot automatedBreakSlot = additionalBreakTime > 0
                    ? new TimeSlot(target.date(), occupiedSlot.endTime(), target.endTime())
                    : null;
            return new TimeSlotTuple(occupiedSlot, automatedBreakSlot);
        }
        // Case 3: Time slot duration is equal to chunk duration
        // -> Fill timeslot
        else {
            this.tempAvailableTimeSlots.remove(target);
            return new TimeSlotTuple(occupiedSlot, null);
        }
    }

    private TaskChunk createAutomatedBreakChunk(TimeSlot breakSlot, UUID taskId) {
        return TaskChunk.builder()
                .taskTitle(AUTOMATED_BREAK_TITLE)
                .chunkId(UUID.randomUUID())
                .taskId(taskId)
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes(breakSlot.durationMinutes())
                .topologicalLevel(0)
                .slackMinutes(0)
                .cognitiveLoad(null)
                .notBefore(null)
                .deadline(null)
                .isPinned(false)
                .dependsOnTaskIds(List.of())
                .build();
    }

    private boolean isAutomatedBreak(TaskChunk chunk) {
        return AUTOMATED_BREAK_TITLE.equals(chunk.taskTitle());
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
            long slackMinutes = Duration.between(earliestStart, latestStart).toMinutes();

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
