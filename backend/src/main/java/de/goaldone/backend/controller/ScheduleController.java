package de.goaldone.backend.controller;

import de.goaldone.backend.api.SchedulesApi;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.GenerateScheduleRequest;
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
        return userIdentityService.
                findAccountsForIdentity(userIdentityService.findIdentityFromAccount(jwt));
    }

    /**
     *
     * @param generateScheduleRequest  (required)
     * @return Schedule for multiple accounts
     */
    @Override
    public ResponseEntity<MultiAccountScheduleResponse> generateAllAccountsSchedule(GenerateScheduleRequest generateScheduleRequest) {

        // Extract Token
        Jwt jwt = currentUserResolver.extractJwt();

        // List all linked accounts
        List<UUID> accountIds = getAccountsLinkedToIdentity(jwt).
                stream()
                .map(UserAccountEntity::getUserIdentityId)
                .toList();

        // Generate schedule for each account
        List<ScheduleResponse> scheduleResponses = scheduleService.generateMultiAccountSchedule(
                jwt, accountIds, generateScheduleRequest, timeoutMilliseconds
        );

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<MultiAccountScheduleResponse> getAllAccountsSchedule(LocalDate from, LocalDate to) {

        // Validate goaldone user and its connected accounts using ids

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
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

        return ResponseEntity.status(201).body(scheduleResponse);
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSingleAccountSchedule(UUID accountId, LocalDate from, LocalDate to) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
