package de.goaldone.backend.service;

import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.repositories.GoaldoneUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final GoaldoneUserRepository goaldoneUserRepository;

    public ScheduleResponse generateSchedule(UUID goaldoneUserID, List<UUID> accountIDs) {

        // Validate goaldone user and its connected accounts

        // Forward to schedule generator

        return null;
    }




}