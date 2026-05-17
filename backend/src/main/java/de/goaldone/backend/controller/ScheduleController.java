package de.goaldone.backend.controller;

import de.goaldone.backend.api.SchedulesApi;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.MarkScheduleEntryRequest;
import de.goaldone.backend.model.MarkScheduleEntryResponse;
import de.goaldone.backend.model.MultiAccountScheduleResponse;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.ScheduleService;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class ScheduleController implements SchedulesApi {

    private final ScheduleService scheduleService;
    private final UserIdentityService userIdentityService;
    private final CurrentUserResolver currentUserResolver;

    private final long timeoutMilliseconds = 10000;

    private List<UserAccountEntity> getAccountsLinkedToIdentity(Jwt jwt) {
        return userIdentityService
                .findAccountsForIdentity(userIdentityService.findIdentityFromAccount(jwt));
    }

    /**
     * Generates schedules for all accounts linked to the current user identity.
     *
     * @param generateScheduleRequest Request containing the schedule generation parameters.
     * @return Schedule responses for all linked accounts.
     */
    @Override
    public ResponseEntity<MultiAccountScheduleResponse> generateAllAccountsSchedule(GenerateScheduleRequest generateScheduleRequest) {

        Jwt jwt = currentUserResolver.extractJwt();

        List<UUID> accountIds = getAccountsLinkedToIdentity(jwt)
                .stream()
                .map(UserAccountEntity::getId)
                .toList();

        List<ScheduleResponse> scheduleResponses = scheduleService.generateMultiAccountSchedule(
                jwt, accountIds, generateScheduleRequest, timeoutMilliseconds
        );

        MultiAccountScheduleResponse response = new MultiAccountScheduleResponse();
        response.setSchedules(scheduleResponses);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<MultiAccountScheduleResponse> getAllAccountsSchedule(LocalDate from, LocalDate to) {
        Jwt jwt = currentUserResolver.extractJwt();
        List<UUID> accountIds = getAccountsLinkedToIdentity(jwt)
                .stream()
                .map(UserAccountEntity::getId)
                .toList();
        List<ScheduleResponse> schedules = scheduleService.loadAllAccountsSchedules(jwt, accountIds);
        MultiAccountScheduleResponse response = new MultiAccountScheduleResponse();
        response.setSchedules(schedules);
        return ResponseEntity.ok(response);
    }

    /**
     *
     * @param accountId Account for which the schedule will be generated(required)
     * @param generateScheduleRequest  (required)
     * @return Schedule for the given account
     */
    @Override
    public ResponseEntity<ScheduleResponse> generateSingleAccountSchedule(UUID accountId, GenerateScheduleRequest generateScheduleRequest) {

        // Extract token
        Jwt jwt = currentUserResolver.extractJwt();

        // Generate schedule for account with timeout
        ScheduleResponse scheduleResponse = scheduleService.generateSingleAccountSchedule(
                jwt, accountId, generateScheduleRequest, timeoutMilliseconds
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleResponse);
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSingleAccountSchedule(UUID accountId, LocalDate from, LocalDate to) {
        Jwt jwt = currentUserResolver.extractJwt();
        ScheduleResponse response = scheduleService.loadSingleAccountSchedule(jwt, accountId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MarkScheduleEntryResponse> markScheduleEntryDone(UUID accountId, UUID entryId,
                                                                           MarkScheduleEntryRequest markScheduleEntryRequest) {
        Jwt jwt = currentUserResolver.extractJwt();
        MarkScheduleEntryResponse response = scheduleService.markEntryDone(
                jwt, accountId, entryId, markScheduleEntryRequest.getScope());
        return ResponseEntity.ok(response);
    }
}
