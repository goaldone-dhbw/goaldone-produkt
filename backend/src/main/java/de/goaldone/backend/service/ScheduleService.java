package de.goaldone.backend.service;

import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.scheduler.Solver;
import de.goaldone.backend.scheduler.types.model.PlanningContext;
import de.goaldone.backend.scheduler.types.model.PlanningResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    Solver solver = new Solver();

    public ScheduleResponse generateSchedule(UUID goaldoneUserID, List<UUID> accountIDs) {

        // Validate goaldone user and its connected accounts using ids


        // Get data from database

        PlanningContext planningContext = null;

        // Forward to schedule generator
        PlanningResult bestResult = solver.createSchedule(planningContext); //TODO: Pass tasks and appointments from db

        return null;
    }
}