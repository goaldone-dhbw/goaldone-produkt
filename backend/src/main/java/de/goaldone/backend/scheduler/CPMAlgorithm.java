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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * NOT thread safe – instance fields {@code tempAvailableTimeSlots} and
 * {@code unscheduledReason} are mutated during scheduling and must not be
 * shared across concurrent invocations.
 */
public class CPMAlgorithm {

    private static final int DEFAULT_AUTO_BREAK_DURATION = 15;
    private static final String AUTOMATED_BREAK_TITLE = "Automatische Pause";
    private final int MAX_BUFFER = 15;

    private ArrayList<TimeSlot> tempAvailableTimeSlots;
    private UnscheduledTask.ReasonEnum unscheduledReason;

    private final Chunker chunker = new Chunker();
    private final TaskSorter taskSorter = new TaskSorter();

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

                List<ScheduledChunk> scheduledChunks = findTimeSlotForChunk(chunk, taskMap.get(taskId));

                if (scheduledChunks.isEmpty()) {
                    totalTaskFit = false;
                    break;
                }
                tempResults.addAll(scheduledChunks);
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
        return new SolverState(resultChunks, availableTimeSlots, unscheduledTask, context);
    }

    /**
     * Finds suitable time slots for the given task chunk.
     * The chunk is split across multiple available slots.
     * @param chunk the task chunk to schedule
     * @return a list of scheduled chunks with assigned time slots
     * @throws IllegalStateException if there is not enough free time available
     */
    private List<ScheduledChunk> findTimeSlotForChunk(TaskChunk chunk, TaskResponse task) {

        List<TimeSlot> slotsBetween = getSuitableTimeSlots(chunk);
        if (slotsBetween.isEmpty()) return List.of();

        // At this point, at least one time slot was found in between notBefore and deadline and
        // at least the sum of all slots can fit the chunk
        // -> Greedy slot occupation

        List<ScheduledChunk> result = new ArrayList<>();
        int remainingMinutes = chunk.durationMinutes();

        for (TimeSlot slot : slotsBetween) {

            if (remainingMinutes <= 0) break;

            int usableMinutes = calculateUsableMinutes(slot.durationMinutes(), remainingMinutes);

            // Fill slot with chunk
            TaskChunk partialChunk = getPartialChunk(chunk, usableMinutes);
            TimeSlot partialSlot = getPartialSlot(slot, usableMinutes);
            validateSlotAndChunk(partialSlot, partialChunk);
            result.add(new ScheduledChunk(partialChunk, partialSlot));

            remainingMinutes -= usableMinutes;

            // Calculate optional break slot
            int breakMinutes = getAdditionalTimeForAutomatedBreaks(chunk, remainingMinutes);
            Optional<TimeSlot> automatedBreakSlot = createAutomatedBreakSlot(slot, partialSlot, breakMinutes);
            if (automatedBreakSlot.isPresent()) {
                TimeSlot breakSlot = automatedBreakSlot.get();
                TaskChunk breakChunk = createAutomatedBreakChunk(breakSlot.durationMinutes(), task);
                    result.add(new ScheduledChunk(breakChunk,breakSlot));
            }

            // Calculate remaining slot
            LocalTime endTime = automatedBreakSlot.map(TimeSlot::endTime).orElse(partialSlot.endTime());
            Optional<TimeSlot> remainingSlot = createRemainingAvailableSlot(endTime, slot);

            replaceAvailableSlot(slot, remainingSlot.orElse(null));
        }

        if (remainingMinutes > this.MAX_BUFFER) {
            this.unscheduledReason = UnscheduledTask.ReasonEnum.OTHER;
            return List.of();
        }
        return result;
    }

    /**
     * Calculates how many minutes of a task can be planned into a slot.
     * Extends the slot if the remaining duration is within the allowed buffer.
     *
     * @param slotDuration the available duration of the slot in minutes
     * @param remainingMinutes the remaining task duration in minutes
     * @return the usable duration in minutes
     */
    private int calculateUsableMinutes(int slotDuration, int remainingMinutes) {
        int usableMinutes = Math.min(slotDuration, remainingMinutes);

        int remainder = remainingMinutes - usableMinutes;

        // Extend slot if task only slightly bigger
        if (remainder > 0 && remainder <= this.MAX_BUFFER) {
            return remainingMinutes;
        }

        return usableMinutes;
    }

    /**
     * Creates a partial task chunk with the specified duration
     * while preserving the original chunk metadata.
     *
     * @param chunk the original task chunk
     * @param usableMinutes the duration of the partial chunk in minutes
     * @return a new partial task chunk
     */
    private TaskChunk getPartialChunk(TaskChunk chunk, int usableMinutes) {
        return TaskChunk.builder()
                .taskTitle(chunk.taskTitle())
                .chunkId(chunk.chunkId())
                .taskId(chunk.taskId())
                .chunkIndex(chunk.chunkIndex())
                .totalChunks(chunk.totalChunks())
                .durationMinutes(usableMinutes)
                .cognitiveLoad(chunk.cognitiveLoad())
                .notBefore(chunk.notBefore())
                .deadline(chunk.deadline())
                .isBreak(chunk.isBreak())
                .dependsOnTaskIds(chunk.dependsOnTaskIds())
                .build();
    }

    /**
     * Creates a partial time slot with the given duration,
     * starting from the original slot start time.
     *
     * @param slot the original time slot
     * @param usableMinutes the duration of the partial slot in minutes
     * @return a new partial time slot
     */
    private TimeSlot getPartialSlot(TimeSlot slot, int usableMinutes) {
        return new TimeSlot(
                slot.date(), slot.startTime(), slot.startTime().plusMinutes(usableMinutes)
        );
    }

    /**
     * Returns the duration of an automatically inserted break
     * if the chunk fully fits and the cognitive load limit is reached.
     *
     * @param chunk the task chunk to evaluate
     * @param remainingMinutes remaining unplanned minutes of the chunk
     * @return the additional break duration in minutes, otherwise {@code 0}
     */
    private int getAdditionalTimeForAutomatedBreaks(TaskChunk chunk, int  remainingMinutes) {

        // Chunk not fully planned
        if (remainingMinutes > 0) {
            return 0;
        }

        if (cognitiveLoadReached(chunk.cognitiveLoad(), chunk.durationMinutes())) {
            return DEFAULT_AUTO_BREAK_DURATION;
        }

        return 0;
    }

    /**
     * Checks whether the maximum recommended duration
     * for the given cognitive load has been reached.
     *
     * @param cognitiveLoad the cognitive load level
     * @param plannedMinutes the planned duration in minutes
     * @return {@code true} if the limit is reached, otherwise {@code false}
     */
    private boolean cognitiveLoadReached(CognitiveLoad cognitiveLoad, int plannedMinutes) {
        int maxChunkSize = switch (cognitiveLoad) {
            case HIGH -> 120;
            case MODERATE -> 240;
            default -> Integer.MAX_VALUE;
        };
        return plannedMinutes >= maxChunkSize;
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
     * @param slotsAfterNotBefore List of suitable slots for this chunk after "not before"
     * @param slotsBetween List of suitable slots between "not before" and the deadline
     * @param chunk The chunk to fit in the slots
     * @return True, if the total duration of all suitable slots is bigger
     *          than the duration of the chunk
     */
    private boolean enoughTimeLeftWithBuffer(List<TimeSlot> slotsAfterNotBefore, List<TimeSlot> slotsBetween, TaskChunk chunk) {

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
    private List<TimeSlot> getSuitableTimeSlots(TaskChunk chunk) {
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

        // Check if there is enough time in all the slots to fit the chunk
        if (!enoughTimeLeftWithBuffer(slotsAfterNotBefore, slotsBetween, chunk)) {
            return List.of();
        }

        return slotsBetween;
    }

    /**
     * Updates the attributes totalChunks and chunkIndex for each chunk
     * @param scheduledChunks List of scheduled chunks that belong to the same task and need to be updated due to further splitting
     * @return Updated list of scheduled chunks with correct chunkIndex and totalChunks attributes
     */
    public List<ScheduledChunk> updateChunks(List<ScheduledChunk> scheduledChunks) {

        int totalChunks = (int) scheduledChunks.stream()
                .filter(scheduledChunk -> !scheduledChunk.chunk().isBreak())
                .count();
        List<ScheduledChunk> result = new ArrayList<>();

        scheduledChunks = scheduledChunks.stream()
                .sorted(Comparator.comparing(ScheduledChunk::date)
                .thenComparing(ScheduledChunk::startTime))
                .toList();

        int currentIndex = 0;
        for (ScheduledChunk scheduledChunk : scheduledChunks) {
            TaskChunk chunk = scheduledChunk.chunk();
            TimeSlot timeSlot = scheduledChunk.slot();

            if (chunk.isBreak()) {
                result.add(scheduledChunk);
                continue;
            }

            chunk = updateTaskChunk(chunk, currentIndex, totalChunks);

            currentIndex += 1;
            result.add(new ScheduledChunk(chunk, timeSlot));
        }
        return result;
    }

    /**
     * Validates that the target slot and chunk can be used to update the available slot list.
     *
     * @param targetSlot The slot that will be removed or shortened
     * @param chunk The chunk that will occupy the slot
     */
    private void validateSlotAndChunk(TimeSlot targetSlot, TaskChunk chunk) {
        if (targetSlot == null || chunk == null) {
            throw new IllegalArgumentException("There was a problem in updating the available time slots");
        }
    }


    /**
     * Creates the remaining available slot after work and an optional automated break.
     *
     * @param targetSlot The original available slot
     * @param nextAvailableStart The first minute that can become available again
     * @return A remaining slot if at least {@link #MAX_BUFFER} minutes are left
     */
    private Optional<TimeSlot> createRemainingAvailableSlot(LocalTime nextAvailableStart, TimeSlot targetSlot) {
        int remainingMinutes = (int) Duration.between(nextAvailableStart, targetSlot.endTime()).toMinutes();

        // Time slot is too small for extra time slot
        if (remainingMinutes < this.MAX_BUFFER) {
            return Optional.empty();
        }

        // Time slot is large enough for extra time slot
        return Optional.of(new TimeSlot(
                targetSlot.date(), nextAvailableStart, targetSlot.endTime()
        ));
    }

    /**
     * Replaces the consumed available slot with the remaining available part, if one exists.
     *
     * @param targetSlot The slot consumed by the scheduled chunk and optional break
     * @param remainingSlot The remaining available part of the target slot
     */
    private void replaceAvailableSlot(TimeSlot targetSlot, TimeSlot remainingSlot) {
        int targetIndex = this.tempAvailableTimeSlots.indexOf(targetSlot);
        if (targetIndex < 0) {
            return;
        }

        this.tempAvailableTimeSlots.remove(targetIndex);

        if (remainingSlot != null) {
            this.tempAvailableTimeSlots.add(targetIndex, remainingSlot);
        }
    }

    /**
     * Creates the automated break slot directly after the occupied work slot.
     *
     * @param targetSlot The parent slot in which the break should fit into
     * @param occupiedSlot The work slot after which the break starts
     * @param additionalBreakTime The break duration in minutes, or 0 if no break is needed
     * @return The automated break slot, or null if break does not fit or is not needed
     */
    private Optional<TimeSlot> createAutomatedBreakSlot(TimeSlot targetSlot, TimeSlot occupiedSlot, int additionalBreakTime) {
        long timeLeft = Duration.between(occupiedSlot.endTime(), targetSlot.endTime()).toMinutes();

        if (additionalBreakTime == 0 || timeLeft <= 0) {
            return Optional.empty();
        }

        return Optional.of(new TimeSlot(
                occupiedSlot.date(),
                occupiedSlot.endTime(),
                occupiedSlot.endTime().plusMinutes(Math.min(timeLeft, additionalBreakTime))
        ));
    }


    /**
     * @param chunk The previous state of the task chunk
     * @param currentIndex The new chunk index
     * @param totalChunks The new number of total chunks
     * @return The updated task chunk
     */
    private TaskChunk updateTaskChunk(TaskChunk chunk, int currentIndex, int totalChunks) {
        return TaskChunk.builder()
                .taskTitle(chunk.taskTitle())
                .chunkId(chunk.chunkId())
                .taskId(chunk.taskId())
                .chunkIndex(currentIndex)
                .totalChunks(totalChunks)
                .durationMinutes(chunk.durationMinutes())
                .cognitiveLoad(chunk.cognitiveLoad())
                .notBefore(chunk.notBefore())
                .deadline(chunk.deadline())
                .isBreak(chunk.isBreak())
                .dependsOnTaskIds(chunk.dependsOnTaskIds())
                .build();
    }

    /**
     * @param durationMinutes Duration of the break
     * @param task The parent task
     * @return TaskChunk with attributes for an automated break.
     */
    private TaskChunk createAutomatedBreakChunk(int durationMinutes, TaskResponse task) {
        return TaskChunk.builder()
                .taskTitle(AUTOMATED_BREAK_TITLE)
                .chunkId(UUID.randomUUID())
                .taskId(task.getId())
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes(durationMinutes)
                .cognitiveLoad(task.getCognitiveLoad())
                .notBefore(null)
                .deadline(null)
                .isBreak(true)
                .dependsOnTaskIds(List.of())
                .build();
    }

    /**
     * Tries to schedule tasks that were left unscheduled after the initial plan.
     * This method is called exactly once before the move-loop.
     * Uses the same CPM sort order as {@link #generateInitialSchedule}.
     *
     * <p>NOT thread safe – modifies {@code tempAvailableTimeSlots} and
     * {@code unscheduledReason} instance fields.</p>
     *
     * @param state   the current solver state whose unscheduled tasks should be re-attempted
     * @param context the original scheduling context (used for working times, fromDate and task list)
     * @return updated SolverState with any newly scheduled chunks and a revised unscheduled list
     */
    SolverState tryScheduleUnscheduled(SolverState state, SchedulingContext context) {

        if (state.unscheduledTasks() == null || state.unscheduledTasks().isEmpty()) {
            return state;
        }

        // Collect the IDs of all still-unscheduled tasks
        Set<UUID> unscheduledIds = state.unscheduledTasks().stream()
                .map(UnscheduledTask::getTaskId)
                .collect(Collectors.toSet());

        List<TaskResponse> unscheduledTasks = context.tasks().stream()
                .filter(t -> unscheduledIds.contains(t.getId()))
                .toList();

        if (unscheduledTasks.isEmpty()) {
            return state;
        }

        // Chunk the unscheduled tasks
        Map<UUID, TaskResponse> taskMap = unscheduledTasks.stream()
                .collect(Collectors.toMap(TaskResponse::getId, t -> t));
        Map<UUID, List<TaskChunk>> chunkMap = chunker.chunkTasks(unscheduledTasks, context.workingTimes());

        // Sort using CPM order (same as generateInitialSchedule)
        List<TaskSlack> taskSlacks = calculateSlack(context);
        List<UUID> sortedTasks = taskSorter.sort(unscheduledTasks, taskSlacks);

        // Work on a mutable copy of the current free slots
        ArrayList<TimeSlot> availableTimeSlots = new ArrayList<>(state.freeSlots());
        List<ScheduledChunk> newScheduledChunks = new ArrayList<>(state.scheduledChunks());
        ArrayList<UnscheduledTask> stillUnscheduled = new ArrayList<>();

        for (UUID taskId : sortedTasks) {
            this.tempAvailableTimeSlots = new ArrayList<>(availableTimeSlots);
            boolean totalTaskFit = true;

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
                newScheduledChunks.addAll(tempResults);
                availableTimeSlots = this.tempAvailableTimeSlots;
            } else {
                stillUnscheduled.add(new UnscheduledTask(
                        taskId, taskMap.get(taskId).getTitle(), this.unscheduledReason
                ));
                this.unscheduledReason = null;
            }
        }

        return new SolverState(newScheduledChunks, availableTimeSlots, stillUnscheduled, context);
    }

    /**
     * Calculates the scheduling slack for each task based on
     * its earliest possible start and latest possible finish.
     *
     * @param context the scheduling context containing tasks and working times
     * @return a list of calculated task slack values
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
                earliestStart = context.fromDate().toLocalDate().
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
