package de.goaldone.backend.controller;

import de.goaldone.backend.api.SchedulesApi;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.MultiAccountScheduleResponse;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.security.AuthorizationFacade;
import de.goaldone.backend.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class ScheduleController implements SchedulesApi {

    private final ScheduleService scheduleService;
    private final AuthorizationFacade authorizationFacade;

    private final long timeoutMilliseconds = 10000;

    /**
     * Generates schedules for all accounts linked to the current user identity.
     *
     * @param generateScheduleRequest Request containing the schedule generation parameters.
     * @return Schedule responses for all linked accounts.
     */
    @Override
    public ResponseEntity<MultiAccountScheduleResponse> generateAllAccountsSchedule(GenerateScheduleRequest generateScheduleRequest) {
        List<UUID> accountIds = authorizationFacade.getAccessibleAccountIds();

        List<ScheduleResponse> scheduleResponses = scheduleService.generateMultiAccountSchedule(
                accountIds, generateScheduleRequest, timeoutMilliseconds
        );

        MultiAccountScheduleResponse response = new MultiAccountScheduleResponse();
        response.setSchedules(scheduleResponses);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<MultiAccountScheduleResponse> getAllAccountsSchedule(LocalDate from, LocalDate to) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * Generates a schedule for a single account.
     *
     * @param accountId                Account for which the schedule will be generated (required)
     * @param generateScheduleRequest  (required)
     * @return Schedule for the given account
     */
    @Override
    public ResponseEntity<ScheduleResponse> generateSingleAccountSchedule(UUID accountId, GenerateScheduleRequest generateScheduleRequest) {
        authorizationFacade.requireAccountAccess(accountId);

        ScheduleResponse scheduleResponse = scheduleService.generateSingleAccountSchedule(
                accountId, generateScheduleRequest, timeoutMilliseconds
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleResponse);
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSingleAccountSchedule(UUID accountId, LocalDate from, LocalDate to) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
