package de.goaldone.backend.controller;

import de.goaldone.backend.api.SchedulesApi;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.MultiAccountScheduleResponse;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class ScheduleController implements SchedulesApi {

    private final ScheduleService scheduleService;

    @Override
    public ResponseEntity<MultiAccountScheduleResponse> generateAllAccountsSchedule(GenerateScheduleRequest generateScheduleRequest) throws Exception {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<MultiAccountScheduleResponse> getAllAccountsSchedule(LocalDate from, LocalDate to) throws Exception {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<ScheduleResponse> generateSingleAccountSchedule(UUID accountId, GenerateScheduleRequest generateScheduleRequest) throws Exception {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSingleAccountSchedule(UUID accountId, LocalDate from, LocalDate to) throws Exception {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
