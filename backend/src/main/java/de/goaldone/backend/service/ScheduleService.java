package de.goaldone.backend.service;

import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.TaskListResponse;
import de.goaldone.backend.scheduler.Solver;
import de.goaldone.backend.scheduler.types.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    Solver solver = new Solver();
    TaskService taskService = new TaskService();

    public ScheduleResponse generateSchedule(UUID accountID) {

        // Tasks
        TaskListResponse allTasks = taskService.listTasks(accountID);

        // Chunk unpinned tasks
        List<TaskChunk> chunks = null;

        // List pinned tasks
        List<ScheduledChunk> pinnedChunks = null;

        // Calculate free slots
        List<TimeSlot> availableSlots = null;

        // From which date on, should the tasks be loaded
        // Where to get this date from?
        LocalDate fromDate = null;

        PlanningContext planningContext = new PlanningContext(
                accountID,
                fromDate,
                availableSlots,
                chunks,
                pinnedChunks
        );

        // Forward to schedule generator
        PlanningResult bestResult = solver.createSchedule(planningContext); //TODO: Pass tasks and appointments from db

        return null;
    }

    public ScheduleResponse getSchedule() {
        return null; //TODO
    }



}